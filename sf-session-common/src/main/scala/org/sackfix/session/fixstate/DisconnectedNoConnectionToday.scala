package org.sackfix.session.fixstate

import org.sackfix.session._

object DisconnectedNoConnectionToday extends SfSessState(1, "Disconnected No Connection Today",
      initiator = true, acceptor = true, isSessionOpen=false, isSessionSocketOpen=false) {
  override protected[fixstate] def receiveSocketEvent(sfSession:SfSession, socketEvent:SfSessionSocketEvent) : Option[SfSessState] ={
    socketEvent match {
      case SfSessionServerSocketOpenEvent=> Some(AwaitingConnection)
      case SfSessionNetworkConnectionEstablishedEvent=>Some(InitiateConnection)
      case _ => super.receiveSocketEvent(sfSession, socketEvent)
    }
  }
}
