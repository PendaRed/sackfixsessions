package org.sackfix.session

/**
  * Created by Jonathan in 2016.
  *
  * Ideas taken from Quickfix4J at this point, time to wrap it up.
  */
abstract class SfMessageStore() {

  def storeMySequenceNumber(sessionId:SfSessionId, seqNo:Int)
  def storeTheirSequenceNumber(sessionId:SfSessionId, seqNo:Int)
  def close(sessionId:SfSessionId)

  def storeOutgoingMessage(sessionId:SfSessionId,seqNum:Int, fixStr:String): Unit
  def readMessage(sessionId:SfSessionId,seqNum:Int) : Option[String]

  def archiveTodaysReplay(sessionId:SfSessionId) :Unit

  /**
    * @param readInitialSequenceNumbers If true the read the values, if false return 0,0
    */
  def initialiseSession(sessionId:SfSessionId, readInitialSequenceNumbers:Boolean):SfSequencePair
}

case class SfSequencePair(val ourSeqNum:Int, val theirSeqNum:Int)
