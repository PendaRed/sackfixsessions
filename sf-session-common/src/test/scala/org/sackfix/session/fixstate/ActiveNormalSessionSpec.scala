package org.sackfix.session.fixstate

import org.sackfix.fix44._
import org.sackfix.session.{SfAction, _}
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class ActiveNormalSessionSpec extends FlatSpec {

  behavior of "ActiveNormalSession"
  val session = new SfSessionStub

  it should "handle no messages in heartbeat interval receiveControlEvent" in {
    ActiveNormalSession.receiveControlEvent(session, SfControlNoReceivedHeartbeatTimeout(1)) match {
      case Some(expected: NoMessagesReceivedInInterval) =>
      case _ => fail()
    }
  }

  it should "pass a business message back to the callback in receiveFixMsg" in {
    var passed = false
    val msg = MessageFixtures.NewOrderSingle
    ActiveNormalSession.receiveFixMsg(session, msg, (action: SfAction) => {
      action match {
        case SfActionBusinessMessage(b) => passed = (b == msg)
        case _ =>
      }
    })
    assert(passed)
  }

  def callback(a:SfAction) :Unit = {
    // bah
  }

  it should "handleNormalFixStuff" in {
    val sess = new SfSessionStub
    sess.setTheirSeq(20)
    sess.setLastTheirSeqNumForTesting(20)
    ActiveNormalSession.handleNormalFixStuff(sess, MessageFixtures.newOrderSingle(20, "cl1"), callback) match {
      case None => // pass
      case Some(st) => fail("Did not expect to move to state " + st)
    }
  }
  it should "handleNormalFixStuff for a resendRequest" in {
    val sess = new SfSessionStub
    ActiveNormalSession.handleNormalFixStuff(sess, MessageFixtures.resendRequest(10, 20), callback) match {
      case Some(r: HandleResendRequest) => //pass
      case v@_ => fail("Did not expect to move to state " + v)
    }
  }
  it should "handleNormalFixStuff for a testRequest" in {
    val sess = new SfSessionStub
    ActiveNormalSession.handleNormalFixStuff(sess, MessageFixtures.testRequest("REQ1"), callback) match {
      case Some(HandleTestRequest(initialState)) => //pass
      case v@_ => fail("Did not expect to move to state " + v)
    }
  }
  it should "handleNormalFixStuff for a sequencereset" in {
    val sess = new SfSessionStub
    ActiveNormalSession.handleNormalFixStuff(sess, MessageFixtures.sequenceResetMessage(13),callback) match {
      case Some(SessNoChangeEventConsumed) => //pass
        assert(sess.nextTheirSeqNum == 13)
      case None =>fail("Expected to move to SessNoChangeEventConsumed")
      case v @ _ =>fail("Did not expect to move to state "+v)
    }
  }
  it should "handleNormalFixStuff for a heartbeat" in {
    val sess = new SfSessionStub
    ActiveNormalSession.handleNormalFixStuff(sess, MessageFixtures.heartbeat(1),callback) match {
      case Some(SessNoChangeEventConsumed) => //pass
      case None =>fail("Expected to move to SessNoChangeEventConsumed")
      case v @ _ =>fail("Did not expect to move to state "+v)
    }
  }

}
