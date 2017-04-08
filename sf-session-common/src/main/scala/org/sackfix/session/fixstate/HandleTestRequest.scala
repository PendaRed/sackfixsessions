package org.sackfix.session.fixstate

import org.sackfix.field.{EncryptMethodField, HeartBtIntField, MsgTypeField, TestReqIDField}
import org.sackfix.fix44.{HeartbeatMessage, LogonMessage, TestRequestMessage}
import org.sackfix.session._

/**
  * @param initialState The state to return to once I have dealt with this
  */
case class HandleTestRequest(val initialState:SfSessState) extends SfSessState(20, "Handle Test Request",
      initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    ev match {
      case fixMsgEv : SfSessionFixMessageEvent if (fixMsgEv.msg.body.msgType==MsgTypeField.TestRequest) =>
        fixMsgEv.msg.body match {
          case req :TestRequestMessage =>
            List(SfActionSendMessageToFix(new HeartbeatMessage(Some(req.testReqIDField))))
          case _ =>
            logger.error(s"[${fixSession.idStr}] BUG: state machine expected a Test Request fix body, but instead got "+fixMsgEv.msg.body.getClass.getName())
            List.empty
        }
      case _ =>
        logger.error(s"[${fixSession.idStr}] BUG: state machine expected a Test Request in SfSessionFixMessageEvent, but instead got "+ev.getClass.getName())
        List.empty
    }
  }
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = Some(initialState)
}
