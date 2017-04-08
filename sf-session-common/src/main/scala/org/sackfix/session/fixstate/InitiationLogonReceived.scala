package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.field.{EncryptMethodField, HeartBtIntField, ResetSeqNumFlagField}
import org.sackfix.fix44.LogonMessage
import org.sackfix.session._

/**
  * Transition state, ack the login and go to logon response
  */
object InitiationLogonReceived extends SfSessState(8, "Initiation Logon Received",
    initiator = false, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  private def getResetSetNumField(fixSession:SfSession, ev:SfSessionEvent) : Option[ResetSeqNumFlagField] = {
    // if ours is 1, then it means we have done a reset
    // if they logged in and asked for a reset then the acceptor (us) already reset to 1, so still good to
    // reply Y
    if (fixSession.nextMySeqNum==1) Some(ResetSeqNumFlagField("Y"))
    else None
  }

  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    ev match {
      case msgEv:SfSessionFixMessageEvent => msgEv.msg.body match {
        case l:LogonMessage =>
          l.resetSeqNumFlagField.foreach(resetFlag=> if (resetFlag.value) {
            logger.info("[{}] Login asked to reset their sequence number to 1", fixSession.idStr)
            // msg 1 already used up.
            fixSession.setTheirSeq(1)
          })
      }
      case e @ _ => logger.error(s"[${fixSession.idStr}] Received an event [${e.getClass.getName}] which indicates a coding bug - should only get a logon fix msg, continue anyway")
    }
    List(SfActionSendMessageToFix(new LogonMessage(new EncryptMethodField(EncryptMethodField.NoneOther),
      new HeartBtIntField(fixSession.heartbeatIntervalSecs),
      resetSeqNumFlagField = getResetSetNumField(fixSession, ev))))
  }
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = Some(InitiationLogonResponse)
}
