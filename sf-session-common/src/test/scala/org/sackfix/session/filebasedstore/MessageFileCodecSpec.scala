package org.sackfix.session.filebasedstore

import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Failure, Success}

/**
  * Created by Jonathan during December 2016.
  */
class MessageFileCodecSpec  extends AnyFlatSpec {

  behavior of "MessageFileCodec"

  it should "do nothing in deleteData if there is no file" in {
    MessageIndexFileCodec.deleteData("NoFile")

    assert(1==1)
  }
  it should "create an empty file and delete it" in {
    MessageIndexFileCodec.writeData("MessageFileCodecSpec.txt", List.empty[(Int, Long, Int)])
    MessageIndexFileCodec.deleteData("NoFile")

    assert(1==1)
  }
  it should "write some data and read it back in again" in {
    MessageIndexFileCodec.writeData("MessageFileCodecSpec.txt", List[(Int, Long, Int)]((1,2,3), (4,5,6)))
    val readData = MessageIndexFileCodec.readData("MessageFileCodecSpec.txt")
    MessageIndexFileCodec.deleteData("MessageFileCodecSpec.txt")

    readData match {
      case Success(l) =>
        assert(l(0)==(1,2,3))
        assert(l(1)==(4,5,6))
      case Failure(ex) => println(ex)
        assert(false)
    }
  }
  it should "open a file and hold onto it for consecutive writes" in {
    MessageIndexFileCodec.openIndex("MessageFileCodecSpec.txt") match {
      case Success(indexFile) =>
        MessageIndexFileCodec.writeIndex(Some(indexFile), 1,2,3)
        MessageIndexFileCodec.writeIndex(Some(indexFile), 4,5,6)
        MessageIndexFileCodec.closeIndex(Some(indexFile))
      case Failure(ex) =>
        throw ex
    }
    val readData = MessageIndexFileCodec.readData("MessageFileCodecSpec.txt")
    MessageIndexFileCodec.deleteData("MessageFileCodecSpec.txt")

    readData match {
      case Success(l) =>
        assert(l(0)==(1,2,3))
        assert(l(1)==(4,5,6))
      case Failure(ex) => println(ex)
        assert(false)
    }
  }

}
