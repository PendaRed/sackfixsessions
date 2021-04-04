package org.sackfix.session.fixstate

import org.sackfix.field.TextField
import org.sackfix.fix44.LogoutMessage
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class InitiateLogoutProcessSpec extends AnyFlatSpec {
  behavior of "InitiateLogoutProcess"

  it should "fire off a logon message with known cause" in {
    val session = new SfSessionStub
    session.nextTheirSeqNum = 10
    session.lastTheirSeqNum = 2
    InitiateLogoutProcess("Test reason").stateTransitionAction(session, SfSessionFixMessageEvent(MessageFixtures.newOrderSingle(2, "clordid"))) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions.head match {
          case SfActionSendMessageToFix(msg: LogoutMessage) =>
            assert(msg.textField.getOrElse(TextField("")).value == "Test reason")
          case _ => fail("Expected an action to fire off a logon message (ie the ack)")
        }
      case _ => fail("Expected an action to fire off a logon message (ie the ack)")
    }
  }
  it should "fire off a logon message with unknown cause" in {
    val session = new SfSessionStub
    session.nextTheirSeqNum = 2
    session.lastTheirSeqNum = 2
    InitiateLogoutProcess("Test reason").stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.newOrderSingle(2, "clordid"))) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions.head match {
          case SfActionSendMessageToFix(msg: LogoutMessage) =>
            assert(msg.textField.getOrElse(TextField("")).value == "Test reason")
          case _ => fail("Expected an action to fire off a logon message (ie the ack)")
        }
      case _ => fail("Expected an action to fire off a logon message (ie the ack)")
    }
  }

  it should "not transition to another state" in {
    InitiateLogoutProcess("Test").nextState(new SfSessionStub) match {
      case None => // pass
      case _ => fail("Expected None")
    }
  }

  it should "respond to the return logout by diconnecting " in {
    InitiateLogoutProcess("Test").receiveFixMsg(new SfSessionStub, MessageFixtures.Logout, (a: SfAction) => {}) match {
      case Some(DisconnectSocketNow) => // pass
      case _ => fail("Expected a DisconnectSocketNow state transition")
    }
  }

  it should "Disconnect the socket after a reasonable time" in {
    InitiateLogoutProcess("Test").receiveControlEvent(new SfSessionStub, SfControlNoReceivedHeartbeatTimeout(1)) match {
      case Some(DisconnectSocketNow) => // pass
      case _ => fail("Expected a DisconnectSocketNow state transition")
    }
  }
}
