package org.sackfix.boostrap.acceptor

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.io.Tcp
import org.sackfix.boostrap.{BusinessCommsHandler, SystemErrorNeedsDevOpsMsg}
import org.sackfix.boostrap.acceptor.SfAccepterSocketActor.{AcceptorEndTimeMsgIn, AcceptorStartTimeMsgIn}
import org.sackfix.latency.LatencyActor
import org.sackfix.latency.LatencyActor.LatencyCommand
import org.sackfix.session._
import org.sackfix.session.heartbeat.SfHeartbeaterActor.{AddListenerMsgIn, HbCommand, StartBeatingMsgIn, StopBeatingMsgIn}
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
  * @param guardianActor      Your guardian actor, which should be able to take a message of type
  *                      com.sackfix.bootstrap.SystemErrorNeedsDevOpsMsg and basically shutdown
  *                           eg the server port is already bound to.
  * @param messageStoreDetails The optional persistent store
  * @param businessComms A trair you have written which must be able to receive messages of type
  *                           org.sackfix.session.BusinessFixMessage(sfSessionActor:ActorRef, sessionId:SfSessionId, message:SfMessage)
  *                           It in turn can reply to the sfSessionActor by sending it a
  *                           org.sackfix.session.FixMsgOut(msgBody: SfFixMessageBody)
  */
case class SfAcceptorBooter(guardianActor: ActorRef[SystemErrorNeedsDevOpsMsg],
                            context: ActorContext[SystemErrorNeedsDevOpsMsg],
                            messageStoreDetails:Option[SfMessageStore],
                            sessionOpenTodayStore: SessionOpenTodayStore,
                            businessComms: BusinessCommsHandler) {
  private val logger = LoggerFactory.getLogger(SfAcceptorBooter.getClass)

  logger.info(
    s"""
       |${"#"*60}
       |### SackFix Acceptor starting at ${LocalDateTime.now()} ###
       |${"#" * 60}
       |""".stripMargin)

  // Load the config into an extension object, so can get at values as fields.
  val settings: SfAcceptorSettingsImp = SfAcceptorSettings(context.system)
  logger.info("Config:"+settings.dumpConfig())

  val sessionLookup = new SfSessionLookup
  val heartbeater: ActorRef[HbCommand] = context.spawn(SfHeartbeaterActor(1000), name = "SfHeartbeaterActor")
  val latencyRecorderActorRef: Option[ActorRef[LatencyCommand]] = Some(context.spawn(LatencyActor(1000), name="SfLatencyActor"))

  // Loop through all of the configured potential end points that may connect and set them up
  // and add to the cache of clients who are allowed to connect
  settings.acceptorConfigs.foreach { clientConfig: SfAcceptorTargetCompSettings =>
    val sessionId = new SfSessionId(settings.beginString,
      settings.senderCompID,
      clientConfig.targetCompID)

    val sessionActor = context.spawn(SfSessionActor(SfAcceptor, messageStoreDetails,
      sessionId,
      clientConfig.heartBtIntSecs,
      heartbeater,
      latencyRecorderActorRef,
      sessionOpenTodayStore,
      clientConfig.resetMyNextSeqNumTo,
      clientConfig.resetTheirNextSeqNumTo), name=s"${sessionId.actorNameId}:SfSessionActor")


    sessionLookup.sessionCache.add(sessionId, sessionActor)
  }

  val serverSocketActor: ActorRef[Tcp.Event] = context.spawn(SfAccepterSocketActor(
    settings.socketAcceptAddress, settings.socketAcceptPort, sessionLookup, businessComms, guardianActor,
    latencyRecorderActorRef), name="SfServerSocketActor")


  val scheduler = SfSessionScheduler(settings.startTime, settings.endTime, new SfSessionSchedulListener {
    override def wakeUp(): Unit = serverSocketActor ! AcceptorStartTimeMsgIn

    override def sleepNow(): Unit = serverSocketActor ! AcceptorEndTimeMsgIn
  })

  heartbeater ! AddListenerMsgIn(scheduler)

  heartbeater ! StartBeatingMsgIn

  def closeDown(): Unit = {
    // @TODO tell all client sessions to terminate using the logout sequence, give them a while
    // and when they are all down close, or after a time close.
    serverSocketActor ! AcceptorEndTimeMsgIn

    heartbeater ! StopBeatingMsgIn
  }
}
