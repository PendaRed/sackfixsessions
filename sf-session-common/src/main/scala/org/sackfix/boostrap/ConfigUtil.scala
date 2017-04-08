package org.sackfix.boostrap

import java.time.LocalTime

import com.typesafe.config.ConfigException.BadValue

/**
  * Created by Jonathan during 2017.
  */
object ConfigUtil {
  /**
    * @param timeStr of the for HH:mmm:ss
    */
  def decodeTime(configName: String, timeStr: String) = try {
    LocalTime.parse(timeStr)
  } catch {
    case ex: Exception => throw new BadValue(configName, s"Failed to parse [$timeStr] for config item $configName using JDK 8 LocalTime.parse, expected form '10:15:30'", ex)
  }

}
