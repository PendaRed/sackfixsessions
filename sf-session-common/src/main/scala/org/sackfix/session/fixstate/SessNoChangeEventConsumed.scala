package org.sackfix.session.fixstate

/**
  * Created by Jonathan during 2017.
  */
case object SessNoChangeEventConsumed extends SfSessState(-1,"Only used to consume a message/event but leave state unchanged",
  initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true)
