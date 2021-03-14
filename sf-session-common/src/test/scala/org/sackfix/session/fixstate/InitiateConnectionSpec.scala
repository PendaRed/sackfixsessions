package org.sackfix.session.fixstate

import org.sackfix.fix44.LogonMessage
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class InitiateConnectionSpec extends AnyFlatSpec {
  behavior of "InitiateConnection"
  val session = new SfSessionStub

  it should "fire off a logon message" in {
    InitiateConnection.stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.Logon)) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions(0) match {
          case SfActionSendMessageToFix(msg: LogonMessage) =>
            assert(msg.heartBtIntField.value == 30)
          case _ => fail("Expected an action to fire off a logon message")
        }
      case _ => fail("Expected an action to fire off a logon message")
    }
  }

  it should "transition to InitiationLogonSent" in {
    InitiateConnection.nextState(new SfSessionStub) match {
      case Some(InitiationLogonSent) => // pass
      case _ => fail("Expected Some(InitiationLogonSent)")
    }
  }
}
