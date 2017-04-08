package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.field.ResetSeqNumFlagField
import org.sackfix.fix44.LogonMessage
import org.sackfix.session._
import org.slf4j.LoggerFactory

/**
  * waiting state, for the first login, ie we are the server, and a client has connected
  *
  * SfSessionServerSocketOpenEvent into NoConnection
  * transitions into networkconnection established
  */
object NetworkConnnectionEstablished extends SfSessState(6,"Network Connnection Established",
  initiator = false, acceptor = true, isSessionOpen=false, isSessionSocketOpen=true) {

  override protected[fixstate] def stateTransitionAction(fixSession: SfSession, ev: SfSessionEvent): List[SfAction] = {
    logger.info(s"[${fixSession.idStr}] Session expects theirNextSeqNum=${fixSession.getExpectedTheirSeqNum} and myNextSeqNum=${fixSession.nextMySeqNum}")
    super.stateTransitionAction(fixSession, ev)
  }

  private[fixstate] def handleLogonMessage(fixSession: SfSession, msgIn: SfMessage, logonMessage:LogonMessage,
                                           actionCallback:SfAction=>Unit): Option[SfSessState] = {
    if (isResentDuplicate(fixSession, msgIn)) None
    else {
      if (logonMessage.resetSeqNumFlagField.getOrElse(ResetSeqNumFlagField("N")).value) {
        val logonSeqNum = msgIn.header.msgSeqNumField.value
        if (logonSeqNum != 1) {
          logger.info(s"[${fixSession.idStr}] LogonMessage asks for Sequence reset to 1 (ie for this message), but the message did not have a sequence number of 1 - logout reply.")
          Some(InitiateLogoutProcess(s"Logon ResetSeqNumFlag=Y, but sequence num was [$logonSeqNum] and should be 1"))
        } else {
          // @TODO does a reset mean just reset theirs, or also reset mine?
          logger.info(s"[${fixSession.idStr}] LogonMessage asks for Sequence reset to 1, resetting now.")
          fixSession.resetSeqNums // sets them to 1, next increment will be InitiationLogonResponse, ie move to expect 2 from them next
          actionCallback(SfActionCounterpartyHeartbeat(logonMessage.heartBtIntField.value))
          Some(InitiationLogonReceived)
        }
      } else {
        // Not a message sequence number reset request
        handleSequenceNumberTooLow(fixSession) orElse {
          // There is a special case where their seq num can be too high, which is handled when
          // we have replied with a logon, and then transition to InitiationLogonResponse, which is where we inc their seq num
          actionCallback(SfActionCounterpartyHeartbeat(logonMessage.heartBtIntField.value))
          Some(InitiationLogonReceived)
        }
      }
    }
  }

  override protected[fixstate] def receiveFixMsg(fixSession: SfSession, msgIn: SfMessage,actionCallback:SfAction=>Unit): Option[SfSessState] = {
    msgIn.body match {
      case logonMessage:LogonMessage =>
        handleLogonMessage(fixSession, msgIn, logonMessage, actionCallback)
      case otherMessage =>
        val msgType = otherMessage.msgType
        logger.warn(s"[${fixSession.idStr}] First message not a logon.  Received unexpected FIX message [$msgType], correct sequence number")
        Some(DisconnectSocketNow)
    }
  }
}
