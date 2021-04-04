package org.sackfix.session

import org.sackfix.common.message.SfMessageHeader
import org.sackfix.field._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2016.
  */
class SfSessionIdSpec  extends AnyFlatSpec {
  behavior of "SfSessionId"

  it should "create from a message header" in {
    val h = new SfMessageHeader(beginStringField = BeginStringField("Fix4.2"),
      msgTypeField = MsgTypeField("A"),
      senderCompIDField = SenderCompIDField("SendFGW"),
      targetCompIDField = TargetCompIDField("TargFGW"),
      msgSeqNumField = MsgSeqNumField(0),
      sendingTimeField = SendingTimeField("20170101-10:26:32"))
    val s = SfSessionId(h)
    assert(s.fileName=="fix4.2-targfgw-sendfgw")
    assert(s.id=="fix4.2:targfgw->sendfgw")

  }

  it should "Render down to a string correctly" in {
    val s = new SfSessionId("beginString",
      "senderCompId",
      Some("senderSubId"),
      Some("senderLocationId"),
      "targetCompId",
      Some("targetSubId"),
      Some("targetLocationId"))

    assert(s.fileName=="beginstring-sendercompid_sendersubid_senderlocationid-targetcompid_targetsubid_targetlocationid")
    assert(s.id=="beginstring:sendercompid/sendersubid/senderlocationid->targetcompid/targetsubid/targetlocationid")
  }
}
