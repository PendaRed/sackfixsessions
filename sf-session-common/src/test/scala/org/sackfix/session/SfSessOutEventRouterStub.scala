package org.sackfix.session

import akka.actor.typed.ActorRef
import akka.{actor => classic}
import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.session.SfSessionActor.SfSessionActorCommand

import scala.collection.mutable.ArrayBuffer

class SfSessOutEventRouterStub(override val sfSessionActor: ActorRef[SfSessionActorCommand], override val tcpActor: classic.ActorRef) extends SfSessOutEventRouter {
  val fixMessages = ArrayBuffer.empty[String]
  val businessMessages = ArrayBuffer.empty[SfFixMessageBody]
  var socketCloseCalled = false
  val remoteHostDebugStr = "Stub"
  var informOpenCalled = false
  var informCloseCalled = false
  var rejectMessage : Option[SfMessage] = None

  override def confirmCorrectTcpActor(checkTcpActor: classic.ActorRef):Boolean = true

  override def logOutgoingFixMsg(fixMsgStr: String) = {
    fixMessages += fixMsgStr
  }

  override def informBusinessLayerSessionIsOpen = {
    informOpenCalled = true
  }
  override def informBusinessLayerSessionIsClosed = {
    informCloseCalled = true
  }
  override def informBusinessMessageArrived(msg:SfMessage) = {
    businessMessages += msg.body
  }
  override def informBusinessMessageAcked(correlationId:String) = {}

  override def closeThisFixSessionsSocket = {
    socketCloseCalled = true
  }

  override def informBusinessRejectArrived(fixMsg: SfMessage): Unit = {
    rejectMessage = Some(fixMsg)
  }
}

class SfSessionActorOutActionsStub extends SfSessionActorOutActions {
  val fixMessages = ArrayBuffer.empty[String]
  val businessMessages = ArrayBuffer.empty[SfFixMessageBody]
  var closeSessionCalled = false
  var closeCalled = false

  override def closeSessionSocket: Unit = closeSessionCalled = true
  override def closeActorSystem: Unit = closeCalled = true

  override def sendFixMsgOut(fixMsgStr: String, correlationId:String): Unit = fixMessages += fixMsgStr

  override def forwardBusinessMessageFromSocket(msg: SfMessage): Unit = businessMessages += msg.body

  override def forwardBusinessSessionIsOpen = {}

  override def forwardBusinessSessionIsClosed = {}

  override def addControlTimeout(id:String, durationMs:Long) : Unit = {}

  override def changeHeartbeatInterval(newDurationSecs:Int): Unit = {}
}
