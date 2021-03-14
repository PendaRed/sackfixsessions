package org.sackfix.boostrap.initiator

import akka.actor.{ActorContext, ActorRef}
import org.sackfix.boostrap.BusinessCommsHandler
import org.sackfix.boostrap.initiator.SfInitiatorSocketActor.{InitiatorCloseNowMsgIn, InitiatorStartTimeMsgIn}
import org.sackfix.latency.LatencyActor
import org.sackfix.session._
import org.sackfix.session.heartbeat.SfHeartbeaterActor.{AddListenerMsgIn, StartBeatingMsgIn, StopBeatingMsgIn}
import org.sackfix.session.heartbeat.{SfHeartbeaterActor, SfSessionSchedulListener, SfSessionScheduler}
import org.slf4j.LoggerFactory

import java.time.LocalDateTime

/**
  * Created by Jonathan during 2017.
  *
  * This owns the Sack Fix acceptor.
  *
  * It creates place holders for all of the configured sessions, attaches them to listen for
  * heartbeats...and they sit their ticking away.
  *
  * It then registers itself to listen for the session schedule, and when told to start or stop
  * it turns on, or off the server socket which clients connect to.
  */

/**
  * Constructor
  *
  * @param guardianActor       Your guardian actor, which should be able to take a message of type
  *                      com.sackfix.bootstrap.SystemErrorNeedsDevOpsMsg and basically shutdown
  *                            eg the server port is already bound to.
  * @param messageStoreDetails The optional persisten store, and also a boolean indicating if
  *                            the initial sequence numbers should be read in, or left at 1
  * @param businessComms       A trait you have written which must be able to receive messages of type
  *                           org.sackfix.session.BusinessFixMessage(sfSessionActor:ActorRef, sessionId:SfSessionId, message:SfMessage)
  *                            It in turn can reply to the sfSessionActor by sending it a
  *                           org.sackfix.session.FixMsgOut(msgBody: SfFixMessageBody)
  */
case class SfInitiatorBooter(guardianActor: ActorRef, context: ActorContext,
                             messageStoreDetails: Option[SfMessageStore],
                             sessionOpenTodayStore: SessionOpenTodayStore,
                             businessComms: BusinessCommsHandler) {
  private val logger = LoggerFactory.getLogger(SfInitiatorBooter.getClass)

  logger.info(
    s"""
       |${"#" * 61}
       |### SackFix Initiator starting at ${LocalDateTime.now()} ###
       |${"#" * 61}
       |""".stripMargin)

  // Load the config into an extension object, so can get at values as fields.
  val settings: SfInitiatorSettingsImp = SfInitiatorSettings(context.system)
  logger.info("Config:" + settings.dumpConfig())
  val heartbeater: ActorRef = context.actorOf(SfHeartbeaterActor.props(1000), name="heartbeater")
  val latencyRecorderActorRef: Option[ActorRef] = Some(context.actorOf(LatencyActor.props(1000), name="SfLatencyRecorder"))

  // Maybe they have configured many client connections, I have no idea why they would.....
  val sockets: List[ActorRef] = settings.sessionConfigs.map { clientConfig: SfInitiatorTargetCompSettings =>
    val sessionId = new SfSessionId(settings.beginString,
      settings.senderCompID,
      clientConfig.targetCompID)

    if (clientConfig.socketConfigs.size < 1) {
      throw new RuntimeException(s"Bad config - no sockets configured for session, please fix and restart.  SessionId=${sessionId.id}")
    }


    val sessionActor = context.actorOf(SfSessionActor.props(SfInitiator, messageStoreDetails,
      sessionId,
      clientConfig.heartBtIntSecs,
      heartbeater,
      latencyRecorderActorRef,
      sessionOpenTodayStore,
      clientConfig.resetMyNextSeqNumTo,
      clientConfig.resetTheirNextSeqNumTo), name=s"${sessionId.actorNameId}:SfSessionActor")

    val sessionLookup = new SfSessionLookup
    sessionLookup.sessionCache.add(sessionId, sessionActor)

    val initiatorSocket = context.actorOf(SfInitiatorSocketActor.props(sessionLookup,
      sessionId, sessionActor,
      clientConfig.reconnectIntervalSecs, clientConfig.socketConfigs, businessComms,
      latencyRecorderActorRef), name=s"${sessionId.actorNameId}:SfInitiatorSocketActor")

    // Each session needs a schedule, which fires a start session into it.
    val scheduler = SfSessionScheduler(clientConfig.startTime, clientConfig.endTime, new SfSessionSchedulListener {
      override def wakeUp(): Unit = initiatorSocket ! InitiatorStartTimeMsgIn("Scheduler sends wake up")

      override def sleepNow(): Unit = initiatorSocket ! InitiatorCloseNowMsgIn("Scheduler configured end time says close down")
    })
    heartbeater ! AddListenerMsgIn(scheduler)

    initiatorSocket
  }

  heartbeater ! StartBeatingMsgIn

  def closeDown(): Unit = {
    heartbeater ! StopBeatingMsgIn
    // @TODO tell all client sessions to terminate using the logout sequence, give them a while
    // and when they are all down close, or after a time close.
    sockets.foreach(_ ! InitiatorCloseNowMsgIn("Actor system closing down"))
  }
}
