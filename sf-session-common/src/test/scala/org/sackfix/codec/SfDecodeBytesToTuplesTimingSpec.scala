package org.sackfix.codec

import akka.util.ByteString
import org.scalatest.FlatSpec

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during 2016.
  *
  * BEFORE
  * Duration = 43705846 nano secs
Duration = 1380103 nano secs
Duration = 1277949 nano secs
Duration = 1257435 nano secs
Duration = 1179898 nano secs
Duration = 1077333 nano secs
Duration = 1252513 nano secs
Duration = 1052308 nano secs
Duration = 1938051 nano secs
Duration = 1225847 nano secs
Duration = 980923 nano secs
Duration = 892308 nano secs
Duration = 1167179 nano secs
Duration = 916513 nano secs
Duration = 717538 nano secs
Duration = 756103 nano secs
Duration = 700718 nano secs
Duration = 850871 nano secs
Duration = 859488 nano secs
Duration = 622769 nano secs
Duration = 694154 nano secs
Duration = 585436 nano secs
Duration = 1341948 nano secs
Duration = 466872 nano secs
Duration = 477538 nano secs
Duration = 5192615 nano secs
Duration = 2238769 nano secs
Duration = 4471384 nano secs
Duration = 489847 nano secs
Duration = 377026 nano secs
Duration = 322872 nano secs
Duration = 318359 nano secs
Duration = 252308 nano secs
Duration = 299487 nano secs
Duration = 305231 nano secs
Duration = 278565 nano secs
Duration = 254769 nano secs
Duration = 2852513 nano secs
Duration = 434051 nano secs
Duration = 18804103 nano secs
Duration = 285128 nano secs
Duration = 1485538 nano secs
Duration = 1211077 nano secs
Duration = 226461 nano secs
Duration = 200205 nano secs
Duration = 197334 nano secs
Duration = 264615 nano secs
Duration = 195282 nano secs
Duration = 233026 nano secs
Duration = 7225026 nano secs
Duration = 360615 nano secs
Duration = 205538 nano secs
Duration = 324513 nano secs
Duration = 205128 nano secs
Duration = 224821 nano secs
Duration = 160000 nano secs
Duration = 152205 nano secs
Duration = 139487 nano secs
Duration = 138666 nano secs
Duration = 139077 nano secs
Duration = 196513 nano secs
Duration = 172718 nano secs
Duration = 295385 nano secs
Duration = 176820 nano secs
Duration = 169436 nano secs
Duration = 194872 nano secs
Duration = 10884103 nano secs
Duration = 872616 nano secs
Duration = 448000 nano secs
Duration = 189948 nano secs
Duration = 240000 nano secs
Duration = 200615 nano secs
Duration = 403283 nano secs
Duration = 166975 nano secs
Duration = 144410 nano secs
Duration = 210051 nano secs
Duration = 143179 nano secs
Duration = 332307 nano secs
Duration = 152615 nano secs
Duration = 141949 nano secs
Duration = 193231 nano secs
Duration = 132513 nano secs
Duration = 127589 nano secs
Duration = 155077 nano secs
Duration = 11105230 nano secs
Duration = 508308 nano secs
Duration = 168205 nano secs
Duration = 145231 nano secs
Duration = 233846 nano secs
Duration = 162051 nano secs
Duration = 141538 nano secs
Duration = 160410 nano secs
Duration = 157948 nano secs
Duration = 190359 nano secs
Duration = 125128 nano secs
Duration = 146051 nano secs
Duration = 125128 nano secs
Duration = 187898 nano secs
Duration = 126359 nano secs
Duration = 171898 nano secs
Duration = 210872 nano secs

  */
class SfDecodeBytesToTuplesTimingSpec extends FlatSpec {
  val SOH = 1.toChar

  behavior of "SfDecodeBytesToTuples"

  it should "show the time to decode" in {
    val fixMessage = s"8=FIX.4.4${SOH}9=121${SOH}35=D${SOH}49=FixTests${SOH}56=ExampleFixServer${SOH}34=3${SOH}52=20170306-07:51:46.834${SOH}11=Cl001${SOH}55=IBM${SOH}54=1${SOH}60=20170306-07:51:46${SOH}38=100${SOH}40=1${SOH}10=023${SOH}"
    val msg1 = ByteString(fixMessage)

    val decoder = new SfDecodeBytesToTuples()

    var max =1000000
    var startTime = System.nanoTime()
    for (i <- 0 to max+200)
    {
      if (i==200) startTime = System.nanoTime()
      decoder.decodeToArray(msg1, (msgFlds: Array[Tuple2[Int, String]], rejectDetails: Option[FixStrDecodeRejectDetails],
                                   ts:DecoderTimestamps) => {
      }, failureHandler)
    }
    val dur = System.nanoTime() - startTime
    println(s"Duration ${(dur/max)/1000} microseconds per call")
    println(s"$dur nano seconds for $max calls")
  }

  private def failureHandler(reason:String, ts:DecoderTimestamps): Unit = {
    println(reason)
  }

}
