package org.sackfix.session.fixstate

import org.sackfix.fix44.LogoutMessage
import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class ReceiveLogoutMessageSpec extends FlatSpec {
  behavior of "ReceiveLogoutMessage"

  it should "Correctly generate the message on state transition" in {
    val session = new SfSessionStub
    ReceiveLogoutMessage.stateTransitionAction(session, SfSessionFixMessageEvent(MessageFixtures.Logout)) match {
      case actions:List[SfAction] =>
        println(actions)
        assert(actions.size==2)
        actions.head match {
          case SfActionSendMessageToFix(msg: LogoutMessage) => // good
          case _ => fail("Expected a message action")
        }
        actions(1) match {
          case SfActionStartTimeout(id ,durationMs) => assert(10000 == durationMs)
          case _ => fail("Expected a message action")
        }
      case _ => fail("Expected a message action")
    }
  }
  it should "Correctly move right on to the next state" in {
    val session = new SfSessionStub
    ReceiveLogoutMessage.receiveControlEvent(session, SfControlTimeoutFired("LogoutResponseDisconnect10sWait", 10000)) match {
      case Some(DisconnectSocketNow) => // good
      case Some(oth) => fail("Expected Some(DisconnectSocketNow), note "+oth)
      case _ => fail("Expected Some(DisconnectSocketNow)")
    }
  }
}
