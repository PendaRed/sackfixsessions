package org.sackfix.session.fixstate

import java.time.LocalDateTime

import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.TextField
import org.sackfix.fix44.{LogoutMessage, ResendRequestMessage}
import org.sackfix.session._

/**
  * Created by Jonathan on 01/01/2017.
  *
  * There are some situations especially during logon where we send logout with a reason and then close the socket.
  * Otherwise you send logout and then wait for a reply logout... or a timeout.
  */
case class InitiateLogoutProcess(val reason:String, val closeSocketAfterSend:Boolean = false,
                                 val pausePriorToSocketCloseMs:Option[Long]=None) extends SfSessState(16,"Initiate Logout Process",
      initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  val timeoutId = "LogoutTimeout"+LocalDateTime.now.toString

  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]= {
    val logoutAction = SfActionSendMessageToFix(new LogoutMessage(textField=Some(new TextField(reason))))
    pausePriorToSocketCloseMs match {
      case None => List(logoutAction)
      case Some(durationMs) =>
        List(logoutAction,
          SfActionStartTimeout(timeoutId, durationMs))
    }
  }

  override protected[fixstate] def nextState(fixSession:SfSession) : Option[SfSessState] = {
    if (closeSocketAfterSend && pausePriorToSocketCloseMs.isEmpty) Some(DisconnectSocketNow)
    else None
  }

  /**
    * During this time handle “new” inbound messages and/or ResendRequest if possible.
    * Note that some logout/termination conditions (e.g. loss of database/message safe-store) may
    * require immediate termination of the network connection following the initial send of the
    * Logout message.  Disconnect the network connection and “shutdown” configuration for this
    * session.
    */
  override protected[fixstate] def receiveFixMsg(fixSession:SfSession, msgIn:SfMessage,actionCallback:SfAction=>Unit): Option[SfSessState] = {
    val expectedSequenceNumber = fixSession.getExpectedTheirSeqNum
    val msgSeqNum = fixSession.lastTheirSeqNum
    if (expectedSequenceNumber!=msgSeqNum) {
      logger.info(s"[${fixSession.idStr}] Received SeqNum [$msgSeqNum] when expected [$expectedSequenceNumber], already waiting for logout, so closing socket.")
      Some(DisconnectSocketNow)
    } else {
      fixSession.incrementTheirSeq
      msgIn.body match {
        case resendReq: ResendRequestMessage =>
          Some(HandleResendRequest(this))
        case logoutMessage: LogoutMessage =>
          Some(DisconnectSocketNow)
        case body:SfFixMessageBody if (fixSession.isMessageFixSessionMessage(body.msgType)) =>
          // Stupid fix spec - eg if they send a Reset now.. sod em
          logger.info(s"[${fixSession.idStr}] Received msgType [${body.msgType}] while waiting for logout, so closing socket.")
          Some(DisconnectSocketNow)
        case _ =>
          actionCallback(SfActionBusinessMessage(msgIn))
          None
      }
    }
  }

  override protected[fixstate] def receiveControlEvent(fixSession:SfSession, event:SfSessionControlEvent) : Option[SfSessState] ={
    event match {
      case SfControlTimeoutFired(id, durationMs) if (id==timeoutId) =>
        logger.info(s"[${fixSession.idStr}] No reply for at least "+durationMs+" ms, disconnecting socket")
        Some(DisconnectSocketNow)
      case heartbeatTimeout: SfControlNoReceivedHeartbeatTimeout =>
        logger.info(s"[${fixSession.idStr}] No reply for at least "+heartbeatTimeout.noBeatsMissedPlus20Percent+" heartbeats+20%, disconnecting socket")
        Some(DisconnectSocketNow)
      case ev@ _ =>
        super.receiveControlEvent(fixSession, event)
    }
  }
}
