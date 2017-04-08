package org.sackfix.session.filebasedstore

import java.io.{DataInputStream, File, FileInputStream, RandomAccessFile}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.{Success, Try}

/**
  * Created by Jonathan during December 2016.
  */
object MessageIndexFileCodec {
  private def using[A <: {def close()}, B](closeable: A)(f: A => B): B = {
    try f(closeable) finally closeable.close()
  }

  def readData(filename: String) : Try[List[(Int, Long, Int)]]= {
    @tailrec
    def readDataInputStream(acc: ArrayBuffer[(Int, Long, Int)])(dis: DataInputStream): Try[List[(Int, Long, Int)]] = {
      if (dis.available() == 0) Success(acc.toList)
      else readDataInputStream(acc += Tuple3(dis.readInt, dis.readLong, dis.readInt))(dis)
    }
    def readIndexFileStream(fis: FileInputStream): Try[List[(Int, Long, Int)]] = {
      for {
        dis <- Try(new DataInputStream(fis))
        listOfTuples <- readDataInputStream(ArrayBuffer.empty[(Int, Long, Int)])(dis)
      } yield listOfTuples
    }

    Try(using(new FileInputStream(filename))(readIndexFileStream)).flatten
  }

  def deleteData(fileName:String)  = {
    SfFileUtils.deleteFile(fileName)
  }

  def writeData(fileName: String, data: List[(Int, Long, Int)]): Try[Unit] = {
    def writeRandomAccessFile(data: List[(Int, Long, Int)])(raf: RandomAccessFile): Try[Unit] = {
      Try {
        raf.seek(0)
        data.foreach { case (seqNo, pos, len) => {
          raf.writeInt(seqNo)
          raf.writeLong(pos)
          raf.writeInt(len)
        }
        }
      }
    }

    Try(using(new RandomAccessFile(fileName, "rw"))(writeRandomAccessFile(data))).flatten
  }

  def openIndex(fileName: String): Try[RandomAccessFile] = {
    Try(new RandomAccessFile(fileName, "rw"))
  }

  def closeIndex(fileToClose: Option[RandomAccessFile]): Try[Unit] = {
    fileToClose match {
      case None => Success()
      case Some(raf) =>Try(raf.close)
    }
  }

  def writeIndex(writeFile: Option[RandomAccessFile], seqNo: Int, pos: Long, len: Int): Try[Unit] = {
    writeFile match {
      case None => Success()
      case Some(raf) =>
        Try({
          raf.writeInt(seqNo)
          raf.writeLong(pos)
          raf.writeInt(len)
        })
    }
  }
}
