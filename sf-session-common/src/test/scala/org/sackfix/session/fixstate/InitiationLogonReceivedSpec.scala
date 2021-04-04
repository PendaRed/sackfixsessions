package org.sackfix.session.fixstate

import org.sackfix.fix44.LogonMessage
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class InitiationLogonReceivedSpec extends AnyFlatSpec {
  behavior of "InitiationLogonReceived"

  it should "fire off a logon message" in {
    InitiationLogonReceived.stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.Logon)) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions.head match {
          case SfActionSendMessageToFix(msg: LogonMessage) =>
            assert(msg.heartBtIntField.value == 30)
          case _ => fail("Expected an action to fire off a logon message (ie the ack)")
        }
      case _ => fail("Expected an action to fire off a logon message (ie the ack)")
    }
  }

  it should "transition to InitiationLogonResponse" in {
    InitiationLogonReceived.nextState(new SfSessionStub) match {
      case Some(InitiationLogonResponse) => // pass
      case _ => fail("Expected Some(InitiationLogonResponse)")
    }
  }
}
