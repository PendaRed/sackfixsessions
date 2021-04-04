package org.sackfix.session.filebasedstore

import java.time.{LocalDateTime, ZonedDateTime}

import org.sackfix.session.{SfSequencePair, SfSessionId, SfSessionStub}
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class SfFileMessageSessionIdStoreSpec  extends AnyFlatSpec {

  behavior of "SfFileMessageStore"

  private def createFileStoreFixture(sillyTestId:String) : SfFileMessageSessionIdStore= {
    val sessId = new SfSessionId("beginString","senderCompId","targetCompId")
    new SfFileMessageSessionIdStore("./test/resources/tmp/acceptor"+sillyTestId, 10000, sessId)
  }

  it should "Store and reread sequence numbers" in {
    val fileStore = createFileStoreFixture("a")
    fileStore.initialiseSession(false)

    fileStore.storeMySequenceNumber(101)
    fileStore.storeTheirSequenceNumber(202)

    fileStore.closeFiles

    val fileStore2 = createFileStoreFixture("a")
    fileStore2.initialiseSession(true) match {
      case SfSequencePair(ourSeqNum:Int, theirSeqNum:Int) =>
        assert(ourSeqNum == 101)
        assert(theirSeqNum == 202)
    }
    fileStore.closeFiles
  }

  it should "Record todays session initialised" in {
    val fileStore = createFileStoreFixture("b")
    fileStore.recordSessionConnected(ZonedDateTime.now)
    assert(fileStore.isThisFirstSessionToday() == false)

    val yesterday = ZonedDateTime.now.minusDays(1)
    fileStore.recordSessionConnected(yesterday)
    assert(fileStore.isThisFirstSessionToday() == true)
  }
}
