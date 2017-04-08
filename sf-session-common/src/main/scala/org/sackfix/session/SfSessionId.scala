package org.sackfix.session

import org.sackfix.common.message.SfMessageHeader

/**
  * Created by Jonathan in 2016.
  *
  * Stolen from QuickFix4j as an idea
  */
object SfSessionId {
  def apply(header: SfMessageHeader):SfSessionId = {
    SfSessionId(beginString=header.beginStringField.value,
      senderCompId=header.targetCompIDField.value,
      senderSubId=header.targetSubIDField.map(_.value),
      senderLocationId=header.targetLocationIDField.map(_.value),
      targetCompId=header.senderCompIDField.value,
      targetSubId=header.senderSubIDField.map(_.value),
      targetLocationId=header.senderLocationIDField.map(_.value))
  }
}
case class SfSessionId(val beginString:String,
                  val senderCompId:String,
                  val senderSubId:Option[String] = None,
                  val senderLocationId:Option[String] = None,
                  val targetCompId:String,
                  val targetSubId:Option[String] = None,
                  val targetLocationId:Option[String] = None) {
  def this(beginString:String, senderCompId:String, targetCompId:String) {
    this(beginString = beginString, senderCompId = senderCompId, senderSubId=None,
      targetCompId = targetCompId)
  }

  lazy val id:String =
  {s"$beginString:$senderCompId${fmt("/",senderSubId)}${fmt("/",senderLocationId)}->"+
    s"$targetCompId${fmt("/",targetSubId)}${fmt("/",targetLocationId)}"}.toLowerCase

  lazy val fileName =
  {s"$beginString-$senderCompId${fmt("_",senderSubId)}${fmt("_",senderLocationId)}-"+
      s"$targetCompId${fmt("_",targetSubId)}${fmt("_",targetLocationId)}"}.toLowerCase

  /**
    * can contain:  -_.*$+:@&=,!~';.
    */
  lazy val actorNameId =
    {s"$beginString:$senderCompId${fmt(".",senderSubId)}${fmt(".",senderLocationId)}-"+
      s"$targetCompId${fmt(".",targetSubId)}${fmt(".",targetLocationId)}"}.toLowerCase

  private def fmt(delim:String, s:Option[String]):String = {
    s match {
      case Some(v) => s"$delim$v"
      case None => ""
    }
  }
}
