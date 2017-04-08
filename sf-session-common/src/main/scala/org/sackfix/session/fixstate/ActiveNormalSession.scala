package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{PossDupFlagField, SessionRejectReasonField, TextField}
import org.sackfix.fix44._
import org.sackfix.session._
import org.sackfix.session.fixstate.ReceiveLogoutMessage.logger

/**
  * Created by Jonathan on 01/01/2017.
  */
object ActiveNormalSession extends SfSessState(17, "Active Normal Session",
  initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {

  private val BEFORE_TELL_BUSINESS_TIMEOUT_ID="ActiveNormalSessionDelayInNotifyingBusiness"
  private var lastTimeoutId = BEFORE_TELL_BUSINESS_TIMEOUT_ID

  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    logger.info(s"[${fixSession.idStr}] Pausing 1000ms before informing Business session is open, this allows resend requests etc to flow in.")
    lastTimeoutId = BEFORE_TELL_BUSINESS_TIMEOUT_ID+"_"+System.currentTimeMillis()
    List(SfActionStartTimeout(lastTimeoutId, 1000))
  }

  private[fixstate] def handleSequenceReset(fixSession: SfSession, msgIn:SfMessage, resetMsgBody: SequenceResetMessage): Option[SfSessState] = {
    val newSeqNo = resetMsgBody.newSeqNoField.value
    if (newSeqNo < fixSession.getExpectedTheirSeqNum) {
      /* Stupid spec is all over the place. Designed by idiots, ignoring point 1
      1)	Accept the Sequence Reset (Reset) message without regards its MsgSeqNum
      2)	Send Reject (session-level) message referencing invalid MsgType (>= FIX 4.2: SessionRejectReason = "Value is incorrect (out of range) for this tag")
      3)	Do NOT Increment inbound MsgSeqNum
      4)	Generate an "error" condition in test output
      5)	Do NOT lower expected sequence number.
       */
      val msg = s"SequenceReset specified NewSeqNo of [$newSeqNo] < expected Seq no [${fixSession.getExpectedTheirSeqNum}]"
      logger.info(s"[${fixSession.idStr}] Rejecting $msg")
      fixSession.sendRejectMessage(msgIn.header.msgSeqNumField.value, false,
        SessionRejectReasonField(SessionRejectReasonField.ValueIsIncorrect), TextField(msg))
    } else {
      logger.info(s"[${fixSession.idStr}] SequenceResetMessage to [$newSeqNo]")
      fixSession.setTheirSeq(newSeqNo)
    }
    Some(SessNoChangeEventConsumed)
  }

  private[fixstate] def processSessionLevelMessages(fixSession: SfSession, msgIn: SfMessage, actionCallback:SfAction=>Unit): Option[SfSessState] = {
    msgIn.body match {
      case resendReq: ResendRequestMessage =>
        fixSession.incrementTheirSeq
        Some(HandleResendRequest(this))
      case testReq: TestRequestMessage =>
        fixSession.incrementTheirSeq
        Some(new HandleTestRequest(this))
      case hb: HeartbeatMessage =>
        fixSession.incrementTheirSeq
        Some(SessNoChangeEventConsumed)
      case rj: RejectMessage =>
        fixSession.incrementTheirSeq
        logger.warn(s"[${fixSession.idStr}] Received a Reject Message for our seqNum: ${rj.refSeqNumField} with reason ${rj.textField.getOrElse(TextField("Unknown")).value}")
        // When its sent to the business it is converted to a reject based on the body type
        actionCallback(SfActionBusinessMessage(msgIn))
        Some(SessNoChangeEventConsumed)
      case reset: SequenceResetMessage =>
        handleSequenceReset(fixSession, msgIn,reset)
      case logout: LogoutMessage =>
        fixSession.incrementTheirSeq
        Some(ReceiveLogoutMessage)
      case bdy: SfFixMessageBody =>
        fixSession.incrementTheirSeq
        if (!fixSession.isMessageFixSessionMessage(bdy.msgType)) {
          None
        } else {
          logger.warn(s"[${fixSession.idStr}] Message Type ${bdy.msgType} arrived, expected handling at SessionLevel but none implemented!")
          Some(SessNoChangeEventConsumed)
        }
    }
  }

  private[fixstate] def handleNormalFixStuff(fixSession: SfSession, msgIn: SfMessage,actionCallback:SfAction=>Unit): Option[SfSessState] = {
    // If we stay in normal state all is fine, but if we leave then tell the business layer to hold off with the message sending
    validateStandardHeader(fixSession, msgIn) orElse processSessionLevelMessages(fixSession, msgIn, actionCallback) match {
      case noChange @ Some(SessNoChangeEventConsumed) =>
        noChange
      case None => None
      case leavingNormalState : Some[SfSessState] =>
        actionCallback(SfActionBusinessSessionClosedForSending)
        leavingNormalState
    }
  }

  private def tellBusinessWeAreOpen(actionCallback:SfAction=>Unit) = {
    if (lastTimeoutId.length>0) {
      actionCallback(SfActionBusinessSessionOpenForSending)
      lastTimeoutId = ""
    }
  }

  override protected[fixstate] def receiveFixMsg(fixSession:SfSession, msgIn:SfMessage,actionCallback:SfAction=>Unit): Option[SfSessState] = {
    handleNormalFixStuff(fixSession, msgIn, actionCallback) orElse {
      // Gosh, a message for the business!
      tellBusinessWeAreOpen(actionCallback)
      actionCallback(SfActionBusinessMessage(msgIn))
      None
    }
  }

  override protected[fixstate] def receiveControlEvent(sfSession:SfSession, event:SfSessionControlEvent) : Option[SfSessState] ={
    event match {
      case SfControlTimeoutFired(id, durationMs) if (id==lastTimeoutId) =>
        tellBusinessWeAreOpen(sfSession.handleAction)
        None
      case SfControlNoReceivedHeartbeatTimeout(noHeartbeatsMissed:Int) =>
        Some(new NoMessagesReceivedInInterval)
      case SfControlNoSentHeartbeatTimeout(noHeartbeatsMissed:Int) =>
        sfSession.sendAMessage(new HeartbeatMessage(), "")
        Some(SessNoChangeEventConsumed)
      case ev@ _ =>
        super.receiveControlEvent(sfSession, event)
    }
  }
}