package org.sackfix.session.fixstate

import org.sackfix.session.{SfAction, SfActionCounterpartyHeartbeat, SfSessionStub}
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class WaitingForLogonAckSpec extends FlatSpec {
  behavior of ""
  val session = new SfSessionStub

  it should "Handle an expected logon Ack" in {
    var heartbeat = -1
    val msg = MessageFixtures.Logon
    WaitingForLogonAck.receiveFixMsg(session, msg, (a: SfAction) =>  a match {
      case h: SfActionCounterpartyHeartbeat => heartbeat = h.heartbeatSecs
      case _ => fail("Expected SfActionCounterpartyHeartbeatAction")
    }) match {
      case Some(InitiationLogonResponse) =>
      case c@_ => fail("Expected a new state of InitiationLogonResponse " + c.toString)
    }
  }


  it should "Correctly set the sequence number" in {
    var heartbeat = -1
    val sess = new SfSessionStub
    sess.nextTheirSeqNum=50
    WaitingForLogonAck.receiveFixMsg(sess, MessageFixtures.logonWithReset(1), (a: SfAction) => a match {
      case h: SfActionCounterpartyHeartbeat => heartbeat = h.heartbeatSecs
      case _ => fail("Expected SfActionCounterpartyHeartbeatAction")
    }) match {
      case Some(InitiationLogonResponse) => // good
      case _ => fail("Expected InitiationLogonReceived")
    }
    // 30 is hardcoded in MessageFixtures.Logon
    assert(heartbeat == 30)
    assert(sess.nextTheirSeqNum==1)
  }

  it should "Disconnect if it doesn't get a logon Ack" in {
    val msg = MessageFixtures.NewOrderSingle
    WaitingForLogonAck.receiveFixMsg(session, msg, (action: SfAction) => {
      fail("Did not expect a callback")
    }) match {
      case Some(DisconnectSocketNow) =>
      case c@_ => fail("Expected a new state of DisconnectSocketNow " + c.toString)
    }
  }

}
