package org.sackfix.session.fixstate

import org.sackfix.session.SfSession

/**
  * Transition state from the spec, we got a login, we sent the ack, now either deal with
  * msg seq num too high, or just go to active normal state
  */
object InitiationLogonResponse extends SfSessState(9,"Initiation Logon Response",
      initiator = false, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = {
    handleSequenceNumberTooHigh(fixSession) orElse {
      // If the seq num was too high, we need replay from their last seq.  If not then hurrah, increase seq num for next message
      fixSession.incrementTheirSeq
      Some(ActiveNormalSession)
    }
  }
}
