package org.sackfix.socket

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.sackfix.codec.DecodingFailedData
import org.sackfix.common.message.SfMessage
import org.sackfix.field.{SessionRejectReasonField, TextField}
import org.sackfix.session.SfSessionActor.{ConnectionEstablishedMsgIn, SfSessionActorCommand}
import org.sackfix.session.{SfSessOutEventRouter, SfSessionActor}

import scala.collection.mutable.ArrayBuffer

object StubSessionActor {
  def apply(received : ArrayBuffer[String],failedMsgs : ArrayBuffer[String]): Behavior[SfSessionActorCommand] =
    Behaviors.setup(context => new StubSessionActor(context, received, failedMsgs))
}

class StubSessionActor(context: ActorContext[SfSessionActorCommand],
                       received : ArrayBuffer[String],failedMsgs : ArrayBuffer[String]) extends AbstractBehavior[SfSessionActorCommand](context) {
  override def onMessage(msg: SfSessionActorCommand): Behavior[SfSessionActorCommand] = {
    msg match {
      case SfSessionActor.FixMsgIn(fixMsg: SfMessage) =>
        //println("Got handed a message :"+fixMsg.fixStr)
        received += fixMsg.fixStr
      case SfSessionActor.SendRejectMessageOut(refSeqNum: Int, reason: SessionRejectReasonField, explanation: TextField) =>
        println("FAILED:" + reason.toString() + "\nExplained: " + explanation)
        failedMsgs += explanation.value
      case ConnectionEstablishedMsgIn(outEventRouter: SfSessOutEventRouter, fixMsg: Option[SfMessage],
      decodingFailedData: Option[DecodingFailedData]) =>
        fixMsg.foreach(received += _.fixStr)
    }
    Behaviors.same
  }
}