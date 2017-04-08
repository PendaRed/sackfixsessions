package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.field.TestReqIDField
import org.sackfix.fix44.HeartbeatMessage
import org.sackfix.session._
import org.sackfix.session.fixstate.AwaitingConnection.{logger, stateName}

/**
  * Created by Jonathan on 01/01/2017.
  */
case class AwaitingProcessingResponseToTestRequest(val reqId: String) extends SfSessState(14, "Awaiting Processing Response To Test Request",
    initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  override protected[fixstate] def receiveFixMsg(fixSession: SfSession, msgIn: SfMessage, actionCallback: SfAction => Unit): Option[SfSessState] = {
    // @TODO possible duplicate and seq too low handling...

    fixSession.incrementTheirSeq
    msgIn.body match {
      case hrtBeat: HeartbeatMessage =>
        val s = hrtBeat.testReqIDField.getOrElse(TestReqIDField("")).value
        if (s != reqId)
          logger.warn(s"[${fixSession.idStr}] Received a heartbeat reply to TestRequest, but reqId of [$s] did not match exepected reqId of [$reqId]")
        Some(ActiveNormalSession)
      case fixMsg @ _ =>
        logger.warn(s"[${fixSession.idStr}] Received a reply to TestRequest of type [${fixMsg.msgType}] did not match exepected Heartbeat, presume crossover and handle normally")
        ActiveNormalSession.receiveFixMsg(fixSession, msgIn, actionCallback)
    }
  }

  /**
    * If get 2* heartbeat period then logout with Text=“Test Request Timeout”
    */
  override protected[fixstate] def receiveControlEvent(sfSession: SfSession, event: SfSessionControlEvent): Option[SfSessState] = {
    event match {
      case SfControlNoReceivedHeartbeatTimeout(noBeatsMissed: Int) =>
        if (noBeatsMissed >= 2) Some(InitiateLogoutProcess("Test Request Timeout"))
        else None
      case _ => super.receiveControlEvent(sfSession, event)
    }
  }

}
