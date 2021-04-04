package org.sackfix.session.heartbeat

import akka.actor.typed.ActorRef
import org.sackfix.session.SfSessionActor.SfSessionActorCommand

import java.time.LocalDateTime

/**
  * 1. If we have done nothing for heartbeat period, send a heartbeat.
  * 2. If the counterparty has done nothing for heartbeat + a reasonable time
  *   send a TestRequest.  Wait for heartbeat*2 and if no answer then logout
  *   with LogoutMessageType= "Test Request Timeout"
  */
object SessionTimeoutHandler {
  // Fudge factor added to heartbeat calculations
  val DefaultTransmissionDelayMs = 1000
}
trait SessionTimeoutHandler {

  def nothingSentFor(noHeartbeatsMissed:Int, sessActor: ActorRef[SfSessionActorCommand]) : Unit
  def nothingReceivedFor(noHeartbeatsMissedPlus20Percent:Int, sessActor: ActorRef[SfSessionActorCommand]) : Unit
}

case class MsgMonitor(val heartbeatMs:Long, val transmissionDelayMs:Long) {
  private[session] var lastTimeActivityMs = System.currentTimeMillis()
  private[session ]var lastHearbeatCount = 0

  def recordActivity(currentTimeMs:Long) = {
    lastTimeActivityMs = currentTimeMs
    lastHearbeatCount =0
  }

  def isNewElapsedCount(currentTimeMs:Long) : Option[Int] = {
    val newCount :Int = (((currentTimeMs - transmissionDelayMs)-lastTimeActivityMs)/heartbeatMs).toInt
    if (newCount>lastHearbeatCount) {
      lastHearbeatCount = newCount
      Some(newCount)
    } else None
  }
}


/**
  * @param heartbeatMs If we send nothing for this long, send a heartbeat.  If we receive nothing for
  *                    this long, send a testreq.
  * @param transmissionDelayMs Is added to the nothingReceived calc.
  */
class SfSessionTimeHandler(heartbeatMs:Long,
                           val sessionTimeoutHandler:SessionTimeoutHandler,
                           val sessActor: ActorRef[SfSessionActorCommand],
                           transmissionDelayMs:Long = 250) extends SfHeartbeatListener {
  // If receive nothing for heartbeat+20 then send a test message
  private val receivedMonitor = MsgMonitor((heartbeatMs.toDouble*1.2).toInt, transmissionDelayMs)
  // if send nothing for heartbeat, then send something
  private val sendMonitor = MsgMonitor(heartbeatMs, 0)

  def receivedAMessage: Unit = {
    receivedMonitor.recordActivity(System.currentTimeMillis())
  }
  def sentAMessage = {
    sendMonitor.recordActivity(System.currentTimeMillis())
  }

  override def heartBeatFired(): Unit = {
    val nowMs = System.currentTimeMillis()

    sendMonitor.isNewElapsedCount(nowMs) match {
      case Some(numHeartbeats) =>sessionTimeoutHandler.nothingSentFor(numHeartbeats, sessActor)
      case None =>
    }
    receivedMonitor.isNewElapsedCount(nowMs) match {
      case Some(numHeartbeats) =>sessionTimeoutHandler.nothingReceivedFor(numHeartbeats, sessActor)
      case None =>
    }
  }
}
