package org.sackfix.session

import akka.actor.ActorRef
import org.sackfix.session.SfSessionActor.FixActorSystemCloseDown

/**
  * Created by Jonathan in 2016.
  */
class SfSessionCache {
  val cache = scala.collection.mutable.Map.empty[String, ActorRef]

  /**
    * Add the session, and also returns it
    */
  def add(s:SfSessionId, sessionActor:ActorRef): ActorRef = {
    cache(s.id) = sessionActor
    sessionActor
  }
  def get(s:SfSessionId): Option[ActorRef] = {
    cache.get(s.id)
  }

  def close = {
    cache.values.foreach(_ ! FixActorSystemCloseDown)
    cache.clear()
  }
}
