package org.sackfix.codec

import akka.actor.ActorRef
import org.sackfix.common.message._
import org.sackfix.common.validated.fields.SfFixMessageDecoder
import org.sackfix.field._
import org.sackfix.fix44.SfMessageFactory
import org.sackfix.latency.LatencyActor.RecordMsgLatencyMsgIn
import org.sackfix.session.SfSessionId

/**
  * Created by Jonathan during 2016.
  */
object SfDecodeTuplesToMsg {

  def decodeFromStr(fixStr: String, rejectMessageCallback: DecodingFailedData => Unit,
                    latencyRecorder:Option[ActorRef]=None): Option[SfMessage] = {
    var seqNo = 0
    try {
      val fixTuples: Array[(Int, String)] = fixStr.split(SfDecodeBytesToTuples.SOH_CHAR).map((kv: String) => {
        val s = kv.split(SfDecodeBytesToTuples.EQUALS_CHAR)
        if (s.length != 2) {
          throw SfBadFixStreamException(s"Badly encoded message - tag=value was [$kv]")
        }
        // silly and messy, but for erros the reject is supposed to send the seq no
        val tagId = s(0).toInt
        if (tagId == MsgSeqNumField.TagId) seqNo = s(1).toInt
        (tagId, s(1))
      })
      decode(fixTuples, None, rejectMessageCallback, latencyRecorder)
    } catch {
      case ex: NumberFormatException =>
        rejectMessageCallback(DecodingFailedData(None, seqNo, SessionRejectReasonField(SessionRejectReasonField.IncorrectDataFormatForValue), TextField(s"Bad tagId, expected a numeric: [${ex.getMessage}]")))
        None
      case SfBadFixStreamException(msg: String) =>
        rejectMessageCallback(DecodingFailedData(None, seqNo, SessionRejectReasonField(SessionRejectReasonField.DecryptionProblem), TextField(msg)))
        None
    }
  }

  /**
    * Decode Fix tuples into a strongly typed message, or failing that, try to get the header at least
    * and return the header (so you can determine session details) together with a reason it could
    * not be decoded and a text explanation
    *
    * @return the decoded message which you should now process, or None (after calling the failure callback)
    */
  def decode(fixTuples: Array[Tuple2[Int, String]],rejectDetails:Option[FixStrDecodeRejectDetails],
             rejectMessageCallback: DecodingFailedData => Unit,
             latencyRecorder:Option[ActorRef]=None): Option[SfMessage] = {
    convertAMessage(fixTuples, rejectDetails, latencyRecorder) match {
      case (msg: Some[SfMessage], None) =>
        msg
      case (None, Some(decodingFailedData)) =>
        rejectMessageCallback(decodingFailedData)
        None
      case (None, None) => // Message was garbled, ignore it, do not increment incoming seq num
        None
      case _ => // As above, don't care
        None
    }
  }

  /**
    * find is a bit slow, so only do this if there is no header
    * NOTE I SWAP THE SENDER AND TARGET ID's so the lookup works in the cache.
    */
  def extractSessionId(msgFlds: Array[Tuple2[Int, String]]): Option[SfSessionId] = {
    val beginStr: Option[String] = msgFlds.find(_._1 == BeginStringField.TagId).map(_._2)
    val senderCompId = msgFlds.find(_._1 == TargetCompIDField.TagId).map(_._2)
    val senderSubId = msgFlds.find(_._1 == TargetSubIDField.TagId).map(_._2)
    val senderLocationId = msgFlds.find(_._1 == TargetLocationIDField.TagId).map(_._2)
    val targetCompId = msgFlds.find(_._1 == SenderCompIDField.TagId).map(_._2)
    val targetSubId = msgFlds.find(_._1 == SenderSubIDField.TagId).map(_._2)
    val targetLocationId = msgFlds.find(_._1 == SenderLocationIDField.TagId).map(_._2)

    if (beginStr.isDefined && senderCompId.isDefined && targetCompId.isDefined) {
      Some(new SfSessionId(beginString = beginStr.get,
        senderCompId = senderCompId.get,
        senderSubId = senderSubId,
        senderLocationId = senderLocationId,
        targetCompId = targetCompId.get,
        targetSubId = targetSubId,
        targetLocationId = targetLocationId))
    } else None
  }

  /**
    * Only used during error reporting, so always return either the number, or 0
    */
  def getSeqNum(msgFlds: Array[Tuple2[Int, String]]): Int = {
    msgFlds.find(_._1 == MsgSeqNumField.TagId).map(_._2) match {
      case Some(v) =>
        try {
          v.toInt
        } catch {
          case ex: Exception => 0
        }
      case None => 1
    }
  }

  /**
    * Decodes fix tuples into a strongly typed message.
    *
    * @return (The decoded message, None).  OR
    *         (None, (Some(header), reason, explanation)) If the decode failed
    *         (None, None) means its garbled and should be ignored.
    */
  def convertAMessage(msgFlds: Array[Tuple2[Int, String]], rejectDetails:Option[FixStrDecodeRejectDetails],latencyRecorder:Option[ActorRef]=None): (Option[SfMessage], Option[DecodingFailedData]) = {
    rejectDetails match {
      case Some(reason) =>
        // This means the fix string decoder found an error, but it was one the spec wanted passing back as a reject.
        // ie best place to detect it was in the string to tuple parser - but you need session data to pass back a reject.
        (None,
          Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
            reason.sessionRejectReasonField,
            TextField(reason.description)
          )))
      case None =>
        try {
          convertAMessageWithDecoder(msgFlds, latencyRecorder)
        } catch {
          case ex: SfMissingFieldsException =>
            (None,
              Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
                SessionRejectReasonField(SessionRejectReasonField.RequiredTagMissing),
                TextField(ex.getMessage)
              )))
          case SfDuplicateTagIdException(tagId) =>
            (None,
              Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
                SessionRejectReasonField(SessionRejectReasonField.TagAppearsMoreThanOnce),
                TextField(s"Duplicate TagId[$tagId] in message")
              )))
          case ex: IllegalArgumentException =>
            (None,
              Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
                SessionRejectReasonField(SessionRejectReasonField.IncorrectDataFormatForValue),
                TextField(ex.getMessage)
              )))
          case ex: Exception =>
            (None,
              Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
                SessionRejectReasonField(SessionRejectReasonField.DecryptionProblem),
                TextField(ex.getMessage)
              )))
        }
    }
  }

  def convertAMessageWithDecoder(msgFlds: Array[Tuple2[Int, String]],
                                 latencyRecorder:Option[ActorRef]=None): (Option[SfMessage], Option[DecodingFailedData]) = {
    val headerDecodeStartNanos = System.nanoTime()
    SfMessageHeader.decode(msgFlds) match {
      case Some(header) =>
        latencyRecorder.foreach(_ ! RecordMsgLatencyMsgIn(header.msgSeqNumField.value,"11.HeaderDecode", headerDecodeStartNanos))

        SfMessageFactory.getMessage(header.msgTypeField.value) match {
          case Some(msgDecoder) =>
            decodeTheMessage(msgFlds, header, msgDecoder, latencyRecorder)
          case None =>
            (None,
              Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
                SessionRejectReasonField(SessionRejectReasonField.InvalidMsgtype),
                TextField(s"Could not find message with MessageType (${MsgTypeField.TagId})=(${header.msgTypeField.value})")
              )))
        }
      case None =>
        (None,
          Some(DecodingFailedData(extractSessionId(msgFlds), getSeqNum(msgFlds),
            SessionRejectReasonField(SessionRejectReasonField.DecryptionProblem),
            TextField(s"Could not find any of the expected Header tags in [${msgFlds.mkString(",")}]")
          )))
    }
  }

  /** *
    *
    * @return Can return None,None, or Some msg, None, or None, Some reject message
    */
  def decodeTheMessage(msgFlds: Array[Tuple2[Int, String]], header: SfMessageHeader,
                       bodyDecoder: SfFixMessageDecoder,
                       latencyRecorder:Option[ActorRef]=None): (Option[SfMessage], Option[DecodingFailedData]) = {
    try {
      val startTStampNanos = System.nanoTime()

      val body = bodyDecoder.decode(msgFlds)
      val trailer = SfMessageTrailer.decode(msgFlds)

      latencyRecorder.foreach(_ ! RecordMsgLatencyMsgIn(header.msgSeqNumField.value,"12.BodyDecoded",startTStampNanos))
      val startExtensionsNanos = System.nanoTime()

      // Almost everyone disobeys the spec. So clump any other tags together
      val fixExtensions: List[Tuple2[Int, String]] = {
        msgFlds.filter(_ match {
          case (tagId, valStr) =>
            !SfMessageHeader.isFieldOf(tagId) && !bodyDecoder.isFieldOf(tagId) && !SfMessageTrailer.isFieldOf(tagId)
          case _ => false
        }).toList
      }

      latencyRecorder.foreach(_ ! RecordMsgLatencyMsgIn(header.msgSeqNumField.value,"13.ExtensionsDecoded",startTStampNanos))

      if (body.isDefined && trailer.isDefined) {
        val theMessage = new SfMessage(header, body.get, trailer.get, fixExtensions)
        (Some(theMessage), None)
      } else (None,
        Some(DecodingFailedData(Some(SfSessionId(header)), header.msgSeqNumField.value,
          SessionRejectReasonField(SessionRejectReasonField.DecryptionProblem),
          TextField(s"Failed to find any fields for the body or trailer of the message, missing tags, msg is [${msgFlds.mkString(",")}]"))
        ))
    } catch {
      case ex: SfMissingFieldsException =>
        (None, Some(DecodingFailedData(Some(SfSessionId(header)), header.msgSeqNumField.value,
          SessionRejectReasonField(SessionRejectReasonField.RequiredTagMissing),
          TextField(ex.getMessage)))
        )
      case ex:SfRepeatingGroupCountException =>
        (None, Some(DecodingFailedData(Some(SfSessionId(header)), header.msgSeqNumField.value,
          SessionRejectReasonField(SessionRejectReasonField.IncorrectNumingroupCountForRepeatingGroup),
          TextField(s"Bad count for repeating group, ie value!=actual number of group, TagId=[${ex.tagId}], value=[${ex.count}], expected value=[${ex.actualCount}]")))
        )
    }
  }

}
