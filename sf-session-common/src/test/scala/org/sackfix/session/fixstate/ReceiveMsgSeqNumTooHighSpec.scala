package org.sackfix.session.fixstate

import org.sackfix.fix44.ResendRequestMessage
import org.sackfix.session.{SfAction, SfActionSendMessageToFix, SfSessionFixMessageEvent, SfSessionStub}
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class ReceiveMsgSeqNumTooHighSpec extends AnyFlatSpec {
  behavior of "ReceiveMsgSeqNumTooHigh"

  it should "Generate a resent action back to the session" in {
    val session = new SfSessionStub
    session.nextTheirSeqNum = 10
    session.lastTheirSeqNum = 20
    val ev = SfSessionFixMessageEvent(MessageFixtures.NewOrderSingle)
    ReceiveMsgSeqNumTooHigh(10,20).stateTransitionAction(session, ev) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions.head match {
          case SfActionSendMessageToFix(msg: ResendRequestMessage) =>
            assert(msg.beginSeqNoField.value == 10)
            assert(msg.endSeqNoField.value == 20)
          case _ => fail("Unexpected result")
        }
      case _ => fail("Unexpected result")
    }
  }
  it should "Create the next state holding the correct start and end seq nums" in {
    val session = new SfSessionStub
    session.nextTheirSeqNum = 11
    session.lastTheirSeqNum = 21
    ReceiveMsgSeqNumTooHigh(11,21).nextState(session) match {
      case Some(state: AwaitingProcessingResponseToResendRequest) =>
        assert(state.beginSeqNum == 11)
        assert(state.endSeqNum == 21)
      case _ => fail("Unexpected result")
    }
  }
}
