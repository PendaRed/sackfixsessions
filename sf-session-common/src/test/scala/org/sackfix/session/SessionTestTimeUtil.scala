package org.sackfix.session

import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
  * Created by Jonathan during 2017.
  */
object SessionTestTimeUtil {
  // 8=Fix4.29=6935=A49=SendFGW56=TargFGW34=252=20170105-11:38:05.44298=0108=3010=242
  def stripTimeAndCheckSum(str: String): String = {
    val SOH = 1.toChar
    val utcTimeStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss[.SSS]")

    val pairs = str.split(SOH)
    pairs.map(keyVal => {
      val kv = keyVal.split("=")
      try {
        // checksum should be removed as time str changes it
        if (kv(0) == "10") "10="
        else {
          utcTimeStamp.parse(kv(1))
          // no expection, so its a date, so only return the v part
          kv(0) + "=TSTAMPREMOVED"
        }
      } catch {
        case ex: DateTimeParseException => keyVal
      }
    }).mkString("" + SOH) + "" + SOH
  }
}
