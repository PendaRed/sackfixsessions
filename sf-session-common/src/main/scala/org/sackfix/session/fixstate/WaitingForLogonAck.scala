package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.field._
import org.sackfix.fix44.LogonMessage
import org.sackfix.session._
import org.sackfix.session.fixstate.NetworkConnnectionEstablished.isResentDuplicate

object WaitingForLogonAck extends SfSessState(18, "Waiting For Logon Ack",
  initiator = true, acceptor = false, isSessionOpen=true, isSessionSocketOpen=true) {
  private[fixstate] def handleLogonAckMessage(fixSession: SfSession, msgIn: SfMessage, logonMessage: LogonMessage,
                                              actionCallback: SfAction => Unit): Option[SfSessState] = {
    if (logonMessage.resetSeqNumFlagField.getOrElse(ResetSeqNumFlagField("N")).value) {
      val logonSeqNum = msgIn.header.msgSeqNumField.value
      if (logonSeqNum != 1) {
        logger.info(s"[${fixSession.idStr}] LogonMessage asks for Sequence reset to 1 (ie for this message), but the message did not have a sequence number of 1 - logout reply.")
        fixSession.incrementTheirSeq
        Some(InitiateLogoutProcess(s"Logon ResetSeqNumFlag=Y, but sequence num was [$logonSeqNum] and should be 1"))
      } else {
        logger.info(s"[${fixSession.idStr}] LogonMessage asks for Sequence reset to 1, resetting now.")
        fixSession.setTheirSeq(1) // ie we have used up 1.
        actionCallback(SfActionCounterpartyHeartbeat(logonMessage.heartBtIntField.value))
        Some(InitiationLogonResponse)
      }
    } else {
      handleSequenceNumberTooLow(fixSession) orElse {
        actionCallback(SfActionCounterpartyHeartbeat(logonMessage.heartBtIntField.value))
        Some(InitiationLogonResponse)
      }
    }
  }

  override protected[fixstate] def receiveFixMsg(fixSession: SfSession, msgIn: SfMessage, actionCallback: SfAction => Unit): Option[SfSessState] = {
    if (isResentDuplicate(fixSession, msgIn)) None
    else {
      msgIn.body match {
        case logonMessage: LogonMessage =>
          handleLogonAckMessage(fixSession, msgIn, logonMessage, actionCallback)
        case _ =>
          val msgType = msgIn.header.msgTypeField.value
          logger.warn(s"[${fixSession.idStr}] First message not a logon.  Received unexpected FIX message [$msgType]")
          Some(DisconnectSocketNow)
      }
    }
  }

  /**
    * The spec doesnt say this, but I recon if I send a logon and get no ack I should disconnect
    */
  override protected[fixstate] def receiveControlEvent(fixSession: SfSession, event: SfSessionControlEvent): Option[SfSessState] = {
    event match {
      case heartbeatTimeout: SfControlNoReceivedHeartbeatTimeout =>
        logger.info(s"[${fixSession.idStr}] No reply to Logon for at least " + heartbeatTimeout.noBeatsMissedPlus20Percent + " heartbeats + 20%, disconnecting socket")
        Some(DisconnectSocketNow)
      case ev@_ =>
        super.receiveControlEvent(fixSession, event)
    }
  }
}


