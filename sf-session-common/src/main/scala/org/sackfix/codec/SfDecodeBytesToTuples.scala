package org.sackfix.codec

import akka.util.ByteString
import org.sackfix.field._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

object SfDecodeBytesToTuples {
  val SOH_BYTE: Byte = 1.toByte
  val SOH_CHAR: Char = 1.toChar
  val EQUALS_BYTE: Byte = '='.toByte
  val EQUALS_CHAR: Char = '='.toChar
}

/**
  * The SfDecoder should only be used by one actor, and as such does not need to worry about thread safety.
  *
  * I do not presume that each ByteString from the TCP layer is a complete message, which is why I am stateful
  * ie you can call me multiple times with different bytestrings and end up with one message
  *
  * Created by Jonathan during August 2016.
  */
class SfDecodeBytesToTuples(val validateLenAndChecksum: Boolean = true) {
  // Use SLF4J only logging in non actor classes.  Config will hook it into Akka logging
  // which in turn is hooked into Logback logging.
  private val log = LoggerFactory.getLogger(SfDecodeBytesToTuples.getClass)
  private val validator: Option[ValidateLenAndCheckSum] = if (validateLenAndChecksum) {
    Some(new ValidateLenAndCheckSum)
  } else {
    None
  }
  private var nextMsg = ArrayBuffer[Tuple2[Int, String]]()
  private var thisMsgLenSoFar : Int = 0
  private var startPos :Int = 0
  private var lastStartOfTagIdPos :Int = 0
  private var lastTagId = ""
  private var pos :Int = 0
  private var sumSoFar : Int = 0
  private var sumAtLastStart : Int =0
  private var tagStr = ""
  private var remainderFromPrev: Option[ByteString] = None
  private var firstByteReceiveTimeNanos: Long = 0
  private var msgSeqNum: String = ""
  private var rejectMsgDetails: Option[FixStrDecodeRejectDetails] = None
  private var nonNumericTagId = false
  private var badTags = new StringBuilder(500)


  /**
    * This class is very imperative and stateful, it decodes as much as it can, and
    * remembers how far it got until it gets to the checksum at the end of the message.
    *
    * Look into iteratees if you want to be scala'd up.
    */
  def decode(completeMessageCallback: (Array[Tuple2[Int, String]], Option[FixStrDecodeRejectDetails], DecoderTimestamps) => Unit,
             rejectAsGarbledCallback: (String, DecoderTimestamps) => Unit)(rawInput: ByteString): Unit = {
    if (firstByteReceiveTimeNanos < 1) firstByteReceiveTimeNanos = System.nanoTime()
    decodeToArray(rawInput, completeMessageCallback, rejectAsGarbledCallback)
  }

  private[codec] def decodeToArray(newFields: ByteString, callback: (Array[Tuple2[Int, String]], Option[FixStrDecodeRejectDetails], DecoderTimestamps) => Unit,
                                   rejectAsGarbledCallback: (String, DecoderTimestamps) => Unit): Unit = {
    val allBytes = {
      remainderFromPrev match {
        case None => newFields
        case Some(prevContent) => prevContent ++ newFields
      }
    }
    // pos is already offset into allBytes, no need to reprocess the prevContent
    newFields.foreach(b => {
      sumSoFar += b
      b match {
        case SfDecodeBytesToTuples.SOH_BYTE =>
          handleEndOfField(lastTagId, allBytes.slice(startPos, pos).utf8String)
          sumAtLastStart = sumSoFar
          startPos = pos + 1
          lastStartOfTagIdPos = startPos
        case ch@SfDecodeBytesToTuples.EQUALS_BYTE =>
          tagStr = allBytes.slice(startPos, pos).utf8String
          lastTagId = tagStr
          startPos = pos + 1
        case ch@_ =>
      }
      pos += 1
    })
    if (pos > lastStartOfTagIdPos) {
      remainderFromPrev = Some(allBytes.slice(lastStartOfTagIdPos, pos))
      pos = pos - lastStartOfTagIdPos
    } else {
      remainderFromPrev = None
      pos = 0
    }
    thisMsgLenSoFar += lastStartOfTagIdPos
    startPos -= lastStartOfTagIdPos
    lastStartOfTagIdPos =0

    // Nested to can share callback functions
    def handleEndOfField(tagIdStr: String, str: String) = {
      try {
        val tagId = tagIdStr.toInt

        if (str.isEmpty) {
          rejectMsgDetails = Some(FixStrDecodeRejectDetails(SessionRejectReasonField(SessionRejectReasonField.TagSpecifiedWithoutAValue),
            s"The tagID [$tagId] had no value."))
        } else {
          nextMsg += Tuple2(tagId, str)
        }

        if (tagId == MsgSeqNumField.TagId) {
          msgSeqNum = str
        }

        if (tagId == CheckSumField.TagId) {
          handleCheckSumField(str)
        } // else validator.foreach(_.workOutNumerics(tagIdStr + SfDecodeBytesToTuples.EQUALS_CHAR + str + SfDecodeBytesToTuples.SOH_CHAR))

        if (tagId == BodyLengthField.TagId) {
          validator.foreach(_.handleBodyLen(tagId, str, thisMsgLenSoFar + pos + 1))
        }
      } catch {
        case ex: NumberFormatException =>
          nonNumericTagId = true
          if (badTags.nonEmpty) badTags.append(",")
          badTags.append(tagStr)
      }
    }


    def isHeaderOrderBad: Boolean = {
      // Test spec section 2.t says
      // t. BeginString, BodyLength, and MsgType are not the first three fields of message.
      nextMsg.size<3 ||
        nextMsg(0)._1 != BeginStringField.TagId ||
        nextMsg(1)._1 != BodyLengthField.TagId ||
        nextMsg(2)._1 != MsgTypeField.TagId
    }

    /**
      * Checksum is the end of the message, so work out the real body lenght and checksum from bytes
      * received and compare to the mesage fields.
      * Then callback or reject it
      */
    def handleCheckSumField(checkSumFromMsgStr: String): Unit = {
      validator.foreach(_.determineMessageLen(thisMsgLenSoFar+lastStartOfTagIdPos))
      val tstampInfo = DecoderTimestamps(allBytes, pos+1,msgSeqNum, firstByteReceiveTimeNanos, System.nanoTime())
      if (nonNumericTagId) {
        rejectAsGarbledCallback(s"SeqNum=[$msgSeqNum] Failed to decode tag [${badTags.toString}]as was not numeric, message badly formatted.",
          tstampInfo)
      } else {
        if (checkSumFromMsgStr.length != 3)
          rejectAsGarbledCallback(s"SeqNum=[$msgSeqNum] Checksum value was not 3 characters [$checkSumFromMsgStr], message badly formatted.",
            tstampInfo)
        else {
          val checkSumFromMsg = checkSumFromMsgStr.toInt
          if (isHeaderOrderBad) {
            rejectAsGarbledCallback(s"SeqNum=[$msgSeqNum] Badly encoded message - expected first 3 fields to be ${BeginStringField.TagId},${BodyLengthField.TagId}, ${MsgTypeField.TagId}, but got ${if (nextMsg.nonEmpty) nextMsg(0)._1 else "missing"}, ${if (nextMsg.size>1) nextMsg(1)._1 else "missing"}, ${if (nextMsg.size>2) nextMsg(2)._1 else "missing"}",
              tstampInfo)
          } else {
            validator match {
              case None => callback(nextMsg.toArray, rejectMsgDetails, tstampInfo)
              case Some(v) => v.validateMsgNumerics(msgSeqNum, checkSumFromMsg, sumAtLastStart) match {
                case None =>
                  callback(nextMsg.toArray, rejectMsgDetails, tstampInfo)
                case Some(failureReason) =>
                  rejectAsGarbledCallback(failureReason, tstampInfo)
              }
            }
          }
        }
      }
      validator.foreach(_.reset())
      nonNumericTagId = false
      badTags.clear()
      nextMsg = ArrayBuffer[Tuple2[Int, String]]()
      firstByteReceiveTimeNanos = 0
      msgSeqNum = ""
      rejectMsgDetails = None
      sumSoFar = 0
      sumAtLastStart =0
      thisMsgLenSoFar = 0
      lastStartOfTagIdPos=0
    }
  }
}

class ValidateLenAndCheckSum {
  private var bodyLen: Int = 0
  private var bodyLenFromMsg: Int = 0
  private var posAfterBodyLenField:Int = 0

  def reset(): Unit = {
    bodyLen = 0
    bodyLenFromMsg = 0
    posAfterBodyLenField =0
  }

  // Validate message numerics
  def validateMsgNumerics(msgSeqNum: String, checkSumFromMsg: Int, sum:Int): Option[String] = {
    val checkSum = sum % 256
    if (bodyLenFromMsg != bodyLen) Some(s"SeqNum=[$msgSeqNum] Body Length in message [$bodyLenFromMsg] should have been [$bodyLen]")
    else if (checkSumFromMsg != checkSum) Some(s"SeqNum=[$msgSeqNum] CheckSum in message [$checkSumFromMsg] should have been [$checkSum]")
    else {
      None
    }
  }

  def handleBodyLen(tagId: Int, str: String, posAfterBodyLenField:Int): Unit = {
    bodyLenFromMsg = str.toInt
    this.posAfterBodyLenField = posAfterBodyLenField
  }

  def determineMessageLen(lenWithoutChecksum:Int): Unit = {
    bodyLen = lenWithoutChecksum - posAfterBodyLenField
  }
}

case class FixStrDecodeRejectDetails(sessionRejectReasonField: SessionRejectReasonField, description: String)

case class DecoderTimestamps(rawMsg:ByteString, msgEndPos:Int,
                             msgSeqNum: String, firstByteTstampNanos: Long, lastByteTstampNanos: Long)