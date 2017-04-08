package org.sackfix.boostrap

/**
  * This will be sent up to your gardian actor if there is a problem.
  */
case class SystemErrorNeedsDevOpsMsg(val humanReadableMessageForDevOps : String)
