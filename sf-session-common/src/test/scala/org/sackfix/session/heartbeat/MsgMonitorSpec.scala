package org.sackfix.session.heartbeat

import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2016.
  */
class MsgMonitorSpec extends AnyFlatSpec {
  behavior of "MsgMonitor"

  it should "Determine the number of heartbeats properly" in {
    val m  = new MsgMonitor(30L, 0)

    m.lastTimeActivityMs = 100L
    m.lastHearbeatCount = 0

    m.recordActivity(101L)

    assert(m.isNewElapsedCount(102)==None)
    assert(m.isNewElapsedCount(131).getOrElse(0)==1)
    assert(m.isNewElapsedCount(131)==None)
    assert(m.isNewElapsedCount(161).getOrElse(0)==2)
  }
  it should "Determine the number of heartbeats properly with transmission delay" in {
    val m  = new MsgMonitor(30L, 10)

    m.lastTimeActivityMs = 100L
    m.lastHearbeatCount = 0

    m.recordActivity(101L)

    assert(m.isNewElapsedCount(102)==None)
    assert(m.isNewElapsedCount(131)==None)
    assert(m.isNewElapsedCount(141).getOrElse(0)==1)
    assert(m.isNewElapsedCount(171).getOrElse(0)==2)
  }
}
