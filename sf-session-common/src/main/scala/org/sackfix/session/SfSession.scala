package org.sackfix.session

import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field._

import scala.collection.immutable.HashSet

abstract class SfSession(val sessionType: SfSessionType, val sessionId: SfSessionId, val heartbeatIntervalSecs: Int) {

  protected val sessionMessageTypes: HashSet[String] = SfSession.SessionMessageTypes

  // At the start of the day we start with seq number of 1.
  private[session] var nextMySeqNum: Int = 1
  private[session] var nextTheirSeqNum: Int = 1

  private[session] var lastTheirSeqNum: Int = 1
  //  private [session] var lastCloseTime : Option[LocalDateTime] = None

  val idStr = sessionId.id


  def resetSeqNums

  def setTheirSeq(resetTo: Int): Int

  def setMySeq(resetTo: Int): Int

  def incrementMySeq: Int

  def incrementTheirSeq: Int

  /**
    * Creates the full message which can be sent out.
    * Also store the message in the message store - but NOT admin messages.
    * ANd then send it out
    */
  def sendAMessage(msgBody: SfFixMessageBody, correlationId:String) : Unit

  /**
    * Close the persistent store and record the time the session closed.  If it is opened tomorrow then
    * sequence numbers reset down to 1
    */
  def close

  /**
    * A fix message has arrived.  Yippee (I guess)
    *
    * @param incomingMessage   The incoming message
    */
  def handleMessage(incomingMessage: SfMessage): Unit

  /**
    * Called back from the state machine when an action should be taken - either a reply message
    * or close the socket etc.
    *
    * @param action             The action
    */
  def handleAction(action: SfAction)

  /**
    * Try to retrieve all the fix messages from the store, or if there are gaps then send a gap fill instead.
    * Note that its generally stupid to resent admin messages....up to you if you stick them in your
    * message store or not.
    *
    * @param beginSeqNum The first one to send back
    * @param endSeqNum   The last one to send back
    */
  def replayMessages(beginSeqNum: Int, endSeqNum: Int): Unit


  def isMessageFixSessionMessage(msgType: String): Boolean = {
    sessionMessageTypes.contains(msgType)
  }

  def sendRejectMessage(refSeqNum:Int, incSeqNum:Boolean, reason: SessionRejectReasonField, explanation: TextField)

  /**
    * Deals with close from the previous day and the fact this process was not restarted
    * ie will reset the sequence numbers back to 1
    */
  def getExpectedTheirSeqNum: Int

}

object SfSession {
  val SessionMessageTypes = HashSet[String](
    MsgTypeField.Logon, MsgTypeField.Logout, MsgTypeField.Heartbeat,
    MsgTypeField.Reject, MsgTypeField.ResendRequest, MsgTypeField.SequenceReset)
}


