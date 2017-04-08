package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class DisconnectSocketNowSpec extends FlatSpec {
  behavior of "DisconnectSocketNow"
  val session = new SfSessionStub

  it should "fire off a close socket" in {
    DisconnectSocketNow.
      stateTransitionAction(new SfSessionStub, SfSessionFixMessageEvent(MessageFixtures.NewOrderSingle)) match {
      case actions:List[SfAction] => assert(actions.size==1)
        actions.head match {
          case SfActionCloseSocket() => // pass
          case _ => fail("Expected an action to fire off a Some(SfActionCloseSocketAction())")
        }
      case _ => fail("Expected an action to fire off a Some(SfActionCloseSocketAction())")
    }
  }

  it should "transition to DisconnectedConnectionToday" in {
    DisconnectSocketNow.nextState(new SfSessionStub) match {
      case Some(AwaitingConnection) => // pass
      case _ => fail("Expected Some(AwaitingConnection)")
    }
  }

}
