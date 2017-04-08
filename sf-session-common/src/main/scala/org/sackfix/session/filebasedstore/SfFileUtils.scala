package org.sackfix.session.filebasedstore

import java.io.{File, IOException, RandomAccessFile}

import org.slf4j.LoggerFactory

/**
  * Created by Jonathan during 2017.
  */
object SfFileUtils {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def openFile(filename:String) : Option[RandomAccessFile] ={
    try {
      logger.debug(s"Opening file ${filename}")
      Some(new RandomAccessFile(filename, "rw"))
    } catch {
      case t:IOException => logger.warn(s"Failed to open file [${filename}], ${t.getClass.getName}:${t.getMessage}")
        None
    }
  }

  def closeFile(filename:String, f:Option[RandomAccessFile]) = {
    f match {
      case None =>
      case Some(raf) => {
        logger.debug(s"Closing file ${filename}")
        try {
          raf.close()
        } catch {
          case t:IOException => logger.warn(s"Failed to close file [${filename}], ${t.getClass.getName}:${t.getMessage}")
        }
      }
    }
  }

  def renameFile(fileName:String, newFileName:String) = {
    val f = new File(fileName)
    if (f.exists()) {
      deleteFile(newFileName)
      logger.debug(s"Renaming file $fileName to $newFileName")
      try {
        val newFile = new File(newFileName)
        f.renameTo(newFile)
      } catch {
        case t:IOException => logger.warn(s"Failed to rename file [${fileName}], ${t.getClass.getName}:${t.getMessage}")
      }
    }
  }

  def deleteFile(filename:String) = {
    val f = new File(filename)
    if (f.exists()) {
      logger.debug(s"Deleting file ${filename}")
      try {
        f.delete()
      } catch {
        case t:IOException => logger.warn(s"Failed to delete file [${filename}], ${t.getClass.getName}:${t.getMessage}")
      }
    }
  }
}
