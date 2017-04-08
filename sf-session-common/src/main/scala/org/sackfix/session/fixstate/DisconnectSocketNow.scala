package org.sackfix.session.fixstate

import org.sackfix.session._

/**
  * Transition state, action is close the socket
  */
object DisconnectSocketNow extends SfSessState(19,"Disconnect Socket",
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
