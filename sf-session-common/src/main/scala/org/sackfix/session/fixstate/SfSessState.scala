package org.sackfix.session.fixstate

import java.time.{LocalDateTime, Period, ZoneOffset}

import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{PossDupFlagField, SessionRejectReasonField, TextField}
import org.sackfix.fix44._
import org.sackfix.session._
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

/**
  * Base of all initiator or acceptor states.   When enter the state an action can fire
  * and then you can transition right to another state.
  * Or you can receive events, which may then trigger a new state...
  *
  * Not my design - its from their state transition spec.
  */
abstract class SfSessState(val id: Int, val stateName: String,
                           val initiator: Boolean, val acceptor: Boolean,
                           val isSessionOpen: Boolean,
                           val isSessionSocketOpen: Boolean = true) {
  protected lazy val logger = LoggerFactory.getLogger(this.getClass)

  /**
    * On receipt of an event - a socket connection or a message a series of state transitions can fire
    * off, each may send an action back via the callback, and then transition to anoher state which
    * can again fire an action etc.  this continues until no new state transitions occur
    *
    * @return the eventual new state
    */
  def receiveEvent(fixSession: SfSession, ev: SfSessionEvent, actionCallback: SfAction => Unit): SfSessState = {
    handleEvent(fixSession, ev, actionCallback) match {
      case None => this
      case Some(SessNoChangeEventConsumed) => this
      case Some(newState) =>
        stateTransition(fixSession, newState, ev, actionCallback) match {
          case None => this
          case Some(newState) => newState
        }
    }
  }

  /**
    * After receiving an event we may move to another state - in which case call
    * newState.stateTransitionAction -> which may call back with an action to take
    * newState.nextState -> ie do we move right on to yet another state, in which case repeat
    */
  @tailrec
  private[fixstate] final def stateTransition(fixSession: SfSession, newState: SfSessState, ev: SfSessionEvent, actionCallback: SfAction => Unit): Option[SfSessState] = {
    logger.info(s"[${fixSession.idStr}] - [${newState.id}:${newState.stateName}] state entered, due to event [${ev.description}]")
    newState.stateTransitionAction(fixSession, ev).foreach(actionCallback(_))

    newState.nextState(fixSession) match {
      case None => Some(newState) // ie this is the final state we should end in.
      case Some(nextNewState) => stateTransition(fixSession, nextNewState, ev, actionCallback)
    }
  }

  /**
    * Default action on entering a state is to do nothing
    *
    * @param ev The event which entered the previous states handleEvent and which fired off the action
    */
  protected[fixstate] def stateTransitionAction(fixSession: SfSession, ev: SfSessionEvent): List[SfAction] = {
    List.empty
  }

  /**
    * Some state fire off a stateTransitionAction and then directly transition to a new state,
    * they have no 'steady wait' condition for handling events like messages and sockets
    */
  protected[fixstate] def nextState(fixSession: SfSession): Option[SfSessState] = {
    None
  }

  private[fixstate] def handleEvent(fixSession: SfSession, ev: SfSessionEvent, actionCallback: SfAction => Unit): Option[SfSessState] = ev match {
    case SfSessionFixMessageEvent(msg) => receiveFixMsg(fixSession, msg, actionCallback)
    case socketEvent: SfSessionSocketEvent => receiveSocketEvent(fixSession, socketEvent)
    case control: SfSessionControlEvent => receiveControlEvent(fixSession, control)
  }

  /**
    * When logging in or whenever, validate sequence numbers and dael with them!
    *
    * @return None if sequence numbers are fine
    */
  private[fixstate] def handleSequenceNumberTooLow(fixSession: SfSession): Option[SfSessState] = {
    val expectedSequenceNumber = fixSession.getExpectedTheirSeqNum
    val msgSeqNum = fixSession.lastTheirSeqNum
    if (msgSeqNum < expectedSequenceNumber) {
      logger.info(s"[${fixSession.idStr}] Sequence number [$msgSeqNum] too low, expected [$expectedSequenceNumber], disconnecting socket")
      Some(DisconnectSocketNow)
      // I'm sure some part of the spec said logout, but the tests from Fix say disconnect
      //      Some(InitiateLogoutProcess(s"Sequence number [$msgSeqNum] too low, expected [$expectedSequenceNumber]"))
    } else None
  }

  private[fixstate] def handleSequenceNumberTooHigh(fixSession: SfSession): Option[SfSessState] = {
    val expectedSequenceNumber = fixSession.getExpectedTheirSeqNum
    val msgSeqNum = fixSession.lastTheirSeqNum
    if (msgSeqNum > expectedSequenceNumber) {
      logger.info(s"[${fixSession.idStr}] Sequence number [$msgSeqNum] too high, expected [$expectedSequenceNumber], initiating replay")
      Some(ReceiveMsgSeqNumTooHigh(expectedSequenceNumber, msgSeqNum))
    } else None
  }

  /**
    * Resent duplicate session level messages can be ignored
    */
  private[fixstate] def isResentDuplicate(fixSession: SfSession, msgIn: SfMessage) = {
    msgIn.header.possDupFlagField match {
      case Some(possDupFlag) if (possDupFlag.value) =>
        val expectedSequenceNumber = fixSession.getExpectedTheirSeqNum
        val msgSeqNum = fixSession.lastTheirSeqNum
        (msgSeqNum < expectedSequenceNumber)
      case _ => false
    }
  }

  /**
    * Resent duplicate message HAVE to have possdup=Y and orifSendingTime<=sendingTime otherwise
    * reject & logout and wait a while for the reply logout, or if too slow close socket
    * Section 2f of the test spec
    *
    */
  private[fixstate] def isSendingTimeErrorOnResentDup(fixSession: SfSession, msgIn: SfMessage): Boolean = {
    msgIn.header.origSendingTimeField match {
      case None =>
        logger.info(s"[${fixSession.idStr}] ${msgIn.header.msgSeqNumField} SendingTime acccuracy problem, origSendingTime missing on dup message")
        true
      case Some(origSendingTime) =>
        val sendingTime = msgIn.header.sendingTimeField
        if (origSendingTime.value.isAfter(sendingTime.value)) {
          logger.info(s"[${fixSession.idStr}] ${msgIn.header.msgSeqNumField} SendingTime acccuracy problem, origSendingTime[$origSendingTime]>sendingTime[${sendingTime.value}]")
          true
        } else false
    }
  }

  /**
    * Sending time has to be within 2 minutes
    * 1.	Send Reject (session-level) message referencing inaccurate SendingTime (>= FIX 4.2: SessionRejectReason = "SendingTime acccuracy problem")
    * 2.	Increment inbound MsgSeqNum
    * 3.	Send Logout message referencing inaccurate SendingTime value
    * 4.	Wait for Logout message response (note likely will have inaccurate SendingTime) or wait 2 seconds whichever comes first
    * 5.	Disconnect
    * 6.	Generate an "error" condition in test output.
    */
  private[fixstate] def handleClocksInSync(fixSession: SfSession, msgIn: SfMessage): Option[SfSessState] = {
    val now = LocalDateTime.now
    val msgTime = msgIn.header.sendingTimeField.value

    if (isMoreThan2Mins(now, msgTime)) {
      val msg = s"SendingTimeField [${msgIn.header.sendingTimeField.toString()}] more than 2 minutes different from [$now]"
      fixSession.sendRejectMessage(msgIn.header.msgSeqNumField.value, true,SessionRejectReasonField(SessionRejectReasonField.SendingtimeAccuracyProblem),
        TextField(msg))
      Some(InitiateLogoutProcess(msg, true, Some(2000L)))
    } else None
  }

  private[fixstate] def isMoreThan2Mins(now:LocalDateTime, msgTime:LocalDateTime) = {
    val startWin = now.minusMinutes(2)
    if (msgTime.isBefore(startWin)) true
    else {
      val endWin = now.plusMinutes(2)
      msgTime.isAfter(endWin)
    }
  }

  /**
    * All sorts of extra validation rules in the test spec, all a bit random I think, but they are in the spec.
    * See section 2 about receiving a standard header
    */
  private[fixstate] def validateStandardHeader(fixSession: SfSession, msgIn: SfMessage): Option[SfSessState] = {
    // Test spec has a load of validation when past login sequence.
    msgIn.header.possDupFlagField match {
      case Some(PossDupFlagField(true)) =>
        if (msgIn.header.origSendingTimeField.isEmpty) {
          val msg = "OrigSendingTime missing and PosDupFlag is Y"
          logger.info(s"[${fixSession.idStr}] Rejecting message ${msgIn.header.msgSeqNumField} due to [$msg]")
          fixSession.sendRejectMessage(msgIn.header.msgSeqNumField.value, true,SessionRejectReasonField(SessionRejectReasonField.RequiredTagMissing),
            TextField(msg))
          Some(SessNoChangeEventConsumed)
        } else if (isResentDuplicate(fixSession, msgIn)) {
          if (isSendingTimeErrorOnResentDup(fixSession, msgIn)) {
            // The spec here says wait at most 2 seconds for the logout and then disconnect
            // which is in the test spec, but not the main spec which mentioned 2*heatbeat. 2f of the test spec
            fixSession.incrementTheirSeq
            Some(InitiateLogoutProcess("SendingTime acccuracy problem", true, Some(2000L)))
          } else {
            // Its a resent duplicate, do nothing with it ever.
            logger.debug(s"s[${fixSession.idStr}] ${msgIn.header.msgSeqNumField} Discarding duplicate message")
            Some(SessNoChangeEventConsumed)
          }
        } else {
          // so, dup flag is set, but its seq num >= expected, so process it
          None
        }
      case _ =>
        handleSequenceNumberTooLow(fixSession) orElse handleSequenceNumberTooHigh(fixSession) orElse
          handleClocksInSync(fixSession, msgIn) orElse None
    }
  }

  protected[fixstate] def receiveFixMsg(sfSession: SfSession, msgIn: SfMessage, actionCallback: SfAction => Unit): Option[SfSessState] = {
    // This means we are not currently in a state where we expect to handle a fix message
    // so its an error
    None
  }

  protected[fixstate] def receiveSocketEvent(sfSession: SfSession, socketEvent: SfSessionSocketEvent): Option[SfSessState] = {
    // This means we are not currently in a state where we expect to handle a fix message
    // so its an error
    socketEvent match {
      case SfSessionSocketCloseEvent =>
        sfSession.sessionType match {
          case SfAcceptor => Some(AwaitingConnection)
          case SfInitiator => Some(DetectBrokenNetworkConnection)
        }
      case SfSessionServerSocketCloseEvent =>
        Some(DetectBrokenNetworkConnection)
      case ev =>
        logger.error(s"[${sfSession.idStr}] In state [${stateName}], received unexpected event [${ev.name}], ignoring")
        None
    }
  }

  protected[fixstate] def receiveControlEvent(fixSession: SfSession, event: SfSessionControlEvent): Option[SfSessState] = {
    event match {
      case SfControlForceLogoutAndClose(reason, pausePriorToSocketCloseMs:Option[Long]) =>
        if (isSessionSocketOpen) Some(InitiateLogoutProcess(reason, true, pausePriorToSocketCloseMs))
        else Some(DisconnectSocketNow)
      case SfControlTimeoutFired(id, durationMs) =>
        logger.debug(s"[${fixSession.idStr}] Timeout fired (id=[$id], duration=[$durationMs]ms), but no longer relevant to this state.")
        Some(SessNoChangeEventConsumed)
      case ev@_ =>
        logger.error(s"[${fixSession.idStr}] Failed to handle Control event [${ev.getClass.getName}]")
        None
    }
  }

}
