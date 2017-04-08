package org.sackfix.session.fixstate

import org.sackfix.field.{BeginSeqNoField, EndSeqNoField}
import org.sackfix.fix44.ResendRequestMessage
import org.sackfix.session._

/**
  * Transition state due to missing some message, ask them to resend and move to awaiting
  * catch up.
  */
case class ReceiveMsgSeqNumTooHigh(beginSeqNum:Int, endSeqNum:Int) extends SfSessState(11,"Receive Msg Seq Num Too High",
  initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    List(SfActionSendMessageToFix(new ResendRequestMessage(new BeginSeqNoField(beginSeqNum),
                                    new EndSeqNoField(endSeqNum))))
  }
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = {
    Some(AwaitingProcessingResponseToResendRequest(beginSeqNum, endSeqNum))
  }
}
