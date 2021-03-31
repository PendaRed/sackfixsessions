package org.sackfix.session

import akka.actor.typed.ActorRef
import akka.io.Tcp
import akka.{actor => classic}
import org.sackfix.boostrap._
import org.sackfix.common.message.SfMessage
import org.sackfix.session.SfSessionActor.SfSessionActorCommand
import org.slf4j.LoggerFactory

/**
  * The state for this is maintained by SfSocketActor, anything that makes it out of the socket layer
  * (ie message is well formed) and into the SfSessionActor has one of these attached.
  * It IS immutable so we do not expose state.  However, this means there can be race.  ie the session layer
  * wants to send a message out but the socket is already closed
  *
  * Created by Jonathan during 2017.
  */
trait SfSessOutEventRouter {
  val sfSessionActor: ActorRef[SfSessionActorCommand]
  val tcpActor: classic.ActorRef
  protected[session] val remoteHostDebugStr: String

  def confirmCorrectTcpActor(checkTcpActor: classic.ActorRef): Boolean

  def logOutgoingFixMsg(fixMsgStr: String)

  def closeThisFixSessionsSocket()

  def informBusinessLayerSessionIsOpen()

  def informBusinessLayerSessionIsClosed()

  def informBusinessMessageArrived(fixMsg: SfMessage)

  def informBusinessMessageAcked(correlationId: String)

  def informBusinessRejectArrived(fixMsg: SfMessage)
}

case class SfSessOutEventRouterImpl( businessComms: BusinessCommsHandler,
                                    override val sfSessionActor: ActorRef[SfSessionActorCommand],
                                     sessionId: SfSessionId,
                                    override val tcpActor: classic.ActorRef,
                                    override val remoteHostDebugStr: String) extends SfSessOutEventRouter {

  import Tcp._

  private val fixlog = LoggerFactory.getLogger("fixmessages")

  /**
    * If a client connects to a server, the router is connected and attached to the sessionactor based on the
    * sessionId - ie fielde from the message header.
    * If another client then spams in a connection while one is created, then you can have several routers
    * linked to a single SessionActor.  So the session actor has to confirm that the router it is working with
    * matches the sender of any tcp events from the SfSocketHandlerActor
    *
    * If an actor fails then the actor ref will fail.  But I dont mind as if the tcp actor fails the socket is gone
    * anyway.
    *
    * @return true if it matches
    */
  override def confirmCorrectTcpActor(checkTcpActor: classic.ActorRef): Boolean = tcpActor == checkTcpActor

  /**
    * Obviously the logging could go someplace eles, BUT this is so damn handy for testing.
    * Originally this sent the message, but since I want an Ack I could got get the implicit sender
    * to be assigned, which means the ack never came back
    */
  override def logOutgoingFixMsg(fixMsgStr: String): Unit = {
    fixlog.info("OUT {}", fixMsgStr)
  }

  /**
    * Something has happened in the session layer to say lets close the socket now
    */
  override def closeThisFixSessionsSocket(): Unit = tcpActor ! Close


  /**
    * The session opened
    */
  override def informBusinessLayerSessionIsOpen(): Unit = {
    businessComms.handleFix(FixSessionOpen(sessionId, sfSessionActor))
  }

  /**
    * The session closed
    */
  override def informBusinessLayerSessionIsClosed(): Unit = {
    businessComms.handleFix(FixSessionClosed(sessionId))
  }

  /**
    * We received a fix message, validated it, confirmed it was not a session message and so now
    * need to forward it to the business OMS
    *
    * @param fixMsg The decoded business message
    */
  override def informBusinessMessageArrived(fixMsg: SfMessage): Unit = {
    businessComms.handleFix(BusinessFixMessage(sessionId, sfSessionActor, fixMsg))
  }

  override def informBusinessMessageAcked(correlationId: String): Unit = {
    businessComms.handleFix(BusinessFixMsgOutAck(sessionId, sfSessionActor, correlationId))
  }


  /**
    * Under any normal conditions there should be zero rejects.  So, decide what you want to do...
    *
    * @param fixMsg     - the body will be a reject ie
    *                   fixMsg.body match{
    *                   case rj:RejectMessage =>
    *                   }
    */
  override def informBusinessRejectArrived(fixMsg: SfMessage): Unit = {
    businessComms.handleFix(BusinessRejectMessage(sessionId, sfSessionActor, fixMsg))
  }

}
