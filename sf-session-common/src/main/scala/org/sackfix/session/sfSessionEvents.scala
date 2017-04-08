package org.sackfix.session

import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.MsgTypeField

/**
  * Created by Jonathan during November 2016.
  *  Using Early Initialisation and a trait rather than inheritance...just to see how it works
  */
sealed trait SfSessionEvent {
  val name:String

  def description = name
}
sealed trait SfSessionSocketEvent extends SfSessionEvent
case object SfSessionServerSocketOpenEvent extends {val name="Socket Waiting"} with SfSessionSocketEvent
case object SfSessionNetworkConnectionEstablishedEvent extends {val name="Socket Connected"} with  SfSessionSocketEvent
case object SfSessionSocketCloseEvent extends {val name="Socket Closed"} with  SfSessionSocketEvent
case object SfSessionServerSocketCloseEvent extends {val name="Acceptor Socket Closed"} with SfSessionSocketEvent

case class SfSessionFixMessageEvent(val msg:SfMessage) extends {val name="Fix Message Arrived"} with SfSessionEvent {
  override def description: String = {
    s"$name [${MsgTypeField.fixDescriptionByValue.getOrElse(msg.header.msgTypeField.value,"")}, msgSeqNum=${msg.header.msgSeqNumField.value}]"
  }
}

sealed trait SfSessionControlEvent extends SfSessionEvent
case class SfControlTimeoutFired(id:String,durationMs:Long) extends {val name="TimeoutFired"} with SfSessionControlEvent
case class SfControlNoSentHeartbeatTimeout(val noBeatsMissed:Int) extends {val name="Sent Nothing for Heartbeat Interval"} with SfSessionControlEvent
case class SfControlNoReceivedHeartbeatTimeout(val noBeatsMissedPlus20Percent:Int) extends {val name="Received Nothing for Heartbeat Interval+20%"} with SfSessionControlEvent
case class SfControlForceLogoutAndClose(val reason:String, val pausePriorToSocketCloseMs:Option[Long]=None) extends {val name=s"Force Logout [$reason]"} with SfSessionControlEvent


sealed trait SfAction
case class SfActionStartTimeout(id:String,durationMs:Long) extends SfAction
case class SfActionSendMessageToFix(msg: SfFixMessageBody) extends SfAction
case class SfActionResendMessages(beginSeqNo:Int, endSeqNo:Int) extends SfAction
case class SfActionCloseSocket() extends SfAction
case class SfActionCounterpartyHeartbeat(heartbeatSecs:Int) extends SfAction
case object SfActionBusinessSessionOpenForSending extends SfAction
case object SfActionBusinessSessionClosedForSending extends SfAction
case class SfActionBusinessMessage(msg: SfMessage) extends SfAction
