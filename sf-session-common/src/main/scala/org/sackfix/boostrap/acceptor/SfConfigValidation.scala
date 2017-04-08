package org.sackfix.boostrap.acceptor

import java.time.LocalTime

import org.sackfix.session.SfSessionConfig
import org.slf4j.LoggerFactory

/**
  * Created by Jonathan during 2017.
  */
class SfConfigValidation(val mySenderCompId:String,
                         val configs: List[SfAcceptorTargetCompSettings]) extends SfSessionConfig {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def validateAllowedFromConfig(senderCompId:String, targetCompId:String,socketConnectHost:String): Boolean = {
    if (mySenderCompId.equals(targetCompId)) {
      val clientConfig = configs.filter(_.targetCompID.equals(senderCompId))
      if (clientConfig.nonEmpty) {
        val clientConfigHost = configs.filter(_.socketConnectHost.equals(socketConnectHost))
        if (clientConfigHost.nonEmpty) {
          true
        } else {
          logger.warn(s"Rejected connection from [$socketConnectHost] as no config for SenderConnectHost. senderCompId=[${senderCompId}]")
          false
        }
      } else {
        logger.warn(s"Rejected connection from [$socketConnectHost] as no config for SenderCompId=[${senderCompId}]")
        false
      }
    } else {
      logger.warn(s"Rejected connection from [$socketConnectHost] as bad TargetCompId=[${targetCompId}], should be [$senderCompId]")
      false
    }
  }

  private def findConfig(senderCompId:String, targetCompId:String):Option[SfAcceptorTargetCompSettings] = {
    if (mySenderCompId.equals(targetCompId)) {
      val clientConfig = configs.filter(_.targetCompID.equals(senderCompId))
      if (clientConfig.nonEmpty) {
        Some(clientConfig.head)
      } else None
    } else None
  }

  def heartbeat(senderCompId:String, targetCompId:String):Int = {
    findConfig(senderCompId, targetCompId) match {
      case Some(clientConfig) => clientConfig.heartBtIntSecs
      case None =>
        val hb = SfSessionConfig.DefaultHeartbeatSecs
        logger.info(s"No heartbeat configured for senderCompId ${senderCompId} targetCompId ${targetCompId}, so deafulting to ${hb} secs")
        hb
    }
  }
}
