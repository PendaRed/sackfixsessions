package org.sackfix.session.fixstate

import org.sackfix.field.{EncryptMethodField, HeartBtIntField, ResetSeqNumFlagField}
import org.sackfix.fix44.LogonMessage
import org.sackfix.session._

/**
  * Transition state to send out a logon
  */
object InitiateConnection extends SfSessState(5,"Initiate Connecton",
    initiator = true, acceptor = false, isSessionOpen=false, isSessionSocketOpen=true) {
  private def getResetSetNumField(fixSession:SfSession, ev:SfSessionEvent) : Option[ResetSeqNumFlagField] = {
    // if ours is 1, then it means we have done a reset
    // if they logged in and asked for a reset then the acceptor (us) already reset to 1, so still good to
    // reply Y
    if (fixSession.nextMySeqNum==1) Some(ResetSeqNumFlagField("Y"))
    else None
  }

  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    List(SfActionSendMessageToFix(new LogonMessage(new EncryptMethodField(EncryptMethodField.NoneOther),
      new HeartBtIntField(fixSession.heartbeatIntervalSecs),
      resetSeqNumFlagField = getResetSetNumField(fixSession, ev))))
  }
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = Some(InitiationLogonSent)
}
