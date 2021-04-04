package org.sackfix.session.filebasedstore

import org.sackfix.session.{SfSequencePair, SfSessionId}
import org.slf4j.LoggerFactory

import java.io._
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZonedDateTime}
import scala.util.{Failure, Success}

/**
  * Created by Jonathan during 2017.
  *
  * @param maxReplayCount If -1 then no limit - but you may fail with out of memory.
  */
class SfFileMessageSessionIdStore(pathToFileStore:String, val maxReplayCount:Int , val sessionId:SfSessionId) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ")
  val storeDir = getPathDir

  private[session] val prefixCode = storeDir + sessionId.fileName + "."
  private val mySeqFileName = prefixCode+"senderseqnums"
  private val theirSeqFileName = prefixCode+"targetseqnums"
  private val msgIndexFileName = prefixCode+"messagesindex"
  private val msgFileName = prefixCode+"messages"
  private val sessionTodayFileName = prefixCode+"firstSessionOpened"

  private var mySeqFile:Option[RandomAccessFile] = None
  private var theirSeqFile:Option[RandomAccessFile] = None

  // Sequence number, to seek,size tuple
  private val msgSeekLookup = scala.collection.mutable.LinkedHashMap.empty[Int,(Long, Int)]
  private var msgIndexFile:Option[RandomAccessFile] = None
  private var msgFile:Option[RandomAccessFile] = None

  private val fileArchiveDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS")

  private[session] def getPathDir = {
    val path = {if (pathToFileStore==null) "" else pathToFileStore}
    val dir = new File(path)

    if (!dir.exists) dir.mkdirs()

    val fullPath= dir.getAbsolutePath
    if (fullPath.endsWith(File.separator)) fullPath
    else fullPath+File.separator
  }

  private def storeNum(file:Option[RandomAccessFile] , seqNo:Int): Unit = {
    file match {
      case None =>
      case Some(f) =>
        f.seek(0)
        f.writeUTF(""+seqNo)
    }
  }

  def storeMySequenceNumber( seqNo:Int) = {
    storeNum(mySeqFile, seqNo)
  }
  def storeTheirSequenceNumber( seqNo:Int) = {
    storeNum(theirSeqFile, seqNo)
  }


  def closeFiles = {
    SfFileUtils.closeFile(theirSeqFileName, theirSeqFile)
    theirSeqFile = None
    SfFileUtils.closeFile(mySeqFileName, mySeqFile)
    mySeqFile = None
    SfFileUtils.closeFile(msgIndexFileName, msgIndexFile)
    msgIndexFile = None

    SfFileUtils.closeFile(msgFileName, msgFile)
    msgFile = None
  }


  def deleteFiles = {
    closeFiles
    SfFileUtils.deleteFile(mySeqFileName)
    SfFileUtils.deleteFile(theirSeqFileName)

    // Note that intraday restarts can create loads of _YYYYmmDD..archive files
    val d = new File(storeDir)
    if (d.exists && d.isDirectory) {
      val allFiles = d.listFiles.filter(_.isFile)
      val allIndexFiles =  allFiles.filter(_.getName.startsWith(msgIndexFileName))
      allIndexFiles.foreach{f:File => SfFileUtils.deleteFile(f.getAbsolutePath)}

      val allMsgFiles =  allFiles.filter(_.getName.startsWith(msgFileName))
      allMsgFiles.foreach{f:File => SfFileUtils.deleteFile(f.getAbsolutePath)}
    }
  }

  def initialiseMessageLookup = {
    msgSeekLookup.clear()

    val indexFile = new File(msgIndexFileName)
    if (indexFile.exists()) {
      logger.info(s"[${sessionId.id}] Reading message index file name [$msgIndexFileName], so can replay messages as needed")
      MessageIndexFileCodec.readData(msgIndexFileName) match {
        case Success( indexValues : List[(Int, Long, Int)]) =>
          indexValues.foreach( (seqOffSize:Tuple3[Int,Long,Int]) => {
            msgSeekLookup(seqOffSize._1) = (seqOffSize._2, seqOffSize._3)
            if (maxReplayCount >= 0 && msgSeekLookup.size > maxReplayCount)
              msgSeekLookup.remove(msgSeekLookup.head._1)
          })
          logger.info(s"[${sessionId.id}] Reading message index file name [$msgIndexFileName], completed read of ${msgSeekLookup.size} records")
        case Failure(e) =>
          logger.error(s"[${sessionId.id}] Failed to read message index file name [$msgIndexFileName], replay of messages will not work")
      }
    } else {
      logger.info(s"[${sessionId.id}] No message store index file found [${msgIndexFileName}]")
    }
    logger.info(s"[${sessionId.id}] Opening files (will store all non session level messages) [$msgIndexFileName],[$msgFileName].")
    msgIndexFile = SfFileUtils.openFile(msgIndexFileName)
    msgFile = SfFileUtils.openFile(msgFileName)
  }

  /**
    * This message store is no good at handling reset seq nums or preserving day by day message stores.
    */
  def archiveTodaysReplay = {
    if (msgIndexFile.isDefined || msgFile.isDefined) {
      val archivePostfix = "_"+LocalDateTime.now.format(fileArchiveDateFormatter) +".archive"
      logger.info(s"[${sessionId.id}] Session is resetting sequence files, so moving current message store files to ${archivePostfix}")

      SfFileUtils.closeFile(msgIndexFileName, msgIndexFile)
      SfFileUtils.closeFile(msgFileName, msgFile)

      SfFileUtils.renameFile(msgIndexFileName, msgIndexFileName+archivePostfix)
      SfFileUtils.renameFile(msgFileName, msgFileName+archivePostfix)
      msgSeekLookup.clear()
      msgIndexFile = SfFileUtils.openFile(msgIndexFileName)
      msgFile = SfFileUtils.openFile(msgFileName)
    }
  }

  private[session] def readIntValueFromFile(seqFile:Option[RandomAccessFile], filePath:String) : Int = {
    try {
      seqFile match {
        case None => 1
        case Some(f) =>
          if (f.length()>0) {
            f.seek(0)
            Integer.parseInt(f.readUTF())
          } else 1
      }
    } catch {
      case ex: IOException => throw new IOException(s"Failure trying to read initial sequence numbers from [$filePath]", ex)
    }
  }

  /**
    * @param readInitialSequenceNumbers If true read from the existing files, otherwise delete existing files
    */
  def initialiseSession(readInitialSequenceNumbers:Boolean):SfSequencePair ={
    if (readInitialSequenceNumbers) {
      closeFiles
      mySeqFile = SfFileUtils.openFile(mySeqFileName)
      theirSeqFile = SfFileUtils.openFile(theirSeqFileName)

      // If any existing files, seed from them.
      initialiseMessageLookup
      SfSequencePair(readIntValueFromFile(mySeqFile, mySeqFileName),
        readIntValueFromFile(theirSeqFile, theirSeqFileName))
    } else {
      deleteFiles
      mySeqFile = SfFileUtils.openFile(mySeqFileName)
      theirSeqFile = SfFileUtils.openFile(theirSeqFileName)
      storeMySequenceNumber(1)
      storeTheirSequenceNumber(1)
      SfSequencePair(1, 1)
    }
  }


  def storeOutgoingMessage(seqNum:Int, fixStr:String): Unit = {
    msgFile match {
      case None =>
      case Some(raf) =>
        try {
          val pos = raf.length()
          raf.seek(pos)
          raf.writeUTF(fixStr)
          val len = (raf.length() - pos).toInt
          msgSeekLookup(seqNum) = (pos, len)
          if (maxReplayCount >= 0 && msgSeekLookup.size > maxReplayCount)
            msgSeekLookup.remove(msgSeekLookup.head._1)

          MessageIndexFileCodec.writeIndex(msgIndexFile, seqNum, pos, len) match {
            case Success(_) =>
            case Failure(t:IOException) =>
              logger.error(s"[${sessionId.id}] Failed to write fix index to file [${msgIndexFileName}], closing file")
              SfFileUtils.closeFile(msgIndexFileName, msgIndexFile)
              msgIndexFile = None
            case Failure(t:Throwable) =>
          }
        } catch {
          case ex:IOException =>
            logger.error(s"[${sessionId.id}] Failed to write fix messsage to file [${msgFileName}], closing file")
            SfFileUtils.closeFile(msgFileName, msgFile)
            msgFile = None
        }
    }
  }

  def findSeekPos(seqNum:Int) : Option[(Long, Int)] = {
    msgSeekLookup.get(seqNum)
  }
  def readMessage(seqNum:Int) : Option[String] = {
    msgSeekLookup.get(seqNum) match {
      case None => None
      case Some((pos, len)) =>
        msgFile match {
          case None => None
          case Some(raf) =>
            raf.seek(pos)
            Some(raf.readUTF())
        }
    }
  }

  def recordSessionConnected(openTime:ZonedDateTime): Unit = {
    var f:Option[RandomAccessFile] = None
    try {
      f = SfFileUtils.openFile(sessionTodayFileName)
      f match {
        case Some(file) =>
          file.seek(0)
          f.foreach(_.writeUTF(dateTimeFormatter.format(openTime)))
        case None =>
      }
    } finally {
      SfFileUtils.closeFile(sessionTodayFileName, f)
    }
  }

  def isThisFirstSessionToday(): Boolean = {
    var f:Option[RandomAccessFile] = None
    try {
      f = SfFileUtils.openFile(sessionTodayFileName)
      f match {
        case Some(file) =>
          try {
            file.seek(0)
            val lastSessionTime = file.readUTF()
            val tm = ZonedDateTime.parse(lastSessionTime, dateTimeFormatter)
            (tm.getDayOfYear != ZonedDateTime.now.getDayOfYear)
          } catch {
            case ex:EOFException => true // fine, no file, so return true
          }
        case None => true
      }
    } finally {
      SfFileUtils.closeFile(sessionTodayFileName, f)
    }
  }

}
