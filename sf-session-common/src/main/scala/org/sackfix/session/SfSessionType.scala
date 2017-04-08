package org.sackfix.session

import org.sackfix.session.fixstate.{AwaitingConnection, InitiateConnection, NetworkConnnectionEstablished}

/**
  * Created by Jonathan during 2017.
  */
trait SfSessionType {
}
case object SfAcceptor extends SfSessionType {
}
case object SfInitiator extends SfSessionType {
}