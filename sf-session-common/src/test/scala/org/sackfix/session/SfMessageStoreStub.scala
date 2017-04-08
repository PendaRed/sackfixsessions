package org.sackfix.session

import scala.collection.mutable

/**
  * Created by Jonathan during 2017.
  */
class SfMessageStoreStub() extends SfMessageStore {
  class DummyInfo {
    var mySeq =1
    var theirSeq = 1
    val msgLookup = mutable.HashMap.empty[Int,String]
  }

  private val sessLookup = mutable.HashMap.empty[String, DummyInfo]
  var closed = false

  def getInfo(sessId:SfSessionId) : DummyInfo = {
    sessLookup.get(sessId.id) match {
      case Some(i) =>i
      case None => val i = new DummyInfo
        sessLookup(sessId.id) = i
        i
    }
  }

  override def storeMySequenceNumber(sessionId: SfSessionId, seqNo: Int): Unit =
    getInfo(sessionId).mySeq = seqNo

  override def storeTheirSequenceNumber(sessionId: SfSessionId, seqNo: Int): Unit =
    getInfo(sessionId).theirSeq = seqNo

  override def close(sessionId: SfSessionId): Unit = {closed=true}

  override def storeOutgoingMessage(sessionId: SfSessionId, seqNum: Int, fixStr: String): Unit =
    getInfo(sessionId).msgLookup(seqNum) = fixStr

  override def readMessage(sessionId: SfSessionId, seqNum: Int): Option[String] =
    getInfo(sessionId).msgLookup.get(seqNum)

  override def initialiseSession(sessionId: SfSessionId, readInitialSequenceNumbers: Boolean): SfSequencePair = {
    val i = getInfo(sessionId)
    if (!readInitialSequenceNumbers) {
      i.mySeq =1
      i.theirSeq =1
    }
    SfSequencePair(i.mySeq, i.theirSeq)
  }

  override def archiveTodaysReplay(sessionId: SfSessionId): Unit = {}
}
