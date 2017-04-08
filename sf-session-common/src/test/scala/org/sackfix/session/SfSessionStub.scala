package org.sackfix.session
import akka.actor.ActorRef
import org.sackfix.common.message.{SfMessage, SfMessageHeader}
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{SessionRejectReasonField, TextField}
import org.sackfix.session.fixstate.MessageFixtures

/**
  * Created by Jonathan on 02/01/2017.
  */
class SfSessionStub extends SfSession(SfAcceptor, SfSessionId(beginString="beginString",
  senderCompId="senderCompId",targetCompId="targetCompId"), 30) {

  override def resetSeqNums: Unit = {setTheirSeq(1)}

  override def setTheirSeq(resetTo: Int): Int = {
    nextTheirSeqNum = resetTo
    nextTheirSeqNum
  }
  override def setMySeq(resetTo: Int): Int = {
    nextMySeqNum = resetTo
    nextMySeqNum
  }

  def setLastTheirSeqNumForTesting(i:Int) = lastTheirSeqNum=i

  override def incrementMySeq: Int = {1}

  override def incrementTheirSeq: Int = {1}

  /**
    * Send a message to the outgoing connecton Actor.
    * Also store the message in the message store - but NOT admin messages.
    * This also sends the message, correlationId of "" means do not send the ACK to the business layer
    */
  override def sendAMessage(msgBody: SfFixMessageBody, correlationId:String=""): Unit= {
    val msg = new SfMessage(MessageFixtures.head, msgBody)
  }

  /**
    * Close the persistent store and record the time the session closed.  If it is opened tomorrow then
    * sequence numbers reset down to 1
    */
  override def close: Unit = {}

  /**
    * A fix message has arrived.  Yippee (I guess)
    */
  override def handleMessage(incomingMessage: SfMessage): Unit = None

  /**
    * Called back from the state machine when an action should be taken - either a reply message
    * or close the socket etc.
    *
    * @param action             The action
    */
  override def handleAction(action: SfAction): Unit = {}

  /**
    * Try to retrieve all the fix messages from the store, or if there are gaps then send a gap fill instead.
    * Note that its generally stupid to resent admin messages....up to you if you stick them in your
    * message store or not.
    *
    * @param beginSeqNum        The first one to send back
    * @param endSeqNum          The last one to send back
    */
  override def replayMessages(beginSeqNum: Int, endSeqNum: Int): Unit = {}

  override def sendRejectMessage(refSeqNum:Int, incSeqNum:Boolean, reason: SessionRejectReasonField, explanation: TextField): Unit = {
    println(s"Got a sendRejectMessage!!  [$explanation]")
  }

  /**
    * Deals with close from the previous day and the fact this process was not restarted
    * ie will reset the sequence numbers back to 1
    */
  override def getExpectedTheirSeqNum: Int = {nextTheirSeqNum}
}
