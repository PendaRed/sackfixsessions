package org.sackfix.session

import akka.actor.typed.scaladsl.adapter.TypedActorRefOps
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.io.Tcp.{Event, Write}
import akka.util.ByteString
import akka.{actor => classic}
import org.sackfix.codec.DecodingFailedData
import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{SessionRejectReasonField, TextField}
import org.sackfix.fix44.RejectMessage
import org.sackfix.latency.LatencyActor.{LatencyCommand, LogCorrelationMsgIn, RecordMsgLatencyMsgIn}
import org.sackfix.session.SfSessionActor._
import org.sackfix.session.heartbeat.SfHeartbeaterActor.{AddListenerMsgIn, HbCommand, RemoveListenerMsgIn}
import org.sackfix.session.heartbeat.{SessionTimeoutHandler, _}
import org.slf4j.LoggerFactory

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration


/**
  * Created by Jonathan on 06/01/2017.
  *
  * This actor obviously has its own thread, it takes messages from the incoming fix socket,
  * from a heartbeater, and it also handles calls from SfSession asking that replies are sent
  * or business messages which pass validation are passed onto the OMS etc
  *
  * The OMS can also tell me to send message out to the socket.
  */


object SfSessionActor {
  /**
    *
    * @param sessionType            SfInitiator or SfAcceptor, impacts initial state for login sequence
    * @param messageStoreDetails    The optional persisten store, and also a boolean indicating if
    *                               the initial sequence numbers should be read in, or left at 1
    * @param sessionId              The session ID - it MUST match exactly the header values they other end sends
    * @param heartbeatIntervalSecs  Hb in secs
    * @param heartBeaterActor       A global scheduler
    * @param sessionOpenTodayStore  records today if there was a session yet or not, if not reset seq nums
    * @param resetMyNextSeqNumTo    If <1 ignored
    * @param resetTheirNextSeqNumTo If <1 ignored
    * @return
    */
  def apply(sessionType: SfSessionType,
            messageStoreDetails: Option[SfMessageStore],
            sessionId: SfSessionId,
            heartbeatIntervalSecs: Int,
            heartBeaterActor: ActorRef[HbCommand],
            latencyRecorder: Option[ActorRef[LatencyCommand]],
            sessionOpenTodayStore: SessionOpenTodayStore,
            resetMyNextSeqNumTo: Int = -1,
            resetTheirNextSeqNumTo: Int = -1): Behavior[SfSessionActorCommand] =
    Behaviors.setup(context =>
      Behaviors.withTimers { timers =>
        new SfSessionActor(context, timers,
          sessionType, messageStoreDetails,
          sessionId,
          heartbeatIntervalSecs,
          heartBeaterActor, latencyRecorder, sessionOpenTodayStore, resetMyNextSeqNumTo, resetTheirNextSeqNumTo)
      })

  sealed trait SfSessionActorCommand

  case class SfControlTimeoutFiredCmd(payload: SfControlTimeoutFired) extends SfSessionActorCommand

  case object AcceptorSocketWaitingMsgIn extends SfSessionActorCommand

  case object AcceptorSocketClosedMsgIn extends SfSessionActorCommand

  case class ConnectionEstablishedMsgIn(outEventRouter: SfSessOutEventRouter, fixMsg: Option[SfMessage],
                                        decodingFailedData: Option[DecodingFailedData]) extends SfSessionActorCommand

  case class TcpSaysSocketIsClosedMsgIn(tcpActor: classic.ActorRef) extends SfSessionActorCommand

  case class FixMsgIn(fixMsg: SfMessage) extends SfSessionActorCommand

  // The Business object will use BusinessFixMsgOut to send out a fix message via the session actor
  case class BusinessFixMsgOut(msgBody: SfFixMessageBody, correlationId: String) extends SfSessionActorCommand

  // Business layer can send this to close everything down
  case class BusinessSaysLogoutNow(reason: String) extends SfSessionActorCommand

  /**
    * Sometimes when decoding we can determine the session, but then a field is badly formatted etc.
    * ie we cannot even create the well formed FixMsg, so we need to reject the message
    */
  case class SendRejectMessageOut(refSeqNum: Int, reason: SessionRejectReasonField, explanation: TextField) extends SfSessionActorCommand

  /**
    * A case class used by the Business actor when it sends a message out.  All part of
    * AKKA io back pressure.  Without this you risk not knowing AKKA closed down and messages were lost.
    *
    * @param correlationId Allows you to have a unique value which you track as you send a message out
    *                      and wait for the ack back
    */
  case class SfSendFixMessageOutAck(correlationId: String) extends Event with SfSessionActorCommand

  case object FixActorSystemCloseDown extends SfSessionActorCommand

  // eg the entire Actor system is closing etc
  case class NothingSentFor(noHeartbeatsMissed: Int) extends SfSessionActorCommand

  case class NothingReceivedFor(noHeartbeatsMissed: Int) extends SfSessionActorCommand

}

/**
  * The state machone or system commands may need this subset of operations, which are broken out
  * to each testing.
  */
trait SfSessionActorOutActions {
  def closeSessionSocket()

  def closeActorSystem()

  def sendFixMsgOut(fixMsgStr: String, correlationId: String)

  def forwardBusinessMessageFromSocket(msg: SfMessage)

  def forwardBusinessSessionIsOpen()

  def forwardBusinessSessionIsClosed()

  def addControlTimeout(id: String, durationMs: Long)

  def changeHeartbeatInterval(newDurationSecs: Int)
}

class SfSessionActor(context: ActorContext[SfSessionActorCommand],
                     timers: TimerScheduler[SfSessionActorCommand],
                     val sessionType: SfSessionType,
                     messageStoreDetails: Option[SfMessageStore],
                     sessionId: SfSessionId,
                     heartbeatIntervalSecs: Int,
                     val heartBeater: ActorRef[HbCommand],
                     val latencyRecorder: Option[ActorRef[LatencyCommand]] = None,
                     val sessionOpenTodayStore: SessionOpenTodayStore,
                     val resetMyNextSeqNumTo: Int = -1,
                     val resetTheirNextSeqNumTo: Int = -1) extends AbstractBehavior[SfSessionActorCommand](context) with SfSessionActorOutActions {
  private val fixVerboseLog = LoggerFactory.getLogger("fixVerboseMessages")

  private var outEventRouter: Option[SfSessOutEventRouter] = None
  private var sessionOpenedOnceSinceStart = false

  private[session] val session = SfSessionImpl(sessionType, messageStoreDetails, this, sessionId,
    heartbeatIntervalSecs, latencyRecorder)
  context.log.info("[{}] Starting SfSessionActor ", session.idStr)

  private var heartbeatHandler: Option[SfSessionTimeHandler] = None

  override def onMessage(msg: SfSessionActorCommand): Behavior[SfSessionActorCommand] =
    msg match {
      case AcceptorSocketWaitingMsgIn =>
        if (sessionType == SfAcceptor) session.fireEventToStateMachine(SfSessionServerSocketOpenEvent)
        Behaviors.same
      case AcceptorSocketClosedMsgIn =>
        closeAcceptorSocket()
        Behaviors.same
      case ConnectionEstablishedMsgIn(newlyCreatedOutEventRouter: SfSessOutEventRouter, fixMsg, decodingFailedData) =>
        handleNewConnectionEstablished(newlyCreatedOutEventRouter, fixMsg, decodingFailedData)
        Behaviors.same
      // when the socket closes, then clear out the details so we cannot try and send data down it.
      case TcpSaysSocketIsClosedMsgIn(tcpActor: classic.ActorRef) => tcpSaysSocketClosed(tcpActor)
        Behaviors.same
      case FixMsgIn(fixMsg) =>
        actorReceivedIncomingFixMessage(fixMsg)
        Behaviors.same
      case BusinessFixMsgOut(msgBody, correlationId) =>
        sendOutBusinessMsg(msgBody, correlationId)
        Behaviors.same
      case BusinessSaysLogoutNow(reason) =>
        session.fireEventToStateMachine(SfControlForceLogoutAndClose(reason, Some(2000)))
        Behaviors.same
      case SendRejectMessageOut(refSeqNum: Int, reason: SessionRejectReasonField, explanation: TextField) =>
        context.log.warn("[{}] SeqNum= {}, Message from {} could not be handled, cause: {}",
          session.idStr, refSeqNum, this.outEventRouter.get.remoteHostDebugStr, explanation.value)
        session.sendRejectMessage(refSeqNum, incSeqNum = true, reason, explanation)
        Behaviors.same
      case FixActorSystemCloseDown =>
        closeActorSystem()
        Behaviors.stopped
      case NothingSentFor(noHeartbeatsMissed) =>
        if (session.isSessionOpen) session.fireEventToStateMachine(SfControlNoSentHeartbeatTimeout(noHeartbeatsMissed))
        Behaviors.same
      case NothingReceivedFor(noHeartbeatsMissed) =>
        if (session.isSessionOpen) session.fireEventToStateMachine(SfControlNoReceivedHeartbeatTimeout(noHeartbeatsMissed))
        Behaviors.same
      case ev: SfControlTimeoutFiredCmd =>
        session.fireEventToStateMachine(ev.payload)
        Behaviors.same
      case ack: SfSendFixMessageOutAck =>
        receivedAck(ack.correlationId)
        Behaviors.same
      case actorMsg@_ =>
        context.log.error(s"[{}] Match error: unexpected message received by Actor :{}",
          session.idStr, actorMsg.getClass.getName)
        Behaviors.same
    }

  private def receivedAck(correlationId: String): Unit =
    if (correlationId.nonEmpty) outEventRouter.foreach(_.informBusinessMessageAcked(correlationId))

  private def resetSequenceNumbersFromConfig(): Unit = {
    if (sessionOpenTodayStore.isThisFirstSessionToday(sessionId)) {
      context.log.info("[{}] First session today, resetting session sequence number to 1 prior to checking if overrides in application.conf", session.idStr)
      session.resetSeqNums

      sessionOpenTodayStore.recordSessionConnected(sessionId)
    }

    if (!sessionOpenedOnceSinceStart) {
      if (resetTheirNextSeqNumTo > 0) {
        context.log.info("[{}] Configured to set their next sequence number to [{}] on session open",
          session.idStr, resetTheirNextSeqNumTo)
        session.setTheirSeq(resetTheirNextSeqNumTo)
      }
      if (resetMyNextSeqNumTo > 0) {
        context.log.info("[{}] Configured to set my next sequence number to [{}] on session open",
          session.idStr, resetMyNextSeqNumTo)
        session.setMySeq(resetMyNextSeqNumTo)
      }
      sessionOpenedOnceSinceStart = true
    }
  }

  def handleNewConnectionEstablished(newlyCreatedOutRouter: SfSessOutEventRouter, fixMsg: Option[SfMessage],
                                     decodingFailedData: Option[DecodingFailedData]): Unit = {
    if (this.outEventRouter.isDefined) {
      // Have to disconnect the socket without any comms.
      context.log.warn("[{}] New connection from {} disconnected, already have an active connection from {}",
        session.idStr, newlyCreatedOutRouter.remoteHostDebugStr, this.outEventRouter.get.remoteHostDebugStr)
      newlyCreatedOutRouter.closeThisFixSessionsSocket()
      // discard the message!  Note it wont even be logged in the verbose logs
    } else {
      fixVerboseLog.info(s"### Socket opening from ${newlyCreatedOutRouter.remoteHostDebugStr} at ${LocalDateTime.now()} ###")

      this.outEventRouter = Some(newlyCreatedOutRouter)
      changeHeartbeatInterval(heartbeatIntervalSecs)
      session.openStore(true)
      resetSequenceNumbersFromConfig()
      context.log.info(s"[${session.idStr}] Session expects theirNextSeqNum=${session.getExpectedTheirSeqNum} and myNextSeqNum=${session.nextMySeqNum}")
      session.fireEventToStateMachine(SfSessionNetworkConnectionEstablishedEvent)

      // Socket acceptor gives either a message or failed data, but initiator gives neither
      decodingFailedData match {
        case Some(failData) =>
          context.log.warn("[{}] New connection from {} send first message with a problem, so rejecting, failure was {}",
            session.idStr, newlyCreatedOutRouter.remoteHostDebugStr, failData.description.value)
          session.sendRejectMessage(failData.referenceSeqNum, incSeqNum = true, failData.rejectReason, failData.description)
          // and the spec says you follow this reject with logout
          session.fireEventToStateMachine(SfControlForceLogoutAndClose(failData.description.value))
        case None =>
      }
      fixMsg match {
        case Some(msg) =>
          actorReceivedIncomingFixMessage(msg)
        case None =>
      }
    }
  }

  private def actorReceivedIncomingFixMessage(fixMsg: SfMessage): Unit = {
    if (context.log.isDebugEnabled) context.log.debug("[{}] In  = {},{}", session.idStr, fixMsg.header.msgSeqNumField, fixMsg.header.msgTypeField)

    heartbeatHandler.foreach(_.receivedAMessage)
    session.handleMessage(fixMsg)
    if (latencyRecorder.isDefined) {
      val seqNum = fixMsg.header.msgSeqNumField.value
      val latencyMsg = RecordMsgLatencyMsgIn(seqNum,
        "30.Finished", System.nanoTime())
      latencyRecorder.foreach(_ ! LogCorrelationMsgIn(Some(latencyMsg), seqNum.toString, removeDate = true))
    }
  }

  /**
    * The TCP layer is gone, blown away.
    */
  def tcpSaysSocketClosed(tcpActor: classic.ActorRef): Unit = {
    outEventRouter match {
      case Some(sessRouter) if sessRouter.confirmCorrectTcpActor(tcpActor) =>
        forwardBusinessSessionIsClosed()
        sessRouter.closeThisFixSessionsSocket()
        destroyHeartbeatHandler()
        this.outEventRouter = None
        context.log.debug("[{}] PeerClosedSocket so sending in socketClose to state machine ", session.idStr)
        session.fireEventToStateMachine(SfSessionSocketCloseEvent)
        context.log.info(s"[${session.idStr}] Session expects theirNextSeqNum=${session.getExpectedTheirSeqNum} and myNextSeqNum=${session.nextMySeqNum}")
      case _ =>
        context.log.debug("[{}] Ignoring a socket close as it was not from the correct client socket", session.idStr)
    }
  }

  /**
    * This is not obvious.  Someone, eg the server socket comes to the end of the active time for the session,
    * or perhaps the end of the logout sequence.  Anyway, the idea is that we close the sockets, and let
    * the unbind event finally disconnect us.
    */
  def closeAcceptorSocket(): Unit = {
    destroyHeartbeatHandler()
    forwardBusinessSessionIsClosed()
    session.close
    session.fireEventToStateMachine(SfSessionServerSocketCloseEvent)
    // tidy up any sockets which are open still
    outEventRouter.foreach(_.closeThisFixSessionsSocket())
  }

  /**
    * This comes from the protocol state machines, say the first message is not a login.  We close down the socket
    * please, because the other end is broken/toxic etc.
    */
  override def closeSessionSocket(): Unit = {
    session.close
    destroyHeartbeatHandler()
    outEventRouter.foreach(_.closeThisFixSessionsSocket())
  }

  override def closeActorSystem(): Unit = {
    context.log.info("[{}] Closing SfSessionActor ", session.idStr)
    destroyHeartbeatHandler()
    session.close
    // tidy up any sockets which are open still
    outEventRouter.foreach(_.closeThisFixSessionsSocket())
  }

  private def sendOutBusinessMsg(msgBody: SfFixMessageBody, correlationId: String): Unit = {
    session.sendAMessage(msgBody, correlationId)
  }

  /**
    * Imagine we get a fix message in, we pass it to the SfSessionImpl for processing, and it now has to
    * reply - maybe with resend request, logout etc, it calls us synchronously.  ie no need to ! us a message.
    *
    * @param fixMsgStr The full on fix message with correct sequence numbers and so on.
    */
  override def sendFixMsgOut(fixMsgStr: String, correlationId: String): Unit = {
    heartbeatHandler.foreach(_.sentAMessage)
    outEventRouter.foreach(rtr => {
      // The implicit sender should now pick up the self, and send me back the ack
      rtr.logOutgoingFixMsg(fixMsgStr)
      val ackEvent = SfSendFixMessageOutAck(correlationId)
      rtr.tcpActor.tell(Write(ByteString(fixMsgStr), ackEvent), context.self.toClassic) // no typed equivalent for tcp and io.
    })
  }

  /**
    * We received a fix message, validated it, confirmed it was not a session message and so now
    * need to forward it to the business OMS
    *
    * @param msg The decoded business message
    */
  override def forwardBusinessMessageFromSocket(msg: SfMessage): Unit = {
    // @TODO xxxjpg if session is not active normal session then reject it for now
    outEventRouter.foreach(router => msg.body match {
      case rej: RejectMessage =>
        router.informBusinessRejectArrived(msg)
      case _ =>
        router.informBusinessMessageArrived(msg)
    })
  }

  override def forwardBusinessSessionIsOpen(): Unit = {
    // Tell the business layer that the session is open for business
    outEventRouter.foreach(_.informBusinessLayerSessionIsOpen())
  }

  override def forwardBusinessSessionIsClosed(): Unit = {
    outEventRouter.foreach(_.informBusinessLayerSessionIsClosed())
  }

  /**
    * Add a one off event which will fire back into me an SfControlTimeoutFired event
    */
  override def addControlTimeout(id: String, durationMs: Long): Unit =
    timers.startSingleTimer(
      SfControlTimeoutFiredCmd(SfControlTimeoutFired(id, durationMs)),
      Duration.create(durationMs, TimeUnit.MILLISECONDS))

  override def changeHeartbeatInterval(newDurationSecs: Int): Unit = {
    heartbeatHandler match {
      case Some(handler) =>
        context.log.info(s"[${sessionId.id}] Removing previous heartbeat monitor")
        heartBeater ! RemoveListenerMsgIn(handler)
      case None =>
    }
    heartbeatHandler = Some(createHeartbeatHandler(newDurationSecs))
  }

  private[session] def createHeartbeatHandler(heartbeatIntervalSecs: Int): SfSessionTimeHandler = {
    createHeartbeatHandlerMs(heartbeatIntervalSecs * 1000)
  }

  private[session] def createHeartbeatHandlerMs(heartbeatIntervalMs: Long): SfSessionTimeHandler = {
    context.log.info(s"[${sessionId.id}] Starting heartbeat monitor with HeartBeatInterval = $heartbeatIntervalMs ms")
    val ret = new SfSessionTimeHandler(heartbeatIntervalMs,
      new SessionTimeoutHandler() {
        private val logger = LoggerFactory.getLogger(this.getClass)

        def nothingSentFor(noHeartbeatsMissed: Int, sessActor: ActorRef[SfSessionActorCommand]): Unit = {
          logger.info(s"[${sessionId.id}] nothing sent for HeartBeatInterval should send a Heartbeat")
          sessActor ! NothingSentFor(noHeartbeatsMissed)
        }

        def nothingReceivedFor(noHeartbeatsMissed: Int, sessActor: ActorRef[SfSessionActorCommand]): Unit = {
          logger.info(s"[${sessionId.id}] nothing received for HeartBeatInterval+20% should send a TestReq")
          sessActor ! NothingReceivedFor(noHeartbeatsMissed)
        }
      }, context.self, SessionTimeoutHandler.DefaultTransmissionDelayMs)
    heartBeater ! AddListenerMsgIn(ret)
    ret
  }

  private[session] def destroyHeartbeatHandler(): Unit = {
    heartbeatHandler.foreach(handler => heartBeater ! RemoveListenerMsgIn(handler))
    heartbeatHandler = None
  }
}
