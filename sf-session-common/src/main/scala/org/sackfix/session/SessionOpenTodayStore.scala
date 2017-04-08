package org.sackfix.session

/** This is needed to determine if the session has opened today or not.
  * For instance, if it is a long running process, then it knows already, but if you bounce it
  * every nightr and maybe your ctrl-m or autosys was down, or even the host was late starting then
  * how does the process know that it should start with sequence numers at 1?
  *
  * This trait is the answer.
  */
trait SessionOpenTodayStore {
  def recordSessionConnected(sessionId:SfSessionId)
  def isThisFirstSessionToday(sessionId:SfSessionId):Boolean
}
