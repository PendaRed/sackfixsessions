package org.sackfix.session.fixstate

import org.sackfix.session.SfSessionStub
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class InitiationLogonSentSpec extends FlatSpec {
  behavior of "InitiationLogonSent"
  val session = new SfSessionStub

  it should "simply transition right thru to the next state - no idea what this state does...." in {
    InitiationLogonSent.nextState(session) match {
      case Some(WaitingForLogonAck) => // pass
      case _ => fail("Expected WaitingForLogonAck")
    }
  }
}
