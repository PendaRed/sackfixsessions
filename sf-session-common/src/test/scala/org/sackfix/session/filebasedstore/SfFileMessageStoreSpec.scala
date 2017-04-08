package org.sackfix.session.filebasedstore

import org.sackfix.session.{SfSession, SfSessionId, SfSessionImpl, SfSessionStub}
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2016.
  */
class SfFileMessageStoreSpec extends FlatSpec {

  behavior of "SfFileMessageStore"

  private def createFileStoreFixture : SfFileMessageStore= {
    new SfFileMessageStore("./test/resources/tmp/acceptor")
  }

  it should "prefixCode" in {
    val fileStore = createFileStoreFixture
    val sessionId = new SfSessionId("beginString","senderCompId","targetCompId")
    val session = new SfSessionStub
    val initialIds = fileStore.initialiseSession(sessionId, false)
    assert("C:\\all_dev\\sackfix\\sackfixsessions\\.\\test\\resources\\tmp\\acceptor\\beginstring-sendercompid-targetcompid." == fileStore.getStore(sessionId).prefixCode)

    fileStore.close(sessionId)
  }

  it should "storeAndReadSequenceNumbers" in {
    val fileStore = createFileStoreFixture
    val sessionId = new SfSessionId("beginString","senderCompId","targetCompId")
    val session = new SfSessionStub
    val initialIds = fileStore.initialiseSession(sessionId, false)

    fileStore.storeMySequenceNumber(sessionId, 10)
    fileStore.storeTheirSequenceNumber(sessionId, 200)
    fileStore.close(sessionId)


    val fileStore2 = createFileStoreFixture
    val initialIds2 = fileStore2.initialiseSession(sessionId, true)
    assert(initialIds2.ourSeqNum==10)
    assert(initialIds2.theirSeqNum==200)

    fileStore2.storeMySequenceNumber(sessionId, 1)
    fileStore2.storeTheirSequenceNumber(sessionId, 2)

    fileStore2.close(sessionId)

    val fileStore3 = createFileStoreFixture
    val initialIds3 = fileStore3.initialiseSession(sessionId, true)
    assert(initialIds3.ourSeqNum==1)
    assert(initialIds3.theirSeqNum==2)
    fileStore3.close(sessionId)
  }
}
