package org.sackfix.boostrap.initiator

import java.net.InetSocketAddress
import java.time.LocalTime
import java.time.format.DateTimeParseException

import akka.actor._
import org.sackfix.boostrap.ConfigUtil
import org.sackfix.common.config.SfConfigUtils
import com.typesafe.config.Config

/**
  * Cribbed from
  * http://doc.akka.io/docs/akka/current/scala/extending-akka.html#extending-akka-scala-settings
  * Created by Jonathan on 14/05/2016.
  */
class SfInitiatorSettingsImp(conf: Config) extends Extension {

  import scala.collection.JavaConversions._

  val config = conf.getConfig("session")

  val beginString = config.getString("BeginString")
  val senderCompID: String = config.getString("SenderCompID")
  val pathToFileStore = config.getString("PathToFileStore")

  val sessionConfigs: List[SfInitiatorTargetCompSettings] =
    config.getConfigList("sessions").map(new SfInitiatorTargetCompSettings(_)).toList


  def dumpConfig() :String= {
    val prefix="\n"
    s"${prefix}BeginString=[$beginString]"+
      s"${prefix}SenderCompID=[$senderCompID]"+
      s"${prefix}PathToFileStore=[$pathToFileStore]"+
      s"${prefix}sessions={" +
      {sessionConfigs.map{_.dumpConfig("\n  ")}.mkString(s"${prefix}}${prefix}{")}+s"${prefix}}"
  }
}

class SfInitiatorTargetCompSettings(config: Config) {
  import scala.collection.JavaConversions._

  val targetCompID: String = config.getString("TargetCompID")
  val reconnectIntervalSecs: Int = config.getInt("ReconnectIntervalSecs")
  val startTime: LocalTime = ConfigUtil.decodeTime("StartTime", config.getString("StartTime"))
  val endTime: LocalTime = ConfigUtil.decodeTime("EndTime", config.getString("EndTime"))
  val heartBtIntSecs: Int = config.getInt("HeartBtIntSecs")

  val resetMyNextSeqNumTo: Int = config.getInt("ResetMyNextSeqNumTo")
  val resetTheirNextSeqNumTo: Int = config.getInt("ResetTheirNextSeqNumTo")

  // uses javaconversions
  val socketConfigs = config.getConfigList("sockets").map(new SfInitiatorSocketSettings(_)).toList

  def dumpConfig(prefix:String):String = {
    s"${prefix}TargetCompID=[$targetCompID]"+
      s"${prefix}ReconnectIntervalSecs=[$reconnectIntervalSecs]"+
      s"${prefix}StartTime=[$startTime]"+
      s"${prefix}EndTime=[$endTime]"+
      s"${prefix}HeartBtIntSecs=[$heartBtIntSecs]"+
      s"${prefix}ResetMyNextSeqNumTo=[$resetMyNextSeqNumTo]"+
      s"${prefix}ResetTheirNextSeqNumTo=[$resetTheirNextSeqNumTo]"+
      s"${prefix}sockets={" +
      {socketConfigs.map{_.dumpConfig(prefix+"  ")}.mkString(s"${prefix}}${prefix}{")}+s"${prefix}}"
  }

}

object SfInitiatorSocketSettings {
  implicit def settingToInetSocketAddress(s:SfInitiatorSocketSettings) =
    new InetSocketAddress(s.socketAddress, s.socketPort)
}
case class SfInitiatorSocketSettings(config:Config) {
  val socketAddress = SfConfigUtils.toInetAddress(config, "SocketConnectHost")
  val socketPort = config.getInt("SocketConnectPort")

  def dumpConfig(prefix:String):String = {
    s"${prefix}SocketConnectHost=[$socketAddress]" +
    s"${prefix}SocketConnectPort=[$socketPort]"
  }
  override def toString: String = s"$socketAddress:$socketPort"
}

object SfInitiatorSettings extends ExtensionId[SfInitiatorSettingsImp] with ExtensionIdProvider {

  override def lookup = SfInitiatorSettings

  override def createExtension(system: ExtendedActorSystem) =
    new SfInitiatorSettingsImp(system.settings.config)

  /**
    * Java API: retrieve the Settings extension for the given system.
    */
  override def get(system: ActorSystem): SfInitiatorSettingsImp = super.get(system)
}
