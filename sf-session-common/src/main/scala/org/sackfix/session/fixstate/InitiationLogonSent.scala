package org.sackfix.session.fixstate

import org.sackfix.session.SfSession

/**
  * transition state to awaiting logon ack
  */
object InitiationLogonSent extends SfSessState(7, "Initiation Logon Sent",
    initiator = true, acceptor = false, isSessionOpen=false, isSessionSocketOpen=true) {
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = Some(WaitingForLogonAck)
}
