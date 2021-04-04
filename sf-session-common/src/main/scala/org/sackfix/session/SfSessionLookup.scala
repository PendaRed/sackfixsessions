package org.sackfix.session

import akka.actor.typed.ActorRef
import org.sackfix.common.message.SfMessageHeader
import org.sackfix.session.SfSessionActor.SfSessionActorCommand
import org.slf4j.LoggerFactory

/**
  * Every socket will have an associated lookup in order to validate the header fields
  * match one of the assocated sessions - an initiator only ever has one per lookup,
  * whereas an acceptor will have many potential clients who can connect
  */
class SfSessionLookup() {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val sessionCache = new SfSessionCache

  private def closeDown = {
    sessionCache.close
  }

  /**
    * Validate the message, and responds with a reject, or other message as required, or None if there
    * is no reply
    *
    * @param incomingHeader     The incoming message header
    * @param socketConnnectHost Useful for debug
    * @return The reply or None
    */
  private def validateSessionDetails(incomingHeader: SfMessageHeader, socketConnnectHost: String): Option[ActorRef[SfSessionActorCommand]] = {
    val sessionId = SfSessionId(incomingHeader)
    // create a session in the cache if we can
    sessionCache.get(sessionId) orElse {
      logger.error(s"No session exists for ${sessionId.id}")
      None
    }
  }

  def findSession(header: SfMessageHeader): Option[ActorRef[SfSessionActorCommand]] = {
    val sessionId = SfSessionId(header)
    sessionCache.get(sessionId)
  }
  def findSession(sessionId: SfSessionId): Option[ActorRef[SfSessionActorCommand]] = sessionCache.get(sessionId)

  def getAllSessionActors : Iterable[ActorRef[SfSessionActorCommand]] = {
    sessionCache.cache.values
  }
}
