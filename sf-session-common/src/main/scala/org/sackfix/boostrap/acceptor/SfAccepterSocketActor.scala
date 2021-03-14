package org.sackfix.boostrap.acceptor

// http://doc.akka.io/docs/akka/current/scala/io-tcp.html
// version 2.4.4
import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import org.sackfix.boostrap.acceptor.SfAccepterSocketActor.{AcceptorEndTimeMsgIn, AcceptorStartTimeMsgIn}
import org.sackfix.boostrap.{BusinessCommsHandler, SystemErrorNeedsDevOpsMsg}
import org.sackfix.session.SfSessionActor.{AcceptorSocketClosedMsgIn, AcceptorSocketWaitingMsgIn}
import org.sackfix.session.{SfAcceptor, SfSessionLookup}
import org.sackfix.socket.SfSocketHandlerActor

/**
  * Binds to a local port and waits for connections.
  * When a connection is confirmed we tell the sender to register a SfInboundDataActor handler, with ACK based write mode
  *
  * http://doc.akka.io/docs/akka/current/scala/io-tcp.html
  *
  * Created by Jonathan in 2016.
  */
object SfAccepterSocketActor {
  def props(myHostName: Option[InetAddress], portNum: Int, sessionLookup: SfSessionLookup,
            businessComms: BusinessCommsHandler,
            guardianActor: ActorRef,
            latencyRecorder: Option[ActorRef]): Props =
    Props(new SfAccepterSocketActor(myHostName, portNum, sessionLookup, businessComms,
      guardianActor, latencyRecorder))

  case object AcceptorStartTimeMsgIn

  case object AcceptorEndTimeMsgIn

}

class SfAccepterSocketActor(val myHostName: Option[InetAddress], val portNum: Int,
                            val sessionLookup: SfSessionLookup,
                            val businessComms: BusinessCommsHandler,
                            val guardianActor: ActorRef,
                            val latencyRecorder: Option[ActorRef]) extends Actor with ActorLogging {

  import Tcp._

  // Aquire a TCP manager, IO(Tcp) returns the manager
  import context.system
  // Implicitly used by IO(Tcp)

  if (myHostName.isEmpty) log.info(s"Configuration did not set ${SfAcceptorSettings.SOCKET_ACCEPT_ADDRESS}, so defaulting to local host")
  val host: InetAddress = if (myHostName.isDefined) myHostName.get else InetAddress.getLocalHost
  var connection: Option[ActorRef] = None

  def receive: Receive = {
    case AcceptorStartTimeMsgIn => startSession()
    case AcceptorEndTimeMsgIn => endSession()
    case b@Bound(localAddress) =>
      connection = Some(sender())
      log.info(s"Successful bind to socket. host:[$host] port:[$portNum]")

    case Unbound =>
      log.info(s"Socket closed")
      sessionLookup.getAllSessionActors.foreach(_ ! AcceptorSocketClosedMsgIn)
      connection = None

    case CommandFailed(_: Bind) =>
      guardianActor ! SystemErrorNeedsDevOpsMsg(s"Another process is using port [$portNum], please close that process before restarting")
      context stop self

    case c@Connected(remote, local) =>
      val debugHostName = remote.getHostName + ":" + remote.getPort
      log.info(s"Incoming connnection from [$debugHostName], registering a new Fix Message Handler")
      val conn = sender()
      val handler = context.actorOf(SfSocketHandlerActor.props(SfAcceptor, conn, sessionLookup, debugHostName, businessComms, latencyRecorder),
        name = s"${debugHostName}SfSocketHandlerActor")
      conn ! Register(handler, keepOpenOnPeerClosed = true)
    case actorMsg@_ =>
      log.error(s"Match error: unexpected message received by Actor :${actorMsg.getClass.getName}")

  }

  def startSession(): Unit = {
    connection match {
      case None =>
        log.info(s"Opening socket to listen for clients. host:[$host] port:[$portNum]")
        IO(Tcp) ! Bind(self, new InetSocketAddress(host, portNum))
        sessionLookup.getAllSessionActors.foreach(sessionActor => {
          sessionActor ! AcceptorSocketWaitingMsgIn
        })
      case Some(c) =>
        log.info(s"startSession call ignored, already open: for host:[$host] port:[$portNum]")
    }
  }

  def endSession(): Unit = {
    connection match {
      case Some(c) =>
        log.info(s"Closing socket host:[$host] port:[$portNum]")
        c ! Unbind
      case None =>
        log.info(s"EndSession event ignored, as acceptor socket is not open.")
    }
  }
}
