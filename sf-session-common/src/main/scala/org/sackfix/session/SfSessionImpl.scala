package org.sackfix.session

import java.time.LocalDateTime

import akka.actor.ActorRef
import org.sackfix.codec.{DecodingFailedData, SfDecodeTuplesToMsg}
import org.sackfix.common.message.{SfFixUtcTime, SfMessage, SfMessageHeader}
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{NewSeqNoField, _}
import org.sackfix.fix44.{RejectMessage, SequenceResetMessage}
import org.sackfix.latency.LatencyActor.{RecordMsgLatenciesMsgIn, RecordMsgLatencyMsgIn}
import org.sackfix.session.fixstate._
import org.slf4j.LoggerFactory

import scala.collection.immutable.HashSet

/**
  * Created by Jonathan in 2016.
  *
  * If you want an easy life, just use the companion object and create the session and a file based message
  * store for the sequence numbers, with a simple session Id.   If you want a more complicated life then
  * copy the apply function to your own code and set it up yourself.
  *
  * You should set the PersistentStore asap, and, from your implementation, as you read initial values
  * call back into the session to set the values.
  *
  * @param sessionMessageTypes A set of the types which are considered to be admin session level messages
  *                            and so do not need to be persisted and stored for replay.
  */
class SfSessionImpl(sessionType: SfSessionType,
                    messageStoreDetails: Option[SfMessageStore],
                    sessionActor: SfSessionActorOutActions, sessionId: SfSessionId,
                    heartbeatIntervalSecs: Int,
                    val latencyRecorder: Option[ActorRef] = None,
                    override val sessionMessageTypes: HashSet[String] = SfSession.SessionMessageTypes
                   ) extends SfSession(sessionType, sessionId, heartbeatIntervalSecs) {

  private val log = LoggerFactory.getLogger(SfSessionImpl.getClass)
  private val fixVerboseLog = LoggerFactory.getLogger("fixVerboseMessages")

  private[session] var lastCloseTime: Option[LocalDateTime] = None

  private[session] var sessionState: SfSessState = DisconnectedNoConnectionToday

  var persistentStore: Option[SfMessageStore] = None

  messageStoreDetails match {
    case Some(messageStore: SfMessageStore) =>
      persistentStore = Some(messageStore)
    case None =>
  }

  // Spec says we should use the initiator heartbeat interval for the session rather than our own
  // But it also says we should agree it before hand....
  private[session] var counterpartyHeartbeatIntervalSecs = heartbeatIntervalSecs

  /** If the first session of the day then reset to 1.
    */
  def resetSeqNums = {
    if (nextMySeqNum!=1 && nextTheirSeqNum!=1) {
      log.debug(s"${idStr} resetSeqNums called, setting seq numbers to 1 (the old vals were myNextSeqNum=$nextMySeqNum theirNextSeqNum=$nextTheirSeqNum)")
      nextMySeqNum = 1
      nextTheirSeqNum = 1
      persistentStore.foreach({ store =>
        store.storeMySequenceNumber(sessionId, nextMySeqNum)
        store.storeTheirSequenceNumber(sessionId, nextTheirSeqNum)
        store.archiveTodaysReplay(sessionId)
      })
    }
  }

  override def setTheirSeq(resetTo: Int): Int = {
    val origTheirSeqNum = nextTheirSeqNum
    nextTheirSeqNum = resetTo
    persistentStore.foreach({ store =>
      store.storeTheirSequenceNumber(sessionId, nextTheirSeqNum)

      // OK, this is a mistake on my part when I coded it prior to reading the test spec.
      // Basically its impossible for them to reset on ongoing session to a prior sequence number
      // but I will leave it in place as its already coded....
      if (nextTheirSeqNum<origTheirSeqNum) {
        store.archiveTodaysReplay(sessionId)
      }
    })
    nextTheirSeqNum
  }

  override def setMySeq(resetTo: Int): Int = {
    val origMySeqNum = nextMySeqNum
    nextMySeqNum = resetTo
    persistentStore.foreach({ store =>
      store.storeMySequenceNumber(sessionId, nextMySeqNum)
    })
    nextMySeqNum
  }

  /**
    * @return Returns the last sequence number, but internally does a +1.
    */
  def incrementMySeq: Int = {
    val ret = nextMySeqNum
    nextMySeqNum = nextMySeqNum + 1
    persistentStore.foreach(_.storeMySequenceNumber(sessionId, nextMySeqNum))
    ret
  }

  /**
    * @return Returns the last sequence number, but internally does a +1.
    */
  def incrementTheirSeq: Int = {
    val ret = nextTheirSeqNum
    nextTheirSeqNum = nextTheirSeqNum + 1
    persistentStore.foreach(_.storeTheirSequenceNumber(sessionId, nextTheirSeqNum))
    ret
  }


  private[session] def createMessage(msgType: String, outgoingMsgBody: SfFixMessageBody): SfMessage = {
    val seqNum = incrementMySeq
    createMessage(seqNum, msgType, None, None, outgoingMsgBody)
  }

  private[session] def createMessage(seqNum: Int, msgType: String,
                                     outgoingMsgBody: SfFixMessageBody): SfMessage = {
    createMessage(seqNum, msgType, None, None, outgoingMsgBody)
  }

  private[session] def createMessage(seqNum: Int, msgType: String,
                                     possDupFlagField: Option[PossDupFlagField],
                                     origSendingTimeField: Option[OrigSendingTimeField],
                                     outgoingMsgBody: SfFixMessageBody): SfMessage = {
    val header = SfMessageHeader(
      beginStringField = BeginStringField(sessionId.beginString),
      msgTypeField = new MsgTypeField(msgType),
      senderCompIDField = SenderCompIDField(sessionId.senderCompId),
      targetCompIDField = TargetCompIDField(sessionId.targetCompId),
      msgSeqNumField = MsgSeqNumField(seqNum),
      possDupFlagField = possDupFlagField,
      sendingTimeField = SendingTimeField(SfFixUtcTime.now),
      origSendingTimeField = origSendingTimeField)

    new SfMessage(header, outgoingMsgBody)
  }


  /**
    * Generates the full SfMessage which can then be sent over the wire
    * Also store the message in the message store - but NOT admin messages.
    * @return The generate SfMessage containing the correct sequence numbers and comp id, and also
    *         the fixString which represents the fix message
    */
  def sendAMessage(msgBody: SfFixMessageBody, correlationId:String="") : Unit = {
    val msg = createMessage(msgBody.msgType, msgBody)
    val msgFixStr = msg.fixStr
    if (!isMessageFixSessionMessage(msg.body.msgType)) {
      persistentStore.foreach(_.storeOutgoingMessage(sessionId, msg.header.msgSeqNumField.value, msgFixStr))
    }
    sessionActor.sendFixMsgOut(msgFixStr, correlationId)
    if (fixVerboseLog.isInfoEnabled) fixVerboseLog.info("OUT {}", msg.toString())
  }


  /**
    * Close the persistent store and record the time the session closed.  If it is opened tomorrow then
    * sequence numbers reset down to 1
    */
  def close = {
    persistentStore.foreach(_.close(sessionId))
    lastCloseTime = Some(LocalDateTime.now())
  }

  /**
    * When a client connects, and established the socket then we should read in the details from
    * file.  Note that now we have index files as well this could be quite slow - implementation
    * dependency within your store
    * @param readInitialSequenceNumbers
    */
  def openStore(readInitialSequenceNumbers:Boolean) = {
    persistentStore.foreach(messageStore =>
      messageStore.initialiseSession(sessionId, readInitialSequenceNumbers) match {
        case SfSequencePair(ourSeqNum: Int, theirSeqNum: Int) =>
          nextMySeqNum = ourSeqNum
          nextTheirSeqNum = theirSeqNum
      })
  }

  /**
    * A fix message has arrived.  Yippee (I guess)
    *
    * @param incomingMessage The incoming message
    */
  def handleMessage(incomingMessage: SfMessage): Unit = {
    if (fixVerboseLog.isInfoEnabled) fixVerboseLog.info("IN  {}", incomingMessage)

    lastTheirSeqNum = incomingMessage.header.msgSeqNumField.value

    if (compIdsCorrect(incomingMessage.header)) {
      fireEventToStateMachine(new SfSessionFixMessageEvent(incomingMessage))
    }
  }

  def compIdsCorrect(header: SfMessageHeader) = {
    val sessId = SfSessionId(header)
    if (sessId == sessionId) true
    else {
      val beginStrWrong = (sessId.beginString != sessionId.beginString)
      val msg = {
        if (beginStrWrong) s"Fix version [${sessId.beginString}] should be [${sessionId.beginString}]"
        else "Message contains invalid CompId fields"
      }
      if (sessionState.isSessionOpen) {
        // If the session is open then different response than if its during login.
        if (beginStrWrong) {
          log.warn(s"[$idStr] Message has wrong beginStr [${sessId.toString}],  expected [${sessionId}] logout")
          fireEventToStateMachine(SfControlForceLogoutAndClose(msg, Some(2000)))
        } else {
          // 1.	Send Reject (session-level) message referencing invalid SenderCompID or TargetCompID
          // (>= FIX 4.2: SessionRejectReason = "CompID problem")
          log.warn(s"[$idStr] Message has wrong header fields [${sessId.toString}],  expected [${sessionId}] inc their seq num, reject message and logout")
          sendRejectMessage(header.msgSeqNumField.value, false,
            SessionRejectReasonField(SessionRejectReasonField.CompidProblem),
            TextField(msg))
          fireEventToStateMachine(SfControlForceLogoutAndClose(msg, Some(2000)))
        }
      } else {
        // reject the message, as no config for this client, ie they maybe changed the compid after login.
        // An invalid login message should result in closing the session.
        log.warn(s"[$idStr] Message has wrong header fields [${sessId.toString}],  expected [${sessionId}] reject message")
        sendRejectMessage(header.msgSeqNumField.value, false,
          SessionRejectReasonField(SessionRejectReasonField.CompidProblem),
          TextField(msg))
      }
      false
    }
  }

  def fireEventToStateMachine(ev: SfSessionEvent) = {
    sessionState = sessionState.receiveEvent(this, ev, handleAction)
  }

  def isSessionOpen: Boolean = {
    sessionState.isSessionOpen
  }

  /**
    * Called back from the state machine when an action should be taken - either a reply message
    * or close the socket etc.
    *
    * @param action The action
    */
  def handleAction(action: SfAction) = action match {
    case SfActionStartTimeout(id, durationMs) =>
      sessionActor.addControlTimeout(id, durationMs)
    case SfActionCloseSocket() =>
      sessionActor.closeSessionSocket
    case SfActionSendMessageToFix(msgBody) =>
      sendAMessage(msgBody,"")
    case SfActionResendMessages(beginSeqNum: Int, endSeqNum: Int) =>
      replayMessages(beginSeqNum, endSeqNum)
    case SfActionCounterpartyHeartbeat(heartbeatSecs: Int) => setSessionHeartbeatToBeTheirs(heartbeatSecs)
    case SfActionBusinessMessage(msg) =>
      // Forward to OMS (Order Management System..or position keeping...or whatever)
      sessionActor.forwardBusinessMessageFromSocket(msg)
    case SfActionBusinessSessionOpenForSending =>
      sessionActor.forwardBusinessSessionIsOpen
    case SfActionBusinessSessionClosedForSending =>
      sessionActor.forwardBusinessSessionIsClosed
  }

  /**
    * Apparently the session should use the initiators heartbeat config in preference to the
    * acceptors heartbeat.
    * @param heartbeatSecs The new heartbeat diration in seconds
    */
  private def setSessionHeartbeatToBeTheirs(heartbeatSecs: Int) = {
    if (heartbeatSecs != counterpartyHeartbeatIntervalSecs && sessionType==SfAcceptor) {
      log.info("Initiator heartbeat is {}, changing session to use this value instead of {}",
        heartbeatSecs, heartbeatIntervalSecs)
      counterpartyHeartbeatIntervalSecs = heartbeatSecs
      sessionActor.changeHeartbeatInterval(counterpartyHeartbeatIntervalSecs)
    }
  }

  def replaceHeaderOnResend(fixStr: String): Option[SfMessage] = {
    SfDecodeTuplesToMsg.decodeFromStr(fixStr, (failData: DecodingFailedData) => {
      // reject handler
      log.warn(s"[$idStr] Trying to replace header fields on replay of message failed to decode it, which is impossible, msg was [$fixStr] - cause [${failData.description.value}]")
    }) match {
      case Some(msg) =>
        val replayMsg = createMessage(msg.header.msgSeqNumField.value, msg.header.msgTypeField.value,
          Some(PossDupFlagField("Y")),
          Some(OrigSendingTimeField(msg.header.sendingTimeField.value)), msg.body)
        Some(replayMsg)
      case None =>
        None
    }
  }

  /**
    * Try to retrieve all the fix messages from the store, or if there are gaps then send a gap fill instead.
    * Note that its generally stupid to resent admin messages....up to you if you stick them in your
    * message store or not.
    *
    * @TODO the fix spec suggests some folks may not want to resent certain messages such as new order singles
    *       after a certain time has elapsed....so should let the users configure a filter here - or a list of
    *       excluded msg types
    * @param beginSeqNum The first one to send back
    * @param endSeqNum   The last one to send back
    */
  def replayMessages(beginSeqNum: Int, endSeqNum: Int): Unit = {
    var lastGapStart: Int = -1
    for (seq <- beginSeqNum to endSeqNum) {
      val msg = getMessageFixStrFromStore(seq)
      msg match {
        case Some(msgFixStr) =>
          if (lastGapStart >= 0) {
            sendGapFill(lastGapStart, seq)
            lastGapStart = -1
          }
          replaceHeaderOnResend(msgFixStr) match {
            case None =>
              if (lastGapStart == -1) lastGapStart = seq
            case Some(msg) =>
              if (fixVerboseLog.isInfoEnabled) fixVerboseLog.info("OUT RESEND{}", msg.toString())
              if (log.isTraceEnabled) log.trace(s"[${sessionId.id}] Resending msgSeqNum={}", msg.header.msgSeqNumField.value)
              sessionActor.sendFixMsgOut(msg.fixStr, "")
          }
        case None =>
          if (lastGapStart == -1) lastGapStart = seq
      }
    }
    if (lastGapStart >= 0) {
      if (log.isTraceEnabled) log.trace(s"[${sessionId.id}] Resending GapFill start={}, end={}", lastGapStart, endSeqNum + 1)
      sendGapFill(lastGapStart, endSeqNum + 1)
    }
  }

  private[session] def sendGapFill(msgSeqNum: Int, nextSeqNum: Int): Unit = {
    val outgoingMsgBody = SequenceResetMessage(Some(GapFillFlagField("Y")),
      NewSeqNoField(nextSeqNum))
    val msg = createMessage(msgSeqNum, outgoingMsgBody.msgType, outgoingMsgBody)
    if (fixVerboseLog.isInfoEnabled) fixVerboseLog.info("OUT RESEND{}", msg.toString())
    sessionActor.sendFixMsgOut(msg.fixStr, "")
  }

  def getMessageFixStrFromStore(seqNo: Int): Option[String] = {
    persistentStore.map(_.readMessage(sessionId, seqNo)).flatten
  }

  def sendRejectMessage(refSeqNum: Int, incSeqNum:Boolean, reason: SessionRejectReasonField, explanation: TextField) = {
    if (sessionState.isSessionOpen && refSeqNum==nextTheirSeqNum && incSeqNum) {
      if (log.isInfoEnabled) log.info(s"[${sessionId.id}] Msg with seq num {} failed to decode due to {}, still increment msgNum", refSeqNum, explanation.value)
      incrementTheirSeq
    } else {
      if (log.isInfoEnabled) log.info(s"[${sessionId.id}] Msg with seq num {} failed to decode due to {}, not incrementing seq num, so next expected seq num= [${nextTheirSeqNum}]", refSeqNum, explanation.value)
    }
    val outgoingMsgBody = RejectMessage(RefSeqNumField(refSeqNum),
      sessionRejectReasonField = Some(reason),
      textField = Some(explanation)
    )
    sendAMessage(outgoingMsgBody,"")
  }

  /**
    * Deals with close from the previous day and the fact this process was not restarted
    * ie will reset the sequence numbers back to 1
    */
  def getExpectedTheirSeqNum: Int = {
    lastCloseTime match {
      case None => nextTheirSeqNum
      case Some(localLastClose) =>
        if (LocalDateTime.now().getDayOfYear != localLastClose.getDayOfYear) {
          // if its tomorrow then we reset to 1.
          resetSeqNums
          lastCloseTime = None
        }
        nextTheirSeqNum
    }
  }

}

/**
  * Feel free to add your own helpers below.
  */
object SfSessionImpl {
  def apply(sessionType: SfSessionType,
            messageStoreDetails: Option[SfMessageStore],
            sessionActor: SfSessionActorOutActions, sessionId: SfSessionId,
            heartbeatIntervalSecs: Int,
            latencyRecorder: Option[ActorRef] = None) = {
    new SfSessionImpl(sessionType, messageStoreDetails, sessionActor, sessionId, heartbeatIntervalSecs,
      latencyRecorder)
  }
}


