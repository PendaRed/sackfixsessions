package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during December 2016.
  */
class DisconnectedNoConnectionTodaySpec extends AnyFlatSpec {
  behavior of "DisconnectedNoConnectionToday"
  val session = new SfSessionStub

  it should "Move from disconnected to awaiting connection" in {
    DisconnectedNoConnectionToday.receiveSocketEvent(session, SfSessionServerSocketOpenEvent) match {
      case Some(AwaitingConnection) => // pass
      case _ => fail("Expected Some(AwaitingConnection)")
    }
  }

  it should "Move from disconnected to handle a socket close" in {
    assert(DisconnectedNoConnectionToday.receiveSocketEvent(session,SfSessionSocketCloseEvent)==Some(AwaitingConnection))
  }

}
