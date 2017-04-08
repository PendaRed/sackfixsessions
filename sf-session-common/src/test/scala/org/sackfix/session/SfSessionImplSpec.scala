package org.sackfix.session

import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, DateTimeParseException}

import org.sackfix.common.message.SfMessageHeader
import org.sackfix.field._
import org.sackfix.session.filebasedstore.SfFileMessageStore
import org.sackfix.session.fixstate.{ActiveNormalSession, MessageFixtures}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

/**
  * Created by Jonathan during 2016.
  */
class SfSessionImplSpec extends FlatSpec with BeforeAndAfterAll {
  behavior of "SfSessionImpl"

  val sessId = new SfSessionId("Fix4.2", "TargFGW", "SendFGW")

  val messageStore = new SfFileMessageStore("./test/resources/tmp/acceptor")
  val msgStoreDetails = Some(messageStore)
  override protected def beforeAll() = {
    val sess = SfSessionImpl(SfAcceptor, Some(messageStore), new SfSessionActorOutActionsStub, sessId, 30)
    sess.openStore(false)
    sess.close
  }

  it should "Create a default session with file store and persist seq numbers" in {
    val session = SfSessionImpl(SfAcceptor, msgStoreDetails, new SfSessionActorOutActionsStub, sessId, 30)
    session.openStore(false)

    session.incrementMySeq
    session.incrementMySeq
    session.incrementTheirSeq
    session.close

    val session2 = SfSessionImpl(SfAcceptor, msgStoreDetails, new SfSessionActorOutActionsStub, sessId, 30)
    session2.openStore(true)
    assert(session2.nextMySeqNum == session.nextMySeqNum)
    assert(session2.nextTheirSeqNum == session.nextTheirSeqNum)
    session2.close
  }

  it should "reset seq nums" in {
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), new SfSessionActorOutActionsStub, sessId, 30)
    session.incrementMySeq
    session.incrementTheirSeq

    store.storeMySequenceNumber(sessId,10)
    store.storeTheirSequenceNumber(sessId,20)

    session.resetSeqNums

    assert(store.getInfo(sessId).mySeq == 1)
    assert(store.getInfo(sessId).theirSeq == 1)
  }

  it should "setTheirSeq" in {
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), new SfSessionActorOutActionsStub, sessId, 30)
    assert(session.setTheirSeq(20) == 20)
    assert(store.getInfo(sessId).theirSeq == 20)
  }
  it should "incrementMySeq" in {
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), new SfSessionActorOutActionsStub, sessId, 30)
    session.incrementMySeq
    assert(session.nextMySeqNum == 2)
    assert(store.getInfo(sessId).mySeq == 2)
  }
  it should "incrementTheirSeq" in {
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), new SfSessionActorOutActionsStub, sessId, 30)
    session.incrementTheirSeq
    assert(session.nextTheirSeqNum == 2)
    assert(store.getInfo(sessId).theirSeq == 2)
  }

  it should "createMessage and inc seq num" in {
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some((store)), new SfSessionActorOutActionsStub, sessId, 30)
    val currSeq = session.nextMySeqNum
    val msg = session.createMessage(MsgTypeField.Logon, MessageFixtures.LogonMessageBody)
    assert(store.getInfo(sessId).mySeq == currSeq + 1)
  }

  it should "createMessage" in {
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some((store)), new SfSessionActorOutActionsStub, sessId, 30)
    session.incrementMySeq
    val msg = session.createMessage(5, MsgTypeField.Logon, MessageFixtures.LogonMessageBody)
    assert(store.getInfo(sessId).mySeq == session.nextMySeqNum)
    assert(msg.header.msgSeqNumField.value == 5)
    assert(msg.header.beginStringField.value == "Fix4.2")
    assert(msg.header.msgTypeField.value == MsgTypeField.Logon)
    assert(msg.header.senderCompIDField.value == "TargFGW")
    assert(msg.header.targetCompIDField.value == "SendFGW")
  }

  it should "close" in {
    val store = new SfMessageStoreStub
    val now = LocalDateTime.now()
    val session = SfSessionImpl(SfAcceptor, Some((store)), new SfSessionActorOutActionsStub, sessId, 30)
    session.close
    session.lastCloseTime match {
      case Some(lct) => assert(lct == now || lct.isAfter(now))
      case None => fail("Expected a last close time")
    }
  }

  it should "send back messages to be sent on the outbound connection" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)

    val seqNo = session.nextMySeqNum
    session.sendAMessage(MessageFixtures.LogonMessageBody)

    val fixMsg = store.readMessage(sessId,seqNo)
    val msg = commsStub.fixMessages(0)

    // message is of the form below, so strip out the time element
    // 8=Fix4.29=6935=A49=SendFGW56=TargFGW34=252=20170105-11:38:05.44298=0108=3010=242
    val msgStr = SessionTestTimeUtil.stripTimeAndCheckSum(msg)
    assert(msgStr == "8=Fix4.2\u00019=69\u000135=A\u000149=TargFGW\u000156=SendFGW\u000134=1\u000152=TSTAMPREMOVED\u000198=0\u0001108=30\u000110=\u0001")
  }

  it should "handleMessage which is well formed" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)
    session.setTheirSeq(20)

    session.sessionState = ActiveNormalSession
    val m = MessageFixtures.newOrderSingle(20, "Ord1234")
    session.handleMessage(m)
    val msgBody = commsStub.businessMessages(0)
    assert(msgBody == m.body)
  }

  it should "handleAction close socket" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)

    session.handleAction(new SfActionCloseSocket)

    assert(commsStub.closeSessionCalled == true)
  }
  it should "handleAction SfActionSendMessageAction" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)
    session.nextMySeqNum=20

    session.sessionState = ActiveNormalSession
    val m = MessageFixtures.Logout
    session.handleAction(SfActionSendMessageToFix(m.body))

    val msg = commsStub.fixMessages(0)
    val msgStr = SessionTestTimeUtil.stripTimeAndCheckSum(msg)
    assert(msgStr == "8=Fix4.2\u00019=80\u000135=5\u000149=TargFGW\u000156=SendFGW\u000134=20\u000152=TSTAMPREMOVED\u000158=I am a test logout\u000110=\u0001")
  }

  it should "handleAction SfActionCounterpartyHeartbeatAction" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)
    session.counterpartyHeartbeatIntervalSecs = 1

    session.sessionState = ActiveNormalSession
    session.handleAction(SfActionCounterpartyHeartbeat(300))

    assert(session.counterpartyHeartbeatIntervalSecs == 300)
  }
  it should "handleAction SfActionBusinessMessageAction" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)
    session.setTheirSeq(20)

    session.sessionState = ActiveNormalSession
    val m = MessageFixtures.newOrderSingle(20, "Ord1234")
    session.handleAction(SfActionBusinessMessage(m))

    val msgBody = commsStub.businessMessages(0)
    assert(msgBody == m.body)
  }
  it should "handleAction SfActionResendMessages with no messages stored - sequencereset gapfill" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)
    session.setTheirSeq(20)

    session.sessionState = ActiveNormalSession
    session.handleAction(SfActionResendMessages(14, 20))
    // expect a gapfill with sequence reset to 21
    val msg = commsStub.fixMessages(0)
    val msgStr = SessionTestTimeUtil.stripTimeAndCheckSum(msg)
    assert(msgStr == "8=Fix4.2\u00019=70\u000135=4\u000149=TargFGW\u000156=SendFGW\u000134=14\u000152=TSTAMPREMOVED\u0001123=Y\u000136=21\u000110=\u0001")
  }

  it should "handleAction SfActionResendMessages" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, sessId, 30)

    // Pretend we sent 3 NewOrd singles with a heartbeat in between
    session.sessionState = ActiveNormalSession
    for (seqNum <- 0 to 6) {
      val m = if (seqNum % 2 == 0) MessageFixtures.newOrderSingle(seqNum, "Ord" + seqNum)
      else MessageFixtures.heartbeat(seqNum)
      session.sendAMessage(m.body)
    }
    // Now make the session ask for a replay from msg 4 to 6.  Which should replay
    // a new ord, a sequenceReset gapfill=Y, a new ord
    val len = commsStub.fixMessages.size
    session.handleAction(SfActionResendMessages(4, 6))

    val expectedReply = List[String](
      "8=Fix4.2\u00019=119\u000135=D\u000149=SendFGW\u000156=TargFGW\u000134=4\u000152=TSTAMPREMOVED\u000111=Ord2\u000155=JPG.GB\u000154=1\u000160=TSTAMPREMOVED\u000138=100.0\u000140=1\u000110=\u0001",
      "8=Fix4.2\u00019=68\u000135=4\u000149=SendFGW\u000156=TargFGW\u000134=5\u000152=TSTAMPREMOVED\u0001123=Y\u000136=6\u000110=\u0001",
      "8=Fix4.2\u00019=119\u000135=D\u000149=SendFGW\u000156=TargFGW\u000134=6\u000152=TSTAMPREMOVED\u000111=Ord4\u000155=JPG.GB\u000154=1\u000160=TSTAMPREMOVED\u000138=100.0\u000140=1\u000110=\u0001"
    )

    for (n <- 0 to 2) {
      val msg = commsStub.fixMessages(len + n)
      val msgStr = SessionTestTimeUtil.stripTimeAndCheckSum(msg)
      println(msgStr)
//      assert(msgStr == expectedReply(n))
    }
    MsgTypeField.SequenceReset
  }

  it should "Correctly match header fields to a session id" in {
    val commsStub = new SfSessionActorOutActionsStub
    val store = new SfMessageStoreStub
    val tstSessId = new SfSessionId("Fix4.4", "SendFGW", "TargFGW")
    val session = SfSessionImpl(SfAcceptor, Some(store), commsStub, tstSessId, 30)

    val head = new SfMessageHeader(beginStringField = BeginStringField("Fix4.4"),
      msgTypeField = MsgTypeField(MsgTypeField.OrderSingle),
      senderCompIDField = SenderCompIDField("TargFGW"),
      targetCompIDField = TargetCompIDField("SendFGW"),
      msgSeqNumField = MsgSeqNumField(1),
      sendingTimeField = SendingTimeField("20170101-10:26:32"))
    // Note it reverses targ and send
    assert(session.compIdsCorrect(head))

    val headDiff = new SfMessageHeader(beginStringField = BeginStringField("Fix4.4"),
      msgTypeField = MsgTypeField(MsgTypeField.OrderSingle),
      senderCompIDField = SenderCompIDField("TargFGW"),
      targetCompIDField = TargetCompIDField("DiffSendFGW"),
      msgSeqNumField = MsgSeqNumField(1),
      sendingTimeField = SendingTimeField("20170101-10:26:32"))
    assert(session.compIdsCorrect(headDiff)==false)
  }

}
