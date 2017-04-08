package org.sackfix.codec

import org.sackfix.common.message.SfMessageHeader
import org.sackfix.field._
import org.sackfix.session.SfSessionId
import org.scalatest.FlatSpec

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during November 2016.
  */
class SfDecodeTuplesToMsgSpec extends FlatSpec {
  val SOH = 1.toChar

  behavior of "SfDecodeTuplesToMsg"

  it should "decode a string" in {
    val msg = s"""8=FIX.4.2${SOH}9=178${SOH}35=8${SOH}49=PHLX${SOH}56=PERS${SOH}34=3444${SOH}
              |52=20071123-05:30:00.000${SOH}37=orderId${SOH}11=ATOMNOCCC9990900${SOH}20=3${SOH}
              |150=E${SOH}39=E${SOH}55=MSFT${SOH}167=CS${SOH}54=1${SOH}38=15${SOH}40=2${SOH}
              |44=15${SOH}17=execcId${SOH}58=PHLX EQUITY TESTING${SOH}59=0${SOH}47=C${SOH}
              |32=0${SOH}31=0${SOH}151=15${SOH}14=0${SOH}6=0${SOH}10=128${SOH}
              |""".stripMargin.replace("\n","")
    SfDecodeTuplesToMsg.decodeFromStr(msg, (det:DecodingFailedData)=> {
      // reject handler
      fail("Did not expect failure:"+det.description.value)
    }) match {
      case Some(msg1) =>
        //        println(msg)
        assert(msg1.toString == "(8)BeginString=(FIX.4.2),(9)BodyLength=(222),(35)MsgType=(8)EXECUTION_REPORT,(49)SenderCompID=(PHLX),(56)TargetCompID=(PERS),(34)MsgSeqNum=(3444),(52)SendingTime=(2007-11-23T05:30),(37)OrderID=(orderId),(11)ClOrdID=(ATOMNOCCC9990900),(17)ExecID=(execcId),(150)ExecType=(E)PENDING_REPLACE,(39)OrdStatus=(E)PENDING_REPLACE,(55)Symbol=(MSFT),(167)SecurityType=(CS)COMMON_STOCK,(54)Side=(1)BUY,(38)OrderQty=(15.0),(40)OrdType=(2)LIMIT,(44)Price=(15.0),(59)TimeInForce=(0)DAY,(32)LastQty=(0.0),(31)LastPx=(0.0),(151)LeavesQty=(15.0),(14)CumQty=(0.0),(6)AvgPx=(0.0),(58)Text=(PHLX EQUITY TESTING),20=3,47=C,(10)CheckSum=(072)")
      case None => fail("Expected a message")
    }
  }

  it should "extract the session id" in {
    val msg = Array[Tuple2[Int, String]]( (BeginStringField.TagId,"Fix.4.4"),
      (SenderCompIDField.TagId,"SenderCompID"),
      (SenderSubIDField.TagId,"SenderSubID"),
      (SenderLocationIDField.TagId,"SenderLocationID"),
      (TargetCompIDField.TagId,"TargetCompID"),
      (TargetSubIDField.TagId,"TargetSubID"),
      (TargetLocationIDField.TagId,"TargetLocationID"))
    SfDecodeTuplesToMsg.extractSessionId(msg) match {
      case Some(sessId) => assert(sessId.id == "fix.4.4:targetcompid/targetsubid/targetlocationid->sendercompid/sendersubid/senderlocationid")
      case None => fail("Failed to extract properly")
    }
  }
  it should "extract the session id with missing optionals" in {
    val msg = Array[Tuple2[Int, String]]( (BeginStringField.TagId,"Fix.4.4"),
      (SenderCompIDField.TagId,"SenderCompID"),
      (TargetCompIDField.TagId,"TargetCompID"))
    SfDecodeTuplesToMsg.extractSessionId(msg) match {
      case Some(sessId) => assert(sessId.id == "fix.4.4:targetcompid->sendercompid")
      case None => fail("Failed to extract properly")
    }
  }
  it should "extract the seq num" in {
    val msg = Array[Tuple2[Int, String]]( (1,"1"),(34,"34"), (203,"203"))
    assert(SfDecodeTuplesToMsg.getSeqNum(msg)==34)
  }
  it should "default the seq num" in {
    val msg = Array[Tuple2[Int, String]]( (1,"1"),(38,"38"), (203,"203"))
    assert(SfDecodeTuplesToMsg.getSeqNum(msg)==1)
  }

  it should "report a string parse failure if missing key or value" in {
    val msg = s"""8=FIX.4.2${SOH}9=178${SOH}35=8${SOH}49=${SOH}56=PERS${SOH}34=3444${SOH}
                 |52=20071123-05:30:00.000${SOH}37=orderId${SOH}11=ATOMNOCCC9990900${SOH}20=3${SOH}
                 |150=E${SOH}39=E${SOH}55=MSFT${SOH}167=CS${SOH}54=1${SOH}38=15${SOH}40=2${SOH}
                 |44=15${SOH}17=execcId${SOH}58=PHLX EQUITY TESTING${SOH}59=0${SOH}47=C${SOH}
                 |32=0${SOH}31=0${SOH}151=15${SOH}14=0${SOH}6=0${SOH}10=128${SOH}
                 |""".stripMargin.replace("\n","")
    SfDecodeTuplesToMsg.decodeFromStr(msg, (det:DecodingFailedData)=> {
      // reject handler
      // pass
    }) match {
      case Some(msg) =>fail("Expected a failure")
      case None =>  // pass
    }
  }

  it should "report a string parse failure if tag not a number" in {
    val msg = s"""eight=FIX.4.2${SOH}9=178${SOH}35=8${SOH}49=PHLX${SOH}56=PERS${SOH}34=3444${SOH}
                 |52=20071123-05:30:00.000${SOH}37=orderId${SOH}11=ATOMNOCCC9990900${SOH}20=3${SOH}
                 |150=E${SOH}39=E${SOH}55=MSFT${SOH}167=CS${SOH}54=1${SOH}38=15${SOH}40=2${SOH}
                 |44=15${SOH}17=execcId${SOH}58=PHLX EQUITY TESTING${SOH}59=0${SOH}47=C${SOH}
                 |32=0${SOH}31=0${SOH}151=15${SOH}14=0${SOH}6=0${SOH}10=128${SOH}
                 |""".stripMargin.replace("\n","")
    SfDecodeTuplesToMsg.decodeFromStr(msg, (det:DecodingFailedData)=> {
      // reject handler
      // pass
    }) match {
      case Some(msg) =>fail("Expected a failure")
      case None =>  // pass
    }
  }

  private def failureHandler(reason:String): Unit = {
    println(reason)
  }

  it should "decode a tuple array" in {
    // 34 is MsgSeqNum and is mandatory
    val fixMessage = Array[Tuple2[Int, String]]( (8,"FIX.4.2"),(9,"178"),(35,"8"),(49,"PHLX"),
      (56,"PERS"),(34,"3444"),(52,"20071123-05:30:00.000"),(37,"orderId"),(11,"ATOMNOCCC9990900"),(20,"3"),(150,"E"),(39,"E"),(55,"MSFT"),
      (167,"CS"),(54,"1"),(38,"15"),(40,"2"),(44,"15"),(17,"execcId"),(58,"PHLX EQUITY TESTING"),(59,"0"),(47,"C"),(32,"0"),
      (31,"0"),(151,"15"),(14,"0"),(6,"0"),(10,"128"))

    var howManyCallbacks = 0

    SfDecodeTuplesToMsg.convertAMessage(fixMessage, None) match {
      case (Some(msg), None) => {
        //        println(msg)
        assert(msg.toString == "(8)BeginString=(FIX.4.2),(9)BodyLength=(222),(35)MsgType=(8)EXECUTION_REPORT,(49)SenderCompID=(PHLX),(56)TargetCompID=(PERS),(34)MsgSeqNum=(3444),(52)SendingTime=(2007-11-23T05:30),(37)OrderID=(orderId),(11)ClOrdID=(ATOMNOCCC9990900),(17)ExecID=(execcId),(150)ExecType=(E)PENDING_REPLACE,(39)OrdStatus=(E)PENDING_REPLACE,(55)Symbol=(MSFT),(167)SecurityType=(CS)COMMON_STOCK,(54)Side=(1)BUY,(38)OrderQty=(15.0),(40)OrdType=(2)LIMIT,(44)Price=(15.0),(59)TimeInForce=(0)DAY,(32)LastQty=(0.0),(31)LastPx=(0.0),(151)LeavesQty=(15.0),(14)CumQty=(0.0),(6)AvgPx=(0.0),(58)Text=(PHLX EQUITY TESTING),20=3,47=C,(10)CheckSum=(072)")
      }
      case _=> fail("Expected to get a message decoded")
    }
  }

}
