package org.sackfix.session.heartbeat

import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2016.
  */
class SfHeartbeaterSpec extends FlatSpec {
  behavior of "SfHeartbeater"

  it should "Fire listeners as expected" in {
    val heartbeater = new SfHeartbeater(20)
    var count = 0
    var count2 = 0
    heartbeater.listeners += new SfHeartbeatListener {
      override def heartBeatFired = {
        count+=1
//        println(s"1: $count")
      }
    }
    heartbeater.listeners += new SfHeartbeatListener {
      override def heartBeatFired = {
        count2+=1
//        println(s"2: $count2")
      }
    }
    heartbeater.start
    Thread.sleep(110)
    heartbeater.stop
    Thread.sleep(60)
    val fixedCount = count
    assert(count>=3)
    assert(count==count2)
    Thread.sleep(60)
    // Check it stoped
    assert(count==fixedCount)
  }
}
