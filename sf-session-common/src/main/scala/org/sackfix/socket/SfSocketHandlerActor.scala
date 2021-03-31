package org.sackfix.socket

import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.time.LocalDateTime
import akka.io.Tcp
import org.sackfix.boostrap.BusinessCommsHandler
import org.sackfix.codec._
import org.sackfix.common.message.{SfFixUtcTime, SfMessage, SfMessageHeader}
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field._
import org.sackfix.latency.LatencyActor.{LatencyCommand, RecordMsgLatenciesMsgIn, RecordMsgLatencyMsgIn}
import org.sackfix.session.SfSessionActor.{ConnectionEstablishedMsgIn, FixMsgIn, SendRejectMessageOut, SfSessionActorCommand, TcpSaysSocketIsClosedMsgIn}
import org.sackfix.session._
import org.sackfix.socket.SfSocketHandlerActor.{CloseSocketMsgIn, InitiatorSocketOpenMsgIn, SfSocketHandlerCommand}
import org.slf4j.LoggerFactory
import akka.{actor => classic}
// adds support for actors to a classic actor system and context
import akka.actor.typed.scaladsl.adapter._

object SfSocketHandlerActor {
  /**
    * @param connection See Using TCP - Akka Documentation, you can Write to this actor.
    */
  def apply(sessionType: SfSessionType, connection: classic.ActorRef, sessionLookup: SfSessionLookup,
            remoteHostName: String, businessComms: BusinessCommsHandler,
            latencyRecorder: Option[ActorRef[LatencyCommand]]): Behavior[Tcp.Event] =
    Behaviors.setup(context => new SfSocketHandlerActor(context, sessionType, connection,
      sessionLookup, remoteHostName, businessComms, latencyRecorder))

  trait SfSocketHandlerCommand extends Tcp.Event
  case class InitiatorSocketOpenMsgIn(sessionId:SfSessionId, sessionActor:ActorRef[SfSessionActorCommand]) extends SfSocketHandlerCommand
  case object CloseSocketMsgIn extends SfSocketHandlerCommand

}

/** Every Socket sends TCP IO events to me.  Bytes being received are the most obvious, which are decoded
  * and some initial validation is performed.  Providing it decodes to a fix message the comp id's etc are
  * used to locate a configured session, if found then the message is handed off to the session actor for
  * handling.
  *
  * When a client connects to the server port this class is registered to handle incoming
  * comms.
  *
  * It calls the decoder to convert from bytes into a Strongly typed Fix Message.
  *
  * If the message is badly formed, missing enough header fields and so on this this actor replies
  *
  * If the session is established, and we know the SfSessionActor to use then the comms is forwarded to it
  * and it does it all.
  *
  * http://doc.akka.io/docs/akka/current/scala/io-tcp.html
  *
  * @param sessionType     Either SfAcceptor or SfInitiator, determines the starting states for new sessions
  * @param connection      This is where we can send data back down to the client
  * @param sessionLookup   Holds details of all the sessions in a cache, and can validate sendCompId etc
  * @param remoteHostName  Just for debug strings
  * @param businessComms   An actor for receiving decoded, validated fix messages.  It is used when a session
  *                        is established with the correct sender and target comp id's
  * @param latencyRecorder An optional microsecond latency recorder.
  */
class SfSocketHandlerActor(context: ActorContext[Tcp.Event],
                           val sessionType: SfSessionType, val connection: classic.ActorRef,
                           val sessionLookup: SfSessionLookup, val remoteHostName: String,
                           val businessComms: BusinessCommsHandler,
                           val latencyRecorder: Option[ActorRef[LatencyCommand]]) extends AbstractBehavior[Tcp.Event](context) {

  import Tcp._

  private val fixlog = LoggerFactory.getLogger("fixmessages")
  fixlog.info(s"### Socket handler from ${remoteHostName} opening at ${LocalDateTime.now()} ###")

  private val fixDecoder = new SfDecodeBytesToTuples(true)
  private val fixDecodeByteString = fixDecoder.decode(receivedAMessageCallback, handleGarbledMessage) _

  // Only if the login works and we know the session actor can we use these two.
  private var outEventRouter: Option[SfSessOutEventRouter] = None

  // sign death pact: this Actor terminates when the connection breaks
  context.watch( connection)


  override def onMessage(msg: Tcp.Event): Behavior[Tcp.Event] =
    msg match {
    case InitiatorSocketOpenMsgIn(sessionId:SfSessionId, sessionActor:ActorRef[SfSessionActorCommand]) =>
      // Tell the session that the connection is established.
      tellSessionAboutTheConnection(sessionActor, sessionId, None, None)
      Behaviors.same
    case Received(data) =>
      fixDecodeByteString(data)
      Behaviors.same
    case PeerClosed =>
      context.log.info(s"Detected PeerClosed to [$remoteHostName], closing myself down")
      // If we had an established session then tell the session actor that its gone
      tellSessionActorCommsIsDown
      Behaviors.stopped
    case Unbound =>
      context.log.info(s"Socket closed to [$remoteHostName], closing myself down")
      tellSessionActorCommsIsDown
      Behaviors.stopped
    case CloseSocketMsgIn =>
      closeSocket
      Behaviors.same
    case _: ConnectionClosed =>
      context.log.info(s"Connection closed to [$remoteHostName], closing myself down")
      tellSessionActorCommsIsDown
      Behaviors.stopped
    case actorMsg@AnyRef =>
      context.log.error(s"Match error: unexpected message received by Actor :${actorMsg.getClass.getName}")
      Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[Tcp.Event]] = {
    case Terminated(connection) =>
      context.log.info(s"Detected death of actor connection to [$remoteHostName], closing myself down")
      tellSessionActorCommsIsDown
      Behaviors.stopped
  }

  private def tellSessionAboutTheConnection(sfsessionActor: ActorRef[SfSessionActorCommand], sessId: SfSessionId,
                                            fixMsg: Option[SfMessage],
                                            decodingFailedData: Option[DecodingFailedData]): Unit = {
    if (outEventRouter.isEmpty) {
      outEventRouter = Some(SfSessOutEventRouterImpl(businessComms, sfsessionActor, sessId, connection, remoteHostName))
      sfsessionActor ! ConnectionEstablishedMsgIn(outEventRouter.get, fixMsg, decodingFailedData)
    }
  }

  private def tellSessionActorCommsIsDown = {
    outEventRouter.foreach(_.sfSessionActor ! TcpSaysSocketIsClosedMsgIn(connection))
    outEventRouter = None
  }

  /**
    * Called by the decoder when the tag id is not a number, or some other bad formatting which means we cannot
    * even work out what session this message is for.
    *
    * @param reason The debug message.
    */
  def handleGarbledMessage(reason: String, decoderTimestamp: DecoderTimestamps) = {
    recordLatencies(decoderTimestamp)

    // Section 2m & 2t
    // 1.	Consider garbled and ignore message  (do not increment inbound MsgSeqNum) and continue accepting messages
    // 2.	Generate a "warning" condition in test output.
    context.log.warn(reason)
  }

  def closeSocket = {
    context.log.info("Sending close to socket actor")
    connection ! Close
  }

  private def recordLatencies(ts: DecoderTimestamps): Unit = {
    latencyRecorder.foreach(rec => {
      val seqNum = ts.msgSeqNum.toInt
      rec ! RecordMsgLatenciesMsgIn(List(RecordMsgLatencyMsgIn(seqNum, "00.FirstByte", ts.firstByteTstampNanos, true),
        RecordMsgLatencyMsgIn(seqNum, "01.LastByte", ts.lastByteTstampNanos)))
    })
    if (fixlog.isInfoEnabled()) fixlog.info("IN  {}", ts.rawMsg.slice(0, ts.msgEndPos).utf8String)
  }

  /**
    * called when it gets the final tuple of a message
    *
    * @param rejectDetails Some of the validation done when decoding the string to tuples should result
    *                      in a garbled message - ie just discard it, while others such as a null value in a
    *                      tag value pair should result in a reject - which means we need the session
    */
  def receivedAMessageCallback(msgTuples: Array[Tuple2[Int, String]], rejectDetails: Option[FixStrDecodeRejectDetails],
                               decoderTimestamps: DecoderTimestamps) = {
    recordLatencies(decoderTimestamps)
    val preDecodeNanos = System.nanoTime()
    SfDecodeTuplesToMsg.decode(msgTuples, rejectDetails,
      handleFailureToDecodeMsg(sessionType) _,
      latencyRecorder) match {
      case Some(msg: SfMessage) =>
        handleDecodedMsg(sessionType, msg, preDecodeNanos)
      case None => // do nothing
    }
  }

  /**
    * This validates the session, calls into the session object to maintain session sequence numbers
    * and so on.  The SessionActor knows all about the state of the session, sequence numbers and so on.
    */
  private def handleDecodedMsg(sessionType: SfSessionType, msg: SfMessage,
                               preDecodeNanos: Long): Unit = {
    val seqNum = msg.header.msgSeqNumField.value
    if (latencyRecorder.isDefined) {
      latencyRecorder.foreach(_ ! RecordMsgLatencyMsgIn(seqNum,
        "10.ToTuples", preDecodeNanos))
      latencyRecorder.foreach(_ ! RecordMsgLatencyMsgIn(seqNum,
        "20.ToMsg", System.nanoTime()))
    }
    /* If this is the first message of a connection then the outEventRouter is empty.  So
    * tell the session about it, and send it the login at the same time - there is a rule which
    * says a login to a session which is already up should close the socket for the new
    * connection.
     */
    if (outEventRouter.isEmpty) {
      val sessId = SfSessionId(msg.header)
      sessionLookup.findSession(sessId) match {
        case Some(session) =>
          tellSessionAboutTheConnection(session, sessId, Some(msg), None)
        case None =>
          // spec says, if you have not logged on yet, then drop the connection right away
          context.log.warn(s"Failed to locate session in session cache using [${sessId.toString}], closing socket")
          closeSocket
      }
    } else {
      latencyRecorder.foreach(_ ! RecordMsgLatencyMsgIn(seqNum,
        "21.! FixMsgIn", System.nanoTime()))
      outEventRouter.foreach(_.sfSessionActor ! FixMsgIn(msg))
    }
  }

  private def handleFailureToDecodeMsg(sessionType: SfSessionType)(decodingFailedData: DecodingFailedData): Unit = {
    if (outEventRouter.isEmpty) {
      decodingFailedData.sessionId match {
        case Some(sessId) =>
          sessionLookup.findSession(sessId) match {
            case Some(session) =>
              tellSessionAboutTheConnection(session, sessId, None, Some(decodingFailedData))
            case None =>
              // spec says, if you have not logged on yet, then drop the connection right away
              context.log.warn(s"Replacing other failure, with socket close - they sent invalid session details (${sessId.toString()}) original error was: ${decodingFailedData.description.value}")
              closeSocket
          }
        case None =>
          context.log.warn(s"Failed to find mandatory session id fields, so closing socket,${decodingFailedData.description.value}")
          closeSocket
      }
    } else {
      outEventRouter.foreach(_.sfSessionActor ! SendRejectMessageOut(decodingFailedData.referenceSeqNum,
        decodingFailedData.rejectReason, decodingFailedData.description))
    }
  }

  /**
    * Could not find the session, so reject it and make up some fields for the header
    */
  def createReplyMessageForNoSession(incomingMsg: SfMessage, msgType: String, outgoingMsgBody: SfFixMessageBody): SfMessage = {
    createReplyMessageForNoSession(SfSessionId(incomingMsg.header), msgType, outgoingMsgBody)
  }

  def createReplyMessageForNoSession(sessId: SfSessionId, msgType: String, outgoingMsgBody: SfFixMessageBody): SfMessage = {
    val header = SfMessageHeader(
      beginStringField = BeginStringField(sessId.beginString),
      msgTypeField = MsgTypeField(msgType),
      senderCompIDField = SenderCompIDField(sessId.targetCompId),
      targetCompIDField = TargetCompIDField(sessId.senderCompId),
      msgSeqNumField = MsgSeqNumField(0),
      sendingTimeField = SendingTimeField(SfFixUtcTime.now))

    new SfMessage(header, outgoingMsgBody)
  }

}


