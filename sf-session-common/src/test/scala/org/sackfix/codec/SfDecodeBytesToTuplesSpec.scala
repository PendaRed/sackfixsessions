package org.sackfix.codec

import akka.util.ByteString
import org.sackfix.common.message.SfMessageHeader
import org.sackfix.field.{SessionRejectReasonField, TextField}
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during 2016.
  */
class SfDecodeBytesToTuplesSpec extends AnyFlatSpec {
  val SOH = 1.toChar

  behavior of "SfDecodeBytesToTuples"

  it should "decode a simple message in one buffer" in {
    val fixMessage = s"8=FIX.4.2${SOH}10=128$SOH"
    val msg1 = ByteString(fixMessage)

    val decoder = new SfDecodeBytesToTuples()

    decoder.decodeToArray(msg1, (msgFlds: Array[Tuple2[Int, String]], rejectDetails:Option[FixStrDecodeRejectDetails],
                                 ts:DecoderTimestamps) => {
      assert(2 == msgFlds.size)

      assert(8 == msgFlds(0)._1)
      assert(10 == msgFlds(1)._1)
      assert("FIX.4.2" == msgFlds(0)._2)
      assert("128" == msgFlds(1)._2)
    }, failureHandler)
  }

  it should "decode a message in multiple buffers" in {
    val fixMessage = List[String](s"8=FIX.4.2${SOH}9=178${SOH}35=8${SOH}49=P",
      s"HLX${SOH}56=PERS${SOH}52=20071123-05:30:00.000${SOH}11=ATOMNO",
      s"CCC9990900${SOH}20=3${SOH}150=E${SOH}39=E${SOH}55=MSFT${SOH}167=CS${SOH}54=1${SOH}38=15",
      s"${SOH}40=2${SOH}44=15${SOH}58=PHLX EQUITY TESTING${SOH}59=0${SOH}47=C${SOH}32=0${SOH}31=0${SOH}151=",
      s"15${SOH}14=0${SOH}6=0${SOH}10=128${SOH}")

    val decoder = new SfDecodeBytesToTuples(true)
    var howManyCallbacks = 0
    var decodedMsg : Option[Array[Tuple2[Int, String]]] = None

    fixMessage.foreach( msg => {
      val msg1 = ByteString(msg)
      decoder.decodeToArray(msg1, (msgFlds: Array[Tuple2[Int, String]], rejectDetails:Option[FixStrDecodeRejectDetails],ts:DecoderTimestamps) => {
        howManyCallbacks = howManyCallbacks+1
        decodedMsg = Some(msgFlds)
      }, (failureHandler))
    })

    assert(1 == howManyCallbacks)
    assert(decodedMsg.isDefined)
    // input and output should match, so, remove the () from around the tuples, and change , to = and seperate with SOH
    assert(fixMessage.mkString == decodedMsg.get.
      mkString(SOH.toString).
      filter(p => p!='(' && p!=')').map( ((b:Char)=>if (b==',') '=' else b)).mkString + SOH.toString)
  }

  it should "decode multiple messages in multiple buffers" in {
    val fixMessage = List[String](s"8=FIX.4.2${SOH}9=178${SOH}35=8${SOH}49=P",
      s"HLX${SOH}56=PERS${SOH}52=20071123-05:30:00.000${SOH}11=ATOMNO",
      s"CCC9990900${SOH}20=3${SOH}150=E${SOH}39=E${SOH}55=MSFT${SOH}167=CS${SOH}54=1${SOH}38=",
      s"15",
      s"${SOH}40=2${SOH}44=15${SOH}58=PHLX EQUITY TESTING${SOH}59=0${SOH}47=C${SOH}32=0${SOH}31=0${SOH}151=",
      s"15${SOH}14=0${SOH}6=0${SOH}10=128${SOH}")

    val decoder = new SfDecodeBytesToTuples()
    var howManyCallbacks = 0
    var decodedMsg : Option[Array[Tuple2[Int, String]]] = None

    fixMessage.foreach( msg => {
      val msg1 = ByteString(msg)
      decoder.decodeToArray(msg1, (msgFlds: Array[Tuple2[Int, String]], rejectDetails:Option[FixStrDecodeRejectDetails],ts:DecoderTimestamps) => {
        howManyCallbacks = howManyCallbacks+1
        decodedMsg = Some(msgFlds)
      }, failureHandler)
    })

    assert(1 == howManyCallbacks)
    assert(decodedMsg.isDefined)
    // input and output should match, so, remove the () from around the tuples, and change , to = and seperate with SOH
    assert(fixMessage.mkString == decodedMsg.get.
      mkString(SOH.toString).
      filter(p => p!='(' && p!=')').map( ((b:Char)=>if (b==',') '=' else b)).mkString + SOH.toString)
  }


  private def failureHandler(reason:String, ts:DecoderTimestamps): Unit = {
    println(reason)
  }

}
