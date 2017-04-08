package org.sackfix.boostrap.acceptor

import java.time.LocalTime

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.sackfix.boostrap.ConfigUtil
import org.sackfix.common.config.SfConfigUtils
import com.typesafe.config.Config

/**
  * Cribbed from
  * http://doc.akka.io/docs/akka/current/scala/extending-akka.html#extending-akka-scala-settings
  * Created by Jonathan on 14/05/2016.
  */
class SfAcceptorSettingsImp(conf: Config) extends Extension {

  import scala.collection.JavaConversions._

  val config = conf.getConfig("session")

  val beginString = config.getString("BeginString")
  val senderCompID: String = config.getString("SenderCompID")
  val socketAcceptAddress = SfConfigUtils.getOptionalInetAddress(config, "SocketAcceptAddress")
  val socketAcceptPort = config.getInt("SocketAcceptPort")

  val startTime: LocalTime = ConfigUtil.decodeTime("StartTime", config.getString("StartTime"))
  val endTime: LocalTime = ConfigUtil.decodeTime("EndTime", config.getString("EndTime"))

  val pathToFileStore = config.getString("PathToFileStore")

  val acceptorConfigs: List[SfAcceptorTargetCompSettings] = config.getConfigList("sessions").map(new SfAcceptorTargetCompSettings(_)).toList

  def dumpConfig() :String= {
    val prefix="\n"
    s"${prefix}BeginString=[$beginString]"+
    s"${prefix}SenderCompID=[$senderCompID]"+
    s"${prefix}SocketAcceptAddress=[${{socketAcceptAddress.map(_.toString)}.getOrElse("None")}]" +
    s"${prefix}SocketAcceptPort=[$socketAcceptPort]"+
    s"${prefix}StartTime=[$startTime]"+
    s"${prefix}EndTime=[$endTime]" +
    s"${prefix}PathToFileStore=[$pathToFileStore]"+
    s"${prefix}sessions={" + acceptorConfigs.map(_.dumpConfig(prefix+"  ")).mkString(s"${prefix}}${prefix}{")+s"${prefix}}"
  }
}

class SfAcceptorTargetCompSettings(config: Config) {
  val targetCompID: String = config.getString("TargetCompID")
  val reconnectIntervalSecs: Int = config.getInt("ReconnectIntervalSecs")
  val heartBtIntSecs: Int = config.getInt("HeartBtIntSecs")
  val socketConnectHost: String = config.getString("SocketConnectHost")
  val resetMyNextSeqNumTo: Int = config.getInt("ResetMyNextSeqNumTo")
  val resetTheirNextSeqNumTo: Int = config.getInt("ResetTheirNextSeqNumTo")

  def dumpConfig(prefix:String) :String= {
    s"${prefix}TargetCompId=[$targetCompID]"+
    s"${prefix}ReconnectIntervalSecs=[$reconnectIntervalSecs]"+
    s"${prefix}HeartBtIntSecs=[$heartBtIntSecs]"+
    s"${prefix}SocketConnectHost=[$socketConnectHost]"+
    s"${prefix}ResetMyNextSeqNumTo=[$resetMyNextSeqNumTo]"+
    s"${prefix}ResetTheirNextSeqNumTo=[$resetTheirNextSeqNumTo]"
  }
}

object SfAcceptorSettings extends ExtensionId[SfAcceptorSettingsImp] with ExtensionIdProvider {
  val SOCKET_ACCEPT_ADDRESS = "SocketAcceptAddress"

  override def lookup = SfAcceptorSettings

  override def createExtension(system: ExtendedActorSystem) =
    new SfAcceptorSettingsImp(system.settings.config)

  /**
    * Java API: retrieve the Settings extension for the given system.
    */
  override def get(system: ActorSystem): SfAcceptorSettingsImp = super.get(system)
}
