package org.sackfix.session

import akka.actor.typed.ActorRef
import org.sackfix.session.SfSessionActor.{FixActorSystemCloseDown, SfSessionActorCommand}

/**
  * Created by Jonathan in 2016.
  */
class SfSessionCache {
  val cache = scala.collection.mutable.Map.empty[String, ActorRef[SfSessionActorCommand]]

  /**
    * Add the session, and also returns it
    */
  def add(s:SfSessionId, sessionActor:ActorRef[SfSessionActorCommand]): ActorRef[SfSessionActorCommand] = {
    cache(s.id) = sessionActor
    sessionActor
  }
  def get(s:SfSessionId): Option[ActorRef[SfSessionActorCommand]] = {
    cache.get(s.id)
  }

  def close = {
    cache.values.foreach(_ ! FixActorSystemCloseDown)
    cache.clear()
  }
}
