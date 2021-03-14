package org.sackfix.session.fixstate

import org.sackfix.session.SfSessionStub
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class InitiationLogonResponseSpec extends AnyFlatSpec {
  behavior of "InitiationLogonResponse"
  val session = new SfSessionStub

  it should "should pass us right through to activeNormalSession" in {
    InitiationLogonResponse.nextState(session) match {
      case Some(ActiveNormalSession) => // pass
      case _ => fail("Expected ActiveNormalSession")
    }
  }
}
