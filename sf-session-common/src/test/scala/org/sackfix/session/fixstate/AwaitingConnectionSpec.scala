package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class AwaitingConnectionSpec extends AnyFlatSpec {

  behavior of "AwaitingConnection"
  val session = new SfSessionStub

  it should "handle the connection established in receiveSocketEvent" in {
    assert(AwaitingConnection.receiveSocketEvent(session, SfSessionNetworkConnectionEstablishedEvent) ==
      Some(NetworkConnnectionEstablished))
  }
}
