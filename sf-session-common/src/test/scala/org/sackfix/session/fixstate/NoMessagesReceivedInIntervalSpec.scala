package org.sackfix.session.fixstate

import org.sackfix.fix44.TestRequestMessage
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class NoMessagesReceivedInIntervalSpec extends AnyFlatSpec {
  behavior of "NoMessagesReceivedInInterval"
  val session = new SfSessionStub

  it should "Generate the TestRequest on state transition" in {
    (new NoMessagesReceivedInInterval).stateTransitionAction(session, SfControlNoReceivedHeartbeatTimeout(1)) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions.head match {
          case SfActionSendMessageToFix(msg: TestRequestMessage) =>
            assert(msg.testReqIDField.value.length > 0)
          case _ => fail("Expected Some(SfActionSendMessageAction(TestRequestMessage))")
        }
      case _ => fail("Expected Some(SfActionSendMessageAction(TestRequestMessage))")
    }
  }

  it should "Move on to the correct next state" in {
    (new NoMessagesReceivedInInterval).nextState(session) match {
      case Some(AwaitingProcessingResponseToTestRequest(reqId: String)) =>
        assert(reqId.length > 0)
      case _ => fail("Expected ome(AwaitingProcessingResponseToTestRequest()")
    }
  }
}
