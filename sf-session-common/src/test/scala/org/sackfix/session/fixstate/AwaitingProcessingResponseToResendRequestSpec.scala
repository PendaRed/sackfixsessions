package org.sackfix.session.fixstate

import org.sackfix.fix44.NewOrderSingleMessage
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during 2017.
  */
class AwaitingProcessingResponseToResendRequestSpec extends AnyFlatSpec {
  behavior of "AwaitingProcessingResponseToResendRequest"
  val session = new SfSessionStub

  it should "deal with replay and gapfill in receiveFixMsg" in {
    val receivedclOrdIds = ArrayBuffer.empty[String]

    val replayer = new AwaitingProcessingResponseToResendRequest(1, 5)

    val msg = MessageFixtures.newOrderSingle(1, "ordId1")

    var passed = true

    def actionCallback(action: SfAction): Unit = {
      action match {
        case SfActionBusinessMessage(b) =>
          b.body match {
            case body: NewOrderSingleMessage =>
              receivedclOrdIds += body.clOrdIDField.value
          }
        case _ => passed = false
      }
    }

    replayer.receiveFixMsg(session, msg, actionCallback)
    assert(passed)
  }
}
