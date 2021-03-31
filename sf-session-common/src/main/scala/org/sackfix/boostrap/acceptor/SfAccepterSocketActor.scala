package org.sackfix.boostrap.acceptor

// http://doc.akka.io/docs/akka/current/scala/io-tcp.html
// version 2.4.4

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.io.{IO, Tcp}
import akka.{actor => classic}
import org.sackfix.boostrap.acceptor.SfAccepterSocketActor.{AcceptorEndTimeMsgIn, AcceptorStartTimeMsgIn}
import org.sackfix.boostrap.{BusinessCommsHandler, SystemErrorNeedsDevOpsMsg}
import org.sackfix.latency.LatencyActor.LatencyCommand
import org.sackfix.session.SfSessionActor.{AcceptorSocketClosedMsgIn, AcceptorSocketWaitingMsgIn}
import org.sackfix.session.{SfAcceptor, SfSessionLookup}
import org.sackfix.socket.SfSocketHandlerActor

import java.net.{InetAddress, InetSocketAddress}

/**
  * Binds to a local port and waits for connections.
  * When a connection is confirmed we tell the sender to register a SfInboundDataActor handler, with ACK based write mode
  *
  * http://doc.akka.io/docs/akka/current/scala/io-tcp.html
  *
  * Created by Jonathan in 2016.
  */
object SfAccepterSocketActor {
  def apply(myHostName: Option[InetAddress], portNum: Int, sessionLookup: SfSessionLookup,
            businessComms: BusinessCommsHandler,
            guardianActor: ActorRef[SystemErrorNeedsDevOpsMsg],
            latencyRecorder: Option[ActorRef[LatencyCommand]]): Behavior[Tcp.Event] =
    Behaviors.setup(context => new SfAccepterSocketActor(context, myHostName, portNum, sessionLookup, businessComms,
      guardianActor, latencyRecorder))


  trait SfAccepterSocketCommand extends Tcp.Event

  case object AcceptorStartTimeMsgIn extends SfAccepterSocketCommand

  case object AcceptorEndTimeMsgIn extends SfAccepterSocketCommand

}

class SfAccepterSocketActor(context: ActorContext[Tcp.Event],
                            val myHostName: Option[InetAddress], val portNum: Int,
                            val sessionLookup: SfSessionLookup,
                            val businessComms: BusinessCommsHandler,
                            val guardianActor: ActorRef[SystemErrorNeedsDevOpsMsg],
                            val latencyRecorder: Option[ActorRef[LatencyCommand]]) extends AbstractBehavior[Tcp.Event](context) {

  import Tcp._

  // Aquire a TCP manager, IO(Tcp) returns the manager
  // Implicitly used by IO(Tcp)

  if (myHostName.isEmpty) context.log.info(s"Configuration did not set ${SfAcceptorSettings.SOCKET_ACCEPT_ADDRESS}, so defaulting to local host")
  val host: InetAddress = if (myHostName.isDefined) myHostName.get else InetAddress.getLocalHost
  var ioActorRef: Option[classic.ActorRef] = None
  var connection: Option[classic.ActorRef] = None

  override def onMessage(msg: Tcp.Event): Behavior[Tcp.Event] = {
    msg match {
      case AcceptorStartTimeMsgIn =>
        startSession()
        Behaviors.same
      case AcceptorEndTimeMsgIn =>
        endSession()
        Behaviors.same
      case b@Bound(localAddress) =>
        connection = ioActorRef
        context.log.info(s"Successful bind to socket. host:[$host] port:[$portNum]")
        Behaviors.same
      case Unbound =>
        context.log.info(s"Socket closed")
        sessionLookup.getAllSessionActors.foreach(_ ! AcceptorSocketClosedMsgIn)
        connection = None
        Behaviors.same
      case CommandFailed(_: Bind) =>
        guardianActor ! SystemErrorNeedsDevOpsMsg(s"Another process is using port [$portNum], please close that process before restarting")
        Behaviors.stopped

      case c@Connected(remote, local) =>
        val debugHostName = remote.getHostName + ":" + remote.getPort
        context.log.info(s"Incoming connnection from [$debugHostName], registering a new Fix Message Handler")
        val conn = ioActorRef.get
        val handler = context.spawn(SfSocketHandlerActor(SfAcceptor, conn, sessionLookup, debugHostName, businessComms, latencyRecorder),
          name = s"${debugHostName}SfSocketHandlerActor")
        conn ! Register(handler.toClassic, keepOpenOnPeerClosed = true)
        Behaviors.same
      case actorMsg@_ =>
        context.log.error(s"Match error: unexpected message received by Actor :${actorMsg.getClass.getName}")
        Behaviors.same
    }
  }

  def startSession(): Unit = {
    connection match {
      case None =>
        context.log.info(s"Opening socket to listen for clients. host:[$host] port:[$portNum]")
        ioActorRef = Some(IO(Tcp)(context.system.classicSystem))
        ioActorRef.get ! Bind(context.self.toClassic, new InetSocketAddress(host, portNum))
        sessionLookup.getAllSessionActors.foreach(sessionActor => {
          sessionActor ! AcceptorSocketWaitingMsgIn
        })
      case Some(c) =>
        context.log.info(s"startSession call ignored, already open: for host:[$host] port:[$portNum]")
    }
  }

  def endSession(): Unit = {
    connection match {
      case Some(c) =>
        context.log.info(s"Closing socket host:[$host] port:[$portNum]")
        c ! Unbind
      case None =>
        context.log.info(s"EndSession event ignored, as acceptor socket is not open.")
    }
  }
}
