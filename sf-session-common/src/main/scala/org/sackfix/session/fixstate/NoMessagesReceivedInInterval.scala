package org.sackfix.session.fixstate

import org.sackfix.field.TestReqIDField
import org.sackfix.fix44.TestRequestMessage
import org.sackfix.session._

/**
  * Created by Jonathan on 01/01/2017.
  */
class NoMessagesReceivedInInterval extends SfSessState(13, "No Messages Received In Interval",
  initiator = true, acceptor = true, isSessionOpen = true, isSessionSocketOpen = true) {
  lazy val reqId = "t" + System.currentTimeMillis()

  override protected[fixstate] def stateTransitionAction(fixSession: SfSession, ev: SfSessionEvent): List[SfAction] =
    List(SfActionSendMessageToFix(new TestRequestMessage(TestReqIDField(reqId))))

  override protected[fixstate] def nextState(fixSession: SfSession): Option[SfSessState] = Some(AwaitingProcessingResponseToTestRequest(reqId))
}
