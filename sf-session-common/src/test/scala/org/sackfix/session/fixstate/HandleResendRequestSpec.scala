package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class HandleResendRequestSpec extends FlatSpec {
  behavior of "HandleResendRequest"
  val session = new SfSessionStub

  it should "fire off a resend of everything" in {
    {
      new HandleResendRequest(ActiveNormalSession)
    }.
      stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.resendRequest(2, 9))) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions(0) match {
          case SfActionResendMessages(beginSeqNo: Int, endSeqNo: Int) =>
            assert(beginSeqNo == 2)
            assert(endSeqNo == 9)
          case _ => fail("Expected an action to fire off a SfActionResendMessages")
        }
      case _ => fail("Expected an action to fire off a SfActionResendMessages")
    }
  }
  it should "fail to return an action for anything other than a resent request" in {
    {
      new HandleResendRequest(ActiveNormalSession)
    }.
      stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.NewOrderSingle)) match {
      case l:List[SfAction] if (l.isEmpty) => // good
      case _ => fail("Expected None")
    }
  }
  it should "fail to return an action for anything other than a FixMessageEvent" in {
    {
      new HandleResendRequest(ActiveNormalSession)
    }.
      stateTransitionAction(new SfSessionStub, SfSessionServerSocketOpenEvent) match {
      case l:List[SfAction] if (l.isEmpty) => // good
      case _ => fail("Expected None")
    }
  }

  it should "transition to InitiationLogonSent" in {
    {
      new HandleResendRequest(ActiveNormalSession)
    }.nextState(new SfSessionStub) match {
      case Some(ActiveNormalSession) => // pass
      case _ => fail("Expected Some(ActiveNormalSession)")
    }
  }
}
