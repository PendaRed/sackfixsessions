package org.sackfix.session.fixstate

import java.time.LocalDateTime

import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class SfSessStateSpec extends FlatSpec {
  behavior of "SfSessState"

  case object TestSessState extends SfSessState(1,"testState",false, true, true) {}
  case object TestSessState1 extends SfSessState(2,"two",false, true, true) {}
  case object TestSessStateWithAction extends SfSessState(3,"three",false, true, true) {
    override def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction] = {
      List(SfActionCounterpartyHeartbeat(2))
    }
  }
  case object TestSessStateWithTransition extends SfSessState(4,"four",false, true, true) {
    override def nextState(fixSession:SfSession) : Option[SfSessState] = {
      Some(TestSessState1)
    }
  }

  class CallbackRecorder {
    var action:Option[SfAction] = None
    def callback(action:SfAction):Unit = {
      this.action = Some(action)
    }
  }

  it should "detect a 2 min gap" in {
    val now = LocalDateTime.now()

    val s =  TestSessState
    assert(false==s.isMoreThan2Mins(now, now.minusMinutes(1)))
    assert(false==s.isMoreThan2Mins(now, now.plusSeconds(100)))
    assert(true==s.isMoreThan2Mins(now, now.minusMinutes(3)))
    assert(true==s.isMoreThan2Mins(now, now.plusSeconds(121)))
  }

  it should "transition with no callback action, and stop" in {
    val sess = new SfSessionStub
    val s =  TestSessState
    val s1 =  TestSessState1
    val cb = new CallbackRecorder

    s.stateTransition(sess,s1, SfSessionServerSocketOpenEvent, cb.callback) match {
      case Some( newState @ TestSessState1 ) =>  // pass
      case v @ _ => fail("Expected state "+s1 + " and not "+v)
    }
    assert(cb.action==None)
  }
  it should "transition with callback action, and stop" in {
    val sess = new SfSessionStub
    val s =  TestSessState
    val s1 =  TestSessStateWithAction
    val cb = new CallbackRecorder

    s.stateTransition(sess,s1, SfSessionServerSocketOpenEvent, cb.callback) match {
      case Some( newState @ TestSessStateWithAction ) =>  // pass
      case v @ _ => fail("Expected state "+s1 + " and not "+v)
    }
    cb.action match {
      case Some(action:SfActionCounterpartyHeartbeat) => // correct
      case v @ _ => fail("Expected an action callback and not "+v)
    }
  }
  it should "transition with NO callback action, and another state" in {
    val sess = new SfSessionStub
    val s =  TestSessState
    val sAction =  TestSessState1
    val cb = new CallbackRecorder

    val newState = s.stateTransition(sess,sAction, SfSessionServerSocketOpenEvent, cb.callback)
    // So it calls TestSessState, with new state TestSessStateWithAction which immediately transitions
    // one more time to TestSessState1
    assert(cb.action==None)
    assert(newState.getOrElse(TestSessState) == TestSessState1)
  }

  it should "handleSequenceNumberTooLow" in {
    val sess = new SfSessionStub
    sess.setTheirSeq(20)
    sess.setLastTheirSeqNumForTesting(2)
    TestSessState.handleSequenceNumberTooLow(sess) match {
//      case Some(InitiateLogoutProcess(reason:String, closeSocketAfterSend)) =>
//        assert(reason=="Sequence number [2] too low, expected [20]")
//        assert(closeSocketAfterSend == false)
      case Some(DisconnectSocketNow)=> // pass
      case _ => fail("Expected to move to logout process")
    }
    sess.setLastTheirSeqNumForTesting(20)
    TestSessState.handleSequenceNumberTooLow(sess) match {
      case None => // correct
      case v@_ => fail("Expected to stay in the same state as now, not move to "+v)
    }
  }
  it should "handleSequenceNumberTooHigh" in {
    val sess = new SfSessionStub
    sess.setTheirSeq(20)
    sess.setLastTheirSeqNumForTesting(25)
    TestSessState.handleSequenceNumberTooHigh(sess) match {
      case Some(ReceiveMsgSeqNumTooHigh(20,25)) => // expected
      case _ => fail("Expected to move to replay process")
    }
    sess.setLastTheirSeqNumForTesting(20)
    TestSessState.handleSequenceNumberTooHigh(sess) match {
      case None => // correct
      case v@_ => fail("Expected to stay in the same state as now, not move to "+v)
    }
  }



  it should "receiveSocketEvent" in {
    val sess = new SfSessionStub
    TestSessState.receiveSocketEvent(sess, SfSessionSocketCloseEvent) match {
      case Some(AwaitingConnection) => // pass, its an acceptor socket so it goes here.
      case Some(state) => fail("Expected to go to broken socket state & not "+ state.getClass.getName)
      case state @_ => fail("Expected to go to broken socket state & not "+ state.getClass.getName)
    }
    TestSessState.receiveSocketEvent(sess, SfSessionNetworkConnectionEstablishedEvent) match {
      case Some(s) => fail("Expected to get None, but got "+s)
      case None => // pass
    }
  }
  it should "receiveControlEvent" in {
    val sess = new SfSessionStub
    TestSessState.receiveControlEvent(sess, SfControlNoReceivedHeartbeatTimeout(1)) match {
      case Some(s) => fail("Expected to get None, but got "+s)
      case None => // pass
    }
  }
}
