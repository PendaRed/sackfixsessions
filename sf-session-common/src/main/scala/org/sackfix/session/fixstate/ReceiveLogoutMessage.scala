package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.fix44.LogoutMessage
import org.sackfix.session._

/**
  * Created by Jonathan on 01/01/2017.
  *
  * 1.	Send Logout response message
  * 2.	Wait for counterparty to disconnect up to 10 seconds.  If max exceeded, disconnect and generate
  * an “error” condition in test output.
  */
object ReceiveLogoutMessage extends SfSessState(15,"Receive Logout Message",
  initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {

  private val TIMEOUT_ID="LogoutResponseDisconnect10sWait"

  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]=
    List(SfActionSendMessageToFix(new LogoutMessage()),
      SfActionStartTimeout(TIMEOUT_ID, 10000))

  override protected[fixstate] def nextState(sfSession:SfSession) : Option[SfSessState] = {
    None
  }

  /**
    * Other end instigated the logout, so just discard ANY messages
    */
  override protected[fixstate] def receiveFixMsg(fixSession:SfSession, msgIn:SfMessage,actionCallback:SfAction=>Unit): Option[SfSessState] = {
    logger.warn(s"[${fixSession.idStr}] Received a fix message (seqNum=[${msgIn.header.msgSeqNumField}]) while waiting for the socket to close, discarding.")
    None
  }

  override protected[fixstate] def receiveControlEvent(fixSession:SfSession, event:SfSessionControlEvent) : Option[SfSessState] ={
    event match {
      case SfControlTimeoutFired(id, durationMs) if (id==TIMEOUT_ID) =>
        logger.warn(s"[${fixSession.idStr}] No reply to final logout for at least "+durationMs+" ms, disconnecting socket")
        Some(DisconnectSocketNow)
      case ev@ _ =>
        super.receiveControlEvent(fixSession, event)
    }
  }

}
