package org.sackfix.boostrap.initiator

import akka.actor._
import com.typesafe.config.Config
import org.sackfix.boostrap.ConfigUtil
import org.sackfix.common.config.SfConfigUtils

import java.net.{InetAddress, InetSocketAddress}
import java.time.LocalTime

/**
  * Cribbed from
  * http://doc.akka.io/docs/akka/current/scala/extending-akka.html#extending-akka-scala-settings
  * Created by Jonathan on 14/05/2016.
  */
class SfInitiatorSettingsImp(conf: Config) extends Extension {

  import scala.jdk.CollectionConverters._

  val config: Config = conf.getConfig("session")

  val beginString: String = config.getString("BeginString")
  val senderCompID: String = config.getString("SenderCompID")
  val pathToFileStore: String = config.getString("PathToFileStore")

  val sessionConfigs: List[SfInitiatorTargetCompSettings] =
    config.getConfigList("sessions").asScala.map(new SfInitiatorTargetCompSettings(_)).toList


  def dumpConfig() :String= {
    val prefix="\n"
    s"${prefix}BeginString=[$beginString]"+
      s"${prefix}SenderCompID=[$senderCompID]"+
      s"${prefix}PathToFileStore=[$pathToFileStore]"+
      s"${prefix}sessions={" +
      {sessionConfigs.map{_.dumpConfig("\n  ")}.mkString(s"$prefix}$prefix{")}+s"$prefix}"
  }
}

class SfInitiatorTargetCompSettings(config: Config) {
  import scala.jdk.CollectionConverters._

  val targetCompID: String = config.getString("TargetCompID")
  val reconnectIntervalSecs: Int = config.getInt("ReconnectIntervalSecs")
  val startTime: LocalTime = ConfigUtil.decodeTime("StartTime", config.getString("StartTime"))
  val endTime: LocalTime = ConfigUtil.decodeTime("EndTime", config.getString("EndTime"))
  val heartBtIntSecs: Int = config.getInt("HeartBtIntSecs")

  val resetMyNextSeqNumTo: Int = config.getInt("ResetMyNextSeqNumTo")
  val resetTheirNextSeqNumTo: Int = config.getInt("ResetTheirNextSeqNumTo")

  // uses javaconversions
  val socketConfigs: List[SfInitiatorSocketSettings] = config.getConfigList("sockets").asScala.map(new SfInitiatorSocketSettings(_)).toList

  def dumpConfig(prefix:String):String = {
    s"${prefix}TargetCompID=[$targetCompID]"+
      s"${prefix}ReconnectIntervalSecs=[$reconnectIntervalSecs]"+
      s"${prefix}StartTime=[$startTime]"+
      s"${prefix}EndTime=[$endTime]"+
      s"${prefix}HeartBtIntSecs=[$heartBtIntSecs]"+
      s"${prefix}ResetMyNextSeqNumTo=[$resetMyNextSeqNumTo]"+
      s"${prefix}ResetTheirNextSeqNumTo=[$resetTheirNextSeqNumTo]"+
      s"${prefix}sockets={" +
      {socketConfigs.map{_.dumpConfig(prefix+"  ")}.mkString(s"$prefix}$prefix{")}+s"$prefix}"
  }

}

object SfInitiatorSocketSettings {
  implicit def settingToInetSocketAddress(s:SfInitiatorSocketSettings) =
    new InetSocketAddress(s.socketAddress, s.socketPort)
}
case class SfInitiatorSocketSettings(config:Config) {
  val socketAddress: InetAddress = SfConfigUtils.toInetAddress(config, "SocketConnectHost")
  val socketPort: Int = config.getInt("SocketConnectPort")

  def dumpConfig(prefix:String):String = {
    s"${prefix}SocketConnectHost=[$socketAddress]" +
    s"${prefix}SocketConnectPort=[$socketPort]"
  }
  override def toString: String = s"$socketAddress:$socketPort"
}

object SfInitiatorSettings extends ExtensionId[SfInitiatorSettingsImp] with ExtensionIdProvider {

  override def lookup: SfInitiatorSettings.type = SfInitiatorSettings

  override def createExtension(system: ExtendedActorSystem) =
    new SfInitiatorSettingsImp(system.settings.config)

  /**
    * Java API: retrieve the Settings extension for the given system.
    */
  override def get(system: ActorSystem): SfInitiatorSettingsImp = super.get(system)
}
