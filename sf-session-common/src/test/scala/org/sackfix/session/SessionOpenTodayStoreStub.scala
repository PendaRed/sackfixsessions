package org.sackfix.session

/**
  * Created by Jonathan during 2017.
  */
class SessionOpenTodayStoreStub extends SessionOpenTodayStore {
  override def recordSessionConnected(sessionId: SfSessionId): Unit = {}

  override def isThisFirstSessionToday(sessionId: SfSessionId): Boolean = true
}