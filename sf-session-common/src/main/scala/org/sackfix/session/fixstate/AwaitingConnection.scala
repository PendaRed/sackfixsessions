package org.sackfix.session.fixstate

import org.sackfix.session._
import org.sackfix.session.fixstate.DisconnectedConnectionToday.logger
import org.slf4j.LoggerFactory

/**
  * Opened server socket, waiting for a client.
  */
object AwaitingConnection extends SfSessState(4, "Awaiting Connection",
    initiator = false, acceptor = true, isSessionOpen=false, isSessionSocketOpen=false) {

  override protected[fixstate] def receiveSocketEvent(sfSession: SfSession, socketEvent: SfSessionSocketEvent): Option[SfSessState] = {
    socketEvent match {
      case SfSessionNetworkConnectionEstablishedEvent => Some(NetworkConnnectionEstablished)
      case _ => super.receiveSocketEvent(sfSession, socketEvent)
    }
  }

}
