package org.sackfix.boostrap.initiator

import akka.actor.typed.scaladsl.adapter.{TypedActorContextOps, TypedActorRefOps}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.io.{IO, Tcp}
import akka.{actor => classic}
import org.sackfix.boostrap.BusinessCommsHandler
import org.sackfix.boostrap.initiator.SfInitiatorSocketActor.{InitiatorCloseNowMsgIn, InitiatorReconnectMsgIn, InitiatorStartTimeMsgIn}
import org.sackfix.latency.LatencyActor.LatencyCommand
import org.sackfix.session.SfSessionActor.SfSessionActorCommand
import org.sackfix.session.{SfInitiator, SfSessionId, SfSessionLookup}
import org.sackfix.socket.SfSocketHandlerActor
import org.sackfix.socket.SfSocketHandlerActor.{CloseSocketMsgIn, InitiatorSocketOpenMsgIn}

import scala.concurrent.duration._

/**
  * Initially cut and paste from http://doc.akka.io/docs/akka/current/scala/io-tcp.html
  * Created by Jonathan during 2016.
  *
  * Note that in the typed to classic interop there is NO sender in scope to when sent
  * to TCP actors (ie classic ones), do not use !, but use .tell(xx, context.self.toClassic)
  */
object SfInitiatorSocketActor {
  def apply(sessionLookup: SfSessionLookup,
            sessionId: SfSessionId,
            sessionActor: ActorRef[SfSessionActorCommand],
            reconnectIntervalSecs: Int,
            socketConfigs: List[SfInitiatorSocketSettings],
            businessComms: BusinessCommsHandler,
            latencyRecorder: Option[ActorRef[LatencyCommand]]): Behavior[Tcp.Event] =
    Behaviors.setup(context =>
      Behaviors.withTimers { timers => new SfInitiatorSocketActor(context, timers, sessionLookup, sessionId, sessionActor, reconnectIntervalSecs, socketConfigs, businessComms, latencyRecorder)
      })

  trait SfInitiatorSocketCommand extends Tcp.Event

  case class InitiatorStartTimeMsgIn(cause: String) extends SfInitiatorSocketCommand

  case class InitiatorCloseNowMsgIn(cause: String) extends SfInitiatorSocketCommand

  case object InitiatorReconnectMsgIn extends SfInitiatorSocketCommand

}

/**
  * @param sessionLookup It seems strange to pass in the lookup when we could pass in the actual
  *                      session actor from the initiator booter.  BUT, all comms on the channel
  *                      has to have the correct header fields, and if it doesnt then it should not
  *                      be able to use the session.
  */
class SfInitiatorSocketActor(context: ActorContext[Tcp.Event],
                             timers: TimerScheduler[Tcp.Event],
                             val sessionLookup: SfSessionLookup,
                             val sessionId: SfSessionId,
                             val sessionActor: ActorRef[SfSessionActorCommand],
                             val reconnectIntervalSecs: Int,
                             val socketConfigs: List[SfInitiatorSocketSettings],
                             val businessComms: BusinessCommsHandler,
                             val latencyRecorder: Option[ActorRef[LatencyCommand]]) extends AbstractBehavior[Tcp.Event](context) {

  import Tcp._

  if (socketConfigs.length < 1) {
    context.log.error("No socket configurations given so initiator config is in error")
    context stop context.self
  }

  // Note tcp only exists in classic actors, there is no types equivalent
  var handler: Option[ActorRef[Tcp.Event]] = None
  private var socketDescription = ""
  private var roundRobinSocketNumber = 0
  private var failedConnectionCounter = 0

  /**
    * Implements the round robin approach to connecting to each configured end point in turn
    */
  private def getSocketDetails: Option[SfInitiatorSocketSettings] = {
    val ret = Some(socketConfigs(roundRobinSocketNumber))
    socketDescription = socketConfigs(roundRobinSocketNumber).toString
    roundRobinSocketNumber += 1
    if (roundRobinSocketNumber >= socketConfigs.size) roundRobinSocketNumber = 0
    ret
  }

  def startSession(): Unit = {

    handler match {
      case None =>
        getSocketDetails match {
          case None => // simply impossible!
          case Some(socketDet) =>
            context.log.info(s"Attempting to connect to Fix Server at [$socketDescription]")
            IO(Tcp)(context.system.classicSystem).tell(Connect(socketDet),context.self.toClassic) // no typed equivalent for tcp and io.
        }
      case Some(c) =>
        context.log.info(s"startSession call ignored, already open")
    }
  }

  def endSession() = {
    handler match {
      case Some(h) =>
        context.log.info(s"Closing socket to:")
        h ! CloseSocketMsgIn
      // it will terminate itself when the socket closes, and the death watch
      // will catch it and set handler to None
      case None =>
    }
  }

  override def onMessage(msg: Tcp.Event): Behavior[Tcp.Event] = {
    msg match {
      case CommandFailed(_: Connect) =>
        failedConnectionCounter += 1
        context.log.error(s"Failed to connect to Fix Server [$failedConnectionCounter] times at [$socketDescription], will reconnect in $reconnectIntervalSecs seconds")
        timers.startSingleTimer(InitiatorReconnectMsgIn, reconnectIntervalSecs.seconds)
        Behaviors.same
      case c@Connected(remote, local) =>
        val debugHostName = remote.getHostName + ":" + remote.getPort
        context.log.info(s"Outgoing connnection to [${debugHostName}] established")
        val sender = context.toClassic.sender()
        val handlerActor = context.spawn(SfSocketHandlerActor(SfInitiator, sender,
          sessionLookup, debugHostName, businessComms, latencyRecorder),
          name = s"$debugHostName-SfSocketHandlerActor")

        // sign death pact: this Actor terminates when the connection breaks
        context.watch(handlerActor)

        sender.tell(Register(handlerActor.toClassic),context.self.toClassic)
        handler = Some(handlerActor)

        handlerActor ! InitiatorSocketOpenMsgIn(sessionId, sessionActor)
        Behaviors.same
      case InitiatorReconnectMsgIn =>
        context.log.info(s"Session Reconnect message arrived")
        startSession()
        Behaviors.same
      case InitiatorStartTimeMsgIn(cause: String) =>
        context.log.info(s"SessionStart called: $cause")
        startSession()
        Behaviors.same
      case InitiatorCloseNowMsgIn(cause: String) =>
        context.log.info(s"SessionClose called: $cause")
        endSession()
        Behaviors.same
      case actorMsg@_ =>
        context.log.error(s"Match error: unexpected message received by Actor :${actorMsg.getClass.getName}")
        Behaviors.same
    }
  }
  override def onSignal: PartialFunction[Signal, Behavior[Tcp.Event]] = {
    case Terminated(handlerActor) =>
      // I only watch the socket handler, and if that is dead then the socket has to be closed
      handler = None
      Behaviors.same
  }

}
