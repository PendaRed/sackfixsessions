package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class NetworkConnnectionEstablishedSpec extends AnyFlatSpec {
  behavior of "NetworkConnnectionEstablished"
  val session = new SfSessionStub

  it should "Correctly respond to a login by moving to next state" in {
    var heartbeat = -1
    NetworkConnnectionEstablished.receiveFixMsg(session, MessageFixtures.Logon, (a: SfAction) => a match {
      case h: SfActionCounterpartyHeartbeat => heartbeat = h.heartbeatSecs
      case _ => fail("Expected SfActionCounterpartyHeartbeatAction")
    }) match {
      case Some(InitiationLogonReceived) => // good
      case _ => fail("Expected InitiationLogonReceived")
    }
    // 30 is hardcoded in MessageFixtures.Logon
    assert(heartbeat == 30)
  }

  it should "Correctly set the sequence number" in {
    var heartbeat = -1
    val sess = new SfSessionStub
    sess.nextTheirSeqNum=50
    NetworkConnnectionEstablished.receiveFixMsg(sess, MessageFixtures.logonWithReset(1), (a: SfAction) => a match {
      case h: SfActionCounterpartyHeartbeat => heartbeat = h.heartbeatSecs
      case _ => fail("Expected SfActionCounterpartyHeartbeatAction")
    }) match {
      case Some(InitiationLogonReceived) => // good
      case _ => fail("Expected InitiationLogonReceived")
    }
    // 30 is hardcoded in MessageFixtures.Logon
    assert(heartbeat == 30)
    assert(sess.nextTheirSeqNum==1)
  }

  it should "Move to disconnect if its not a login" in {
    NetworkConnnectionEstablished.receiveFixMsg(session, MessageFixtures.NewOrderSingle, (a: SfAction) => {}) match {
      case Some(DisconnectSocketNow) => // good
      case _ => fail("Expected InitiationLogonReceived")
    }
  }
}
