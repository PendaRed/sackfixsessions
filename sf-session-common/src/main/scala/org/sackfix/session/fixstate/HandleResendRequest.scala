package org.sackfix.session.fixstate

import org.sackfix.fix44.ResendRequestMessage
import org.sackfix.session._

/** Ask the session to resend everything then move ack to normal session state.
  *
  * @param initialState Once resent, return to this initial state
  */
case class HandleResendRequest(val initialState:SfSessState) extends SfSessState(10, "Handle Resend Request",
    initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    ev match {
      case fixMsg:SfSessionFixMessageEvent => fixMsg.msg.body match {
        case resendReq: ResendRequestMessage =>
          // ie blast out all the messages, may as well let the session (which has access to the store
          // do this
          List(SfActionResendMessages(resendReq.beginSeqNoField.value, resendReq.endSeqNoField.value))
        case _ =>
          logger.error(s"[${fixSession.idStr}] Invalid fixMessage type for state transition:${fixMsg.msg.body.msgType}")
          List.empty
      }
      case unknown@ _ =>
        logger.error(s"[${fixSession.idStr}] Invalid original event to the stateTransition:${unknown.getClass.getName}")
        List.empty
    }
  }
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = Some(initialState)
}
