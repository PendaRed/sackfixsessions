package org.sackfix.session

import akka.actor.{Actor, Props}
import akka.io.Tcp.{Event, Write}
import akka.util.ByteString

class OutbountActorStub extends Actor {
  var lastMsg: Option[String] = None

  def receive = {
    case Write(msg: ByteString, ev: Event) =>
      lastMsg = Some(msg.toString)
  }
}
object OutbountActorStub {
  def props(): Props = Props(new OutbountActorStub())
}