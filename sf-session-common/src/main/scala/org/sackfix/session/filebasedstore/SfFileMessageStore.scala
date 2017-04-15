package org.sackfix.session.filebasedstore

import java.io.{File, IOException, RandomAccessFile}
import java.time.{LocalDateTime, ZonedDateTime}

import org.sackfix.session._
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * This is a 'get you started' file store, but only for the sequence numbers.   Kafka, or some other modern
  * enterprise fabric should be used for everything else.
  *
  * It is NOT thread safe, so only use it from one Actor
  *
  * @param pathToFileStore The path to the directory used as the file based store
  * @param maxReplayCount Since all indexing is stored in memory your JVM will crash with
  *                       out of memory unless you size it correctly - eg about 2 million messages
  *                       caused it to fail with default JVM sizing.  -1 means no limit
  */
class SfFileMessageStore(pathToFileStore:String, val maxReplayCount:Int) extends SfMessageStore with SessionOpenTodayStore {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private var session: Option[SfSession] = None

  private val sessionLookupMap = mutable.HashMap.empty[SfSessionId, SfFileMessageSessionIdStore]


  def getStore(sessionId:SfSessionId) :  SfFileMessageSessionIdStore = {
    sessionLookupMap.get(sessionId) match {
      case None=>
        val msg = s"[${sessionId.id}] No session exists for sessionId [${sessionId.id}] - you should call ${this.getClass.getName}.initialiseSession before using it"
        logger.error(msg)
        throw new RuntimeException(msg)
      case Some(store) => store
    }
  }

  override def storeMySequenceNumber(sessionId:SfSessionId, seqNo:Int) = {
    getStore(sessionId).storeMySequenceNumber(seqNo)
  }
  override def storeTheirSequenceNumber(sessionId:SfSessionId, seqNo:Int) = {
    getStore(sessionId).storeTheirSequenceNumber(seqNo)
  }

  override def close(sessionId:SfSessionId) = {
    getStore(sessionId).closeFiles
  }

  override def archiveTodaysReplay(sessionId:SfSessionId) :Unit = {
    sessionLookupMap.get(sessionId) match {
      case None => logger.info(s"******************  No message store for [$sessionId]")
      case Some(store) => store.archiveTodaysReplay
    }
  }

  def storeOutgoingMessage(sessionId:SfSessionId, seqNum:Int, fixStr:String): Unit = {
    getStore(sessionId).storeOutgoingMessage(seqNum, fixStr)
  }
  def readMessage(sessionId:SfSessionId, seqNum:Int) : Option[String] = {
    getStore(sessionId).readMessage(seqNum)
  }

  override def initialiseSession(sessionId:SfSessionId, readInitialSequenceNumbers:Boolean):SfSequencePair ={
    sessionLookupMap.get(sessionId) match {
      case None =>
        val store = new SfFileMessageSessionIdStore(pathToFileStore, maxReplayCount, sessionId)
        sessionLookupMap(sessionId) = store
        store.initialiseSession(readInitialSequenceNumbers)
      case Some(store) =>
        // If any existing files, seed from them, if not then delete any existing files
        store.initialiseSession(readInitialSequenceNumbers)
    }
  }

  override def recordSessionConnected(sessionId: SfSessionId): Unit = {
    getStore(sessionId).recordSessionConnected(ZonedDateTime.now)
  }

  override def isThisFirstSessionToday(sessionId: SfSessionId): Boolean = {
    getStore(sessionId).isThisFirstSessionToday
  }
}
