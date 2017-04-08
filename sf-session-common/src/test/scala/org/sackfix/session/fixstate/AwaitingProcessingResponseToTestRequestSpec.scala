package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.fix44.HeartbeatMessage
import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class AwaitingProcessingResponseToTestRequestSpec extends FlatSpec {
  behavior of "AwaitingProcessingResponseToTestRequest"
  val session = new SfSessionStub

  it should "Return to the active normal state if get a heartbeat" in {
    val state = AwaitingProcessingResponseToTestRequest("reqId")
    state.receiveFixMsg(session, MessageFixtures.heartbeat("reqId"),(action:SfAction)=>{}) match {
      case Some(ActiveNormalSession) => // pass
      case _ => fail("Expected to return to the ActiveNormalSession state")
    }
  }

  it should "Handle any other fix message as normal..." in {
    val state = AwaitingProcessingResponseToTestRequest("reqId")
    var passed = false
    state.receiveFixMsg(session, MessageFixtures.NewOrderSingle,(action:SfAction)=>{
      passed = true
    }) match {
      case None => // pass
      case _ => fail("Expected no state change")
    }
    assert(passed)
  }

  /**
    * If get 2* heartbeat period then logout with Text=“Test Request Timeout”
    */
  it should "Do nothing with 1 missed heartbeat" in {
    val state = AwaitingProcessingResponseToTestRequest("reqId")
    state.receiveControlEvent(session, SfControlNoReceivedHeartbeatTimeout(1)) match {
      case None => // pass
      case _ => fail("Expected to remain in same state, has to miss 2 heartbeats")
    }
  }
  it should "Logout after two missed heartbeats" in {
    val state = AwaitingProcessingResponseToTestRequest("reqId")
    state.receiveControlEvent(session, SfControlNoReceivedHeartbeatTimeout(2)) match {
      case Some(InitiateLogoutProcess(reason:String, logoutNext:Boolean, delay:Option[Long])) => assert(reason=="Test Request Timeout")
      case _ => fail("Expected to get Some(InitiateLogoutProcess)")
    }
  }
}
