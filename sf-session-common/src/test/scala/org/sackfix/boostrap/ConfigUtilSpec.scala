package org.sackfix.boostrap

import java.time.format.DateTimeParseException
import org.scalatest.flatspec.AnyFlatSpec


/**
  * Created by Jonathan during 2017.
  */
class ConfigUtilSpec extends AnyFlatSpec {
  behavior of "ConfigUtil"

  it should "parse correctly" in {
    ConfigUtil.decodeTime("name", "13:45:30.123456789")
    ConfigUtil.decodeTime("name", "13:45:30")
    ConfigUtil.decodeTime("name", "00:00:00")
    ConfigUtil.decodeTime("name", "23:59:59")
  }

  it should "fail to parse" in {
    try {
      ConfigUtil.decodeTime("name", "13:45.30")
      fail("Expected an exception")
    } catch {
      case ex:RuntimeException => // fine
      case _:Throwable => fail("Should have given a runtime exception")
    }
  }
}
