package org.sackfix.codec

import org.sackfix.field._
import org.scalatest.FlatSpec

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during November 2016.
  */
class SfDecodeTuplesToMsgTimingSpec extends FlatSpec {
  val SOH = 1.toChar

  behavior of "SfDecodeTuplesToMsg"

  it should "decode a string" in {
    val fixMessage = Array[Tuple2[Int, String]]( (8,"FIX.4.2"),(9,"178"),(35,"8"),(49,"PHLX"),
      (56,"PERS"),(34,"3444"),(52,"20071123-05:30:00.000"),(37,"orderId"),(11,"ATOMNOCCC9990900"),(20,"3"),(150,"E"),(39,"E"),(55,"MSFT"),
      (167,"CS"),(54,"1"),(38,"15"),(40,"2"),(44,"15"),(17,"execcId"),(58,"PHLX EQUITY TESTING"),(59,"0"),(47,"C"),(32,"0"),
      (31,"0"),(151,"15"),(14,"0"),(6,"0"),(10,"128"))

    var max =100000
    var startTime = System.nanoTime()
    for (i <- 0 to max+200) {
      if (i==200) startTime = System.nanoTime()
      SfDecodeTuplesToMsg.convertAMessage(fixMessage, None, latencyRecorder=None) match {
        case (Some(msg), None) => {
        }
        case _ => fail("Expected to get a message decoded")
      }
    }
    val dur = System.nanoTime() - startTime
    println(s"Duration ${(dur/max)/1000} microseconds per call")
    println(s"$dur nano seconds for $max calls")

  }
}
