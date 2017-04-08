package org.sackfix.session

import java.time.LocalTime

/**
  * Created by Jonathan during 2017.
  */
trait SfSessionConfig {
  def validateAllowedFromConfig(senderCompId:String, targetCompId:String,socketConnectHost:String): Boolean

  def heartbeat(senderCompId:String, targetCompId:String):Int
}

object SfSessionConfig {
  def DefaultHeartbeatSecs = 30
  def DefaultStartTime = LocalTime.of(0,0,0)
  def DefaultEndTime = LocalTime.of(23,59,59)
}
