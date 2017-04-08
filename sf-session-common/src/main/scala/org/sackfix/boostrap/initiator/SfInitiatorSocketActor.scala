package org.sackfix.boostrap.initiator

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.io.{IO, Tcp}
import org.sackfix.boostrap.BusinessCommsHandler
import org.sackfix.boostrap.initiator.SfInitiatorSocketActor.{InitiatorCloseNowMsgIn, InitiatorReconnectMsgIn, InitiatorStartTimeMsgIn}
import org.sackfix.session.{SfInitiator, SfSessionId, SfSessionLookup}
import org.sackfix.socket.SfSocketHandlerActor
import org.sackfix.socket.SfSocketHandlerActor.{CloseSocketMsgIn, InitiatorSocketOpenMsgIn}

import scala.concurrent.duration._

/**
  * Initially cut and paste from http://doc.akka.io/docs/akka/current/scala/io-tcp.html
  * Created by Jonathan during 2016.
  */
object SfInitiatorSocketActor {
  def props(sessionLookup: SfSessionLookup,
            sessionId: SfSessionId,
            sessionActor: ActorRef,
            reconnectIntervalSecs: Int,
            socketConfigs: List[SfInitiatorSocketSettings],
            businessComms: BusinessCommsHandler,
            latencyRecorder: Option[ActorRef]): Props =
    Props(new SfInitiatorSocketActor(sessionLookup, sessionId, sessionActor, reconnectIntervalSecs, socketConfigs, businessComms, latencyRecorder))

  case class InitiatorStartTimeMsgIn(cause: String)

  case class InitiatorCloseNowMsgIn(cause: String)

  case object InitiatorReconnectMsgIn

}

/**
  * @param sessionLookup It seems strange to pass in the lookup when we could pass in the actual
  *                      session actor from the initiator booter.  BUT, all comms on the channel
  *                      has to have the correct header fields, and if it doesnt then it should not
  *                      be able to use the session.
  */
class SfInitiatorSocketActor(val sessionLookup: SfSessionLookup,
                             val sessionId: SfSessionId,
                             val sessionActor: ActorRef,
                             val reconnectIntervalSecs: Int,
                             val socketConfigs: List[SfInitiatorSocketSettings],
                             val businessComms: BusinessCommsHandler,
                             val latencyRecorder: Option[ActorRef]) extends Actor with ActorLogging {

  import Tcp._

  // These two are needed to schedule the reconnect
  import context.{dispatcher, system}

  if (socketConfigs.length < 1) {
    log.error("No socket configurations given so initiator config is in error")
    context stop self
  }

  var handler: Option[ActorRef] = None
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

  def startSession() = {
    handler match {
      case None =>
        getSocketDetails match {
          case None => // simply impossible!
          case Some(socketDet) =>
            log.info(s"Attempting to connect to Fix Server at [$socketDescription]")
            IO(Tcp) ! Connect(socketDet) // implicit convert
        }
      case Some(c) =>
        log.info(s"startSession call ignored, already open")
    }
  }

  def endSession = {
    handler match {
      case Some(h) =>
        log.info(s"Closing socket to:")
        h ! CloseSocketMsgIn
      // it will terminate itself when the socket closes, and the death watch
      // will catch it and set handler to None
      case None =>
    }
  }

  def receive = {
    case CommandFailed(_: Connect) =>
      failedConnectionCounter += 1
      log.error(s"Failed to connect to Fix Server [$failedConnectionCounter] times at [$socketDescription], will reconnect in $reconnectIntervalSecs seconds")
      system.scheduler.scheduleOnce(reconnectIntervalSecs seconds, self, InitiatorReconnectMsgIn)
    case c@Connected(remote, local) =>
      val debugHostName = remote.getHostName + ":" + remote.getPort
      log.info(s"Outgoing connnection to [${debugHostName}] established")
      val connection = sender
      val handlerActor = context.actorOf(SfSocketHandlerActor.props(SfInitiator, sender,
        sessionLookup, debugHostName, businessComms, latencyRecorder),
        name = s"$debugHostName-SfSocketHandlerActor")

      // sign death pact: this Actor terminates when the connection breaks
      context watch handlerActor

      sender ! Register(handlerActor)
      handler = Some(handlerActor)

      handlerActor ! InitiatorSocketOpenMsgIn(sessionId, sessionActor)
    case Terminated(handlerActor) =>
      // I only watch the socket handler, and if that is dead then the socket has to be closed
      handler = None
    case InitiatorReconnectMsgIn =>
      log.info(s"Session Reconnect message arrived")
      startSession()
    case InitiatorStartTimeMsgIn(cause: String) =>
      log.info(s"SessionStart called: $cause")
      startSession()
    case InitiatorCloseNowMsgIn(cause: String) =>
      log.info(s"SessionClose called: $cause")
      endSession
    case actorMsg@_ =>
      log.error(s"Match error: unexpected message received by Actor :${actorMsg.getClass.getName}")
  }
}
