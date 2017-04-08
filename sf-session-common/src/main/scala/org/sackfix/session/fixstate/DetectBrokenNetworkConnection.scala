package org.sackfix.session.fixstate

import org.sackfix.session._

/**
  * Transition state, got broken socket, so ensure its all closed up and go to disconnected
  */
object DetectBrokenNetworkConnection extends SfSessState(3, "Detect Broken Network Connection",
    initiator = true, acceptor = true, isSessionOpen=false, isSessionSocketOpen=false) {
  override protected[fixstate] def stateTransitionAction(fixSession:SfSession, ev:SfSessionEvent) : List[SfAction]=
    List(SfActionCloseSocket())

  override protected[fixstate] def nextState(sfSession:SfSession) : Option[SfSessState] = {
    sfSession.sessionType match {
      case SfAcceptor => Some(AwaitingConnection)
      case SfInitiator => Some(DisconnectedConnectionToday)
    }
  }

}
