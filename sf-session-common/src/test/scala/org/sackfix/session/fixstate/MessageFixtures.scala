package org.sackfix.session.fixstate

import java.awt.TrayIcon.MessageType
import java.time.LocalDateTime

import org.sackfix.common.message.{SfMessage, SfMessageHeader, SfMessageTrailer}
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{BeginSeqNoField, EndSeqNoField, LeavesQtyField, _}
import org.sackfix.fix44._

/**
  * Created by Jonathan on 01/01/2017.
  */
object MessageFixtures {
  val head = new SfMessageHeader(beginStringField = BeginStringField("Fix4.2"),
    msgTypeField = MsgTypeField(MsgTypeField.OrderSingle),
    senderCompIDField = SenderCompIDField("SendFGW"),
    targetCompIDField = TargetCompIDField("TargFGW"),
    msgSeqNumField = MsgSeqNumField(1),
    sendingTimeField = SendingTimeField("20170101-10:26:32"))

  def createHeader(seqNo:Int, msgType:String):SfMessageHeader = {
    new SfMessageHeader(beginStringField = BeginStringField("Fix4.2"),
      msgTypeField = MsgTypeField(msgType),
      senderCompIDField = SenderCompIDField("SendFGW"),
      targetCompIDField = TargetCompIDField("TargFGW"),
      msgSeqNumField = MsgSeqNumField(seqNo),
      sendingTimeField = new SendingTimeField(LocalDateTime.now)) //"20170101-10:26:32"))
  }
  def createHeader(bodyLen:Int, seqNo:Int, msgType:String, sendingTimeField:SendingTimeField = SendingTimeField("20170101-10:26:32")) = {
    new SfMessageHeader(beginStringField = BeginStringField("Fix4.2"),
      bodyLengthField = Some(BodyLengthField(bodyLen)),
      msgTypeField = MsgTypeField(msgType),
      senderCompIDField = SenderCompIDField("SendFGW"),
      targetCompIDField = TargetCompIDField("TargFGW"),
      msgSeqNumField = MsgSeqNumField(seqNo),
      sendingTimeField = sendingTimeField)
  }
  def createHeaderWithNowTime(seqNo:Int, msgType:String):SfMessageHeader = {
    new SfMessageHeader(beginStringField = BeginStringField("Fix4.2"),
      msgTypeField = MsgTypeField(msgType),
      senderCompIDField = SenderCompIDField("SendFGW"),
      targetCompIDField = TargetCompIDField("TargFGW"),
      msgSeqNumField = MsgSeqNumField(seqNo),
      sendingTimeField = SendingTimeField(LocalDateTime.now))
  }

  val LogonMessageBody = {
    new LogonMessage(encryptMethodField = EncryptMethodField(EncryptMethodField.NoneOther),
      heartBtIntField = HeartBtIntField(30))
  }
  val Logon : SfMessage = {
    val body = LogonMessageBody
    val head = createHeader(1,body.msgType)
    createValidFixMessage(head, body)
  }
  def logonWithReset(seqNum:Int) : SfMessage = {
    val body = new LogonMessage(encryptMethodField = EncryptMethodField(EncryptMethodField.NoneOther),
      resetSeqNumFlagField=Some(ResetSeqNumFlagField("Y")),
      heartBtIntField = HeartBtIntField(30))
    val head = createHeader(seqNum,body.msgType)
    createValidFixMessage(head, body)
  }
  val Logout : SfMessage = {
    createValidFixMessage(createHeader(1,MsgTypeField.Logout), LogoutMessage(textField = Some(TextField("I am a test logout"))))
  }

  def logoutWithNowTime(seqNum:Int, reason:String) : SfMessage = {
    createValidFixMessage(createHeaderWithNowTime(seqNum,MsgTypeField.Logout), LogoutMessage(textField = Some(TextField(reason))))
  }

  def testRequest(reqId:String) : SfMessage = {
    createValidFixMessage(createHeader(1,MsgTypeField.TestRequest), TestRequestMessage(testReqIDField=TestReqIDField(reqId)))
  }
  def heartbeat(reqId:String, seqNum:Int=1) : SfMessage = {
    createValidFixMessage(createHeader(seqNum,MsgTypeField.Heartbeat), HeartbeatMessage(testReqIDField=Some(TestReqIDField(reqId))))
  }
  def heartbeat(seqNum:Int) : SfMessage = {
    createValidFixMessage(createHeader(seqNum,MsgTypeField.Heartbeat), HeartbeatMessage())
  }
  def resendRequest(beginSeqNo:Int, endSeqNo:Int) : SfMessage = {
    createValidFixMessage(createHeader(1,MsgTypeField.ResendRequest), ResendRequestMessage(BeginSeqNoField(beginSeqNo),
      EndSeqNoField(endSeqNo)))
  }

  def sequenceResetMessage(newSeq:Int) :SfMessage = {
    createValidFixMessage(createHeader(1,MsgTypeField.SequenceReset), SequenceResetMessage(newSeqNoField = NewSeqNoField(newSeq)))
  }

  def newOrderSingle(seqNo:Int, clOrdID : String) : SfMessage = {
    val head = createHeader(seqNo, MsgTypeField.OrderSingle)
    val body = new NewOrderSingleMessage(clOrdIDField = ClOrdIDField(clOrdID),
      instrumentComponent = InstrumentComponent(symbolField = SymbolField("JPG.GB")),
      sideField = SideField(SideField.Buy),
      transactTimeField = TransactTimeField("20170101-10:26:32"),
      orderQtyDataComponent = OrderQtyDataComponent(orderQtyField = Some(OrderQtyField(100))),
      ordTypeField = OrdTypeField(OrdTypeField.Market))
    // Because we are injecting this message into the fix handler, we want body len and
    // checksum to be valid, which means we must generate them by asking for the fix string
    createValidFixMessage(head, body)
  }
  def newOrderSingleNowTime(seqNo:Int, clOrdID : String) : SfMessage = {
    val head = createHeaderWithNowTime(seqNo, MsgTypeField.OrderSingle)
    val body = new NewOrderSingleMessage(clOrdIDField = ClOrdIDField(clOrdID),
      instrumentComponent = InstrumentComponent(symbolField = SymbolField("JPG.GB")),
      sideField = SideField(SideField.Buy),
      transactTimeField = TransactTimeField(LocalDateTime.now),
      orderQtyDataComponent = OrderQtyDataComponent(orderQtyField = Some(OrderQtyField(100))),
      ordTypeField = OrdTypeField(OrdTypeField.Market))
    // Because we are injecting this message into the fix handler, we want body len and
    // checksum to be valid, which means we must generate them by asking for the fix string
    createValidFixMessage(head, body)
  }


  private def createValidFixMessage(head:SfMessageHeader, body:SfFixMessageBody) : SfMessage = {
    val interimMsg = new SfMessage(head, body)
    interimMsg.fixStr
    new SfMessage(createHeader(head.lastCalculatedBodyLength.value,head.msgSeqNumField.value,
      head.msgTypeField.value, head.sendingTimeField), body,
      new SfMessageTrailer(checkSumField = Some(interimMsg.trailer.lastChecksumFld)))
  }

  val NewOrderSingle : SfMessage = newOrderSingle(1,"ClientOrderId")

}
