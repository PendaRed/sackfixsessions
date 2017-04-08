package org.sackfix.session.fixstate

import org.sackfix.session._

/**
  * Created by Jonathan on 01/01/2017.
  */
object DisconnectedConnectionToday extends SfSessState(2, "Disconnected Connection Today",
    initiator = true, acceptor = true, isSessionOpen=false, isSessionSocketOpen=false) {

  override protected[fixstate] def stateTransitionAction(fixSession: SfSession, ev: SfSessionEvent): List[SfAction] = {
    val expectedSeqNum = fixSession.getExpectedTheirSeqNum
    val lastTheirSeqNum = fixSession.lastTheirSeqNum
    if (expectedSeqNum!=lastTheirSeqNum) {
      logger.error(s"[${fixSession.idStr}] You should consider updating the values in application.conf for ResetMyNextSeqNumTo and ResetTheirNextSeqNumTo and restarting to match their expected session values.")
    }

    List.empty
  }

  override protected[fixstate] def receiveSocketEvent(sfSession:SfSession, socketEvent:SfSessionSocketEvent) : Option[SfSessState] ={
    socketEvent match {
      case SfSessionServerSocketOpenEvent=> Some(AwaitingConnection)
      case SfSessionNetworkConnectionEstablishedEvent=>Some(InitiateConnection)
      case _ => super.receiveSocketEvent(sfSession, socketEvent)
    }
  }
}
