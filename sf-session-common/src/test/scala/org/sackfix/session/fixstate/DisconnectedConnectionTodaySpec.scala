package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2017.
  */
class DisconnectedConnectionTodaySpec extends FlatSpec {
  behavior of "DisconnectedConnectionToday"
  val session = new SfSessionStub

  it should "Handle a SocketOpen event" in {
    DisconnectedConnectionToday.receiveSocketEvent(session, SfSessionServerSocketOpenEvent) match {
      case Some(AwaitingConnection) => // pass
      case _ => fail("Expected Some(AwaitingConnection)")
    }
  }

}
