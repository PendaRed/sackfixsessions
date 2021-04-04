package org.sackfix.session.heartbeat

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.sackfix.session.heartbeat.SfHeartbeaterActor._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * The listener will be called every durationMs - typically every second.
  * Its up to the listener to decide if its own timeout etc needs to fire
  */
trait SfHeartbeatListener {
  def heartBeatFired()
}


/**
  * Simply tick every period and call back to the listeners
  * WHY NOT DO AS AN ACTOR?  Maybe I should have.
  */
object SfHeartbeaterActor {
  def apply(durationMs: Long): Behavior[HbCommand] =
    Behaviors.setup(context => new SfHeartbeaterActor(context, durationMs))

  sealed trait HbCommand

  case object StartBeatingMsgIn extends HbCommand

  case object StopBeatingMsgIn extends HbCommand

  case class AddListenerMsgIn(heartbeatConsumer: SfHeartbeatListener) extends HbCommand

  case class RemoveListenerMsgIn(heartbeatConsumer: SfHeartbeatListener) extends HbCommand

  case class HeartbeatFiredMsgOut()

}

/**
  * When people register with me for events that fire they pass in their own listeners.  These listeners will
  * be executed within my Thread - so they are in charge of Telling other actors to do stuff.
  *
  * @param durationMs The time between interval tics - ie about a second is good.  This is NOT
  *                   the duration of the heartbeat, it is the finest granularity of ticking
  */
class SfHeartbeaterActor(context: ActorContext[HbCommand], val durationMs: Long) extends AbstractBehavior[HbCommand](context) {
  val heartbeater = new SfHeartbeater(durationMs)

  override def onMessage(msg: HbCommand): Behavior[HbCommand] = {
    msg match {
      case StartBeatingMsgIn => heartbeater.start()
      case StopBeatingMsgIn => heartbeater.stop()
      case AddListenerMsgIn(listener) => heartbeater.listeners += listener
      case RemoveListenerMsgIn(listener) => heartbeater.listeners -= listener
    }
    Behaviors.same
  }
}

/**
  * This was written first, flung an actor on the front
  *
  * @param durationMs Time between internal tics.  Each listener gets messaged when it fires...
  */
class SfHeartbeater(val durationMs: Long) extends Runnable {
  val listeners = ArrayBuffer.empty[SfHeartbeatListener]
  private var theThread: Option[Thread] = None
  private var origName: String = ""

  def start(): Unit = {
    theThread match {
      case None =>
        val myTh = new Thread(this)
        origName = myTh.getName
        theThread = Some(myTh)
        myTh.setName("SackFixHeartbeat")
        myTh.setDaemon(true)
        myTh.start()
      case _ =>
    }
  }

  def stop(): Unit = {
    theThread match {
      case None =>
      case Some(myTh) =>
        theThread = None
    }
  }

  override def run(): Unit = {
    doTheTimerForever()
  }

  @tailrec
  private final def doTheTimerForever(): Unit = {
    theThread match {
      case None => // Stop.
        theThread.foreach(_.setName(origName))
      case Some(thread) =>
        Thread.sleep(durationMs)
        listeners.foreach(_.heartBeatFired())
        doTheTimerForever()
    }
  }
}
