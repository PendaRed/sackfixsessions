package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class AwaitingConnectionSpec extends FlatSpec {

  behavior of "AwaitingConnection"
  val session = new SfSessionStub

  it should "handle the connection established in receiveSocketEvent" in {
    assert(AwaitingConnection.receiveSocketEvent(session, SfSessionNetworkConnectionEstablishedEvent) ==
      Some(NetworkConnnectionEstablished))
  }
}
