package org.sackfix.session.heartbeat

import java.time.LocalTime

import org.sackfix.session.{SessionOpenTodayStore, SfSessionId}
import org.slf4j.LoggerFactory

/**
  * Created by Jonathan during 2017.
  */
trait SfSessionSchedulListener {
  def wakeUp
  def sleepNow
}

case class SfSessionScheduler(val startTime:LocalTime, val endTime:LocalTime,
                              val listener:SfSessionSchedulListener)  extends SfHeartbeatListener {
  private val logger = LoggerFactory.getLogger(SfSessionScheduler.getClass)
  logger.info(s"Session times:  start:$startTime, ends $endTime")

  var sentWakeUp = false
  var sentSleepNow = false

  override def heartBeatFired(): Unit = {
    val now = LocalTime.now()

    if (now.isAfter(startTime) && now.isBefore(endTime)) {
      if (!sentWakeUp) {
         listener.wakeUp
        sentWakeUp = true
        sentSleepNow = false
      }
    } else {
      if (!sentSleepNow) {
        listener.sleepNow
        sentSleepNow = true
        sentWakeUp = false
      }
    }
  }
}
