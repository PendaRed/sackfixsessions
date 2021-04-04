package org.sackfix.session.fixstate

import org.sackfix.field.TestReqIDField
import org.sackfix.fix44.HeartbeatMessage
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class HandleTestRequestSpec extends AnyFlatSpec {
  behavior of "HandleTestRequest"
  val session = new SfSessionStub

  it should "fire off a logon message" in {
    {
      new HandleTestRequest(ActiveNormalSession)
    }.
      stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.testRequest("reqwId"))) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions(0) match {
          case SfActionSendMessageToFix(msg: HeartbeatMessage) =>
            assert(msg.testReqIDField.getOrElse(TestReqIDField("")).value == "reqwId")
          case _ => fail("Expected an action to fire off a heartbeat message")
        }
      case _ => fail("Expected an action to fire off a heartbeat message")
    }
  }

  it should "transition to InitiationLogonSent" in {
    {
      new HandleTestRequest(ActiveNormalSession)
    }.nextState(new SfSessionStub) match {
      case Some(ActiveNormalSession) => // pass
      case _ => fail("Expected Some(ActiveNormalSession)")
    }
  }
}
