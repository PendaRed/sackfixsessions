package org.sackfix.session.heartbeat

import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan on 28/12/2016.
  */
class SfSessionTimeHandlerSpec extends AnyFlatSpec {
  behavior of "SfSessionTimeHandler"

  it should "Determine the number of heartbeats properly" in {
    val sessionTimeoutHandler = new SessionTimeoutHandler {
      var rCount =0
      var sCount =0
      override def nothingReceivedFor(noHeartbeatsMissed: Int) = rCount=noHeartbeatsMissed
      override def nothingSentFor(noHeartbeatsMissed: Int) = sCount=noHeartbeatsMissed
    }

    val timeHander = new SfSessionTimeHandler(10, sessionTimeoutHandler, 0)
    // Reset the timers
    timeHander.receivedAMessage
    timeHander.sentAMessage
//    println(s"r=${sessionTimeoutHandler.rCount} s=${sessionTimeoutHandler.sCount}")
    timeHander.heartBeatFired
//    println(s"r=${sessionTimeoutHandler.rCount} s=${sessionTimeoutHandler.sCount}")

    assert(sessionTimeoutHandler.rCount==0)
    assert(sessionTimeoutHandler.sCount==0)

    Thread.sleep(20)
    timeHander.heartBeatFired

//    println(s"r=${sessionTimeoutHandler.rCount} s=${sessionTimeoutHandler.sCount}")
    assert(sessionTimeoutHandler.rCount>0)
    assert(sessionTimeoutHandler.sCount>0)
  }
}
