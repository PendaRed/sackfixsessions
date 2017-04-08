package org.sackfix.codec

import org.sackfix.field.{SessionRejectReasonField, TextField}
import org.sackfix.session.SfSessionId

/**
  * Created by Jonathan during 2017.
  */
case class DecodingFailedData(val sessionId:Option[SfSessionId],
                              val referenceSeqNum:Int,
                              val rejectReason : SessionRejectReasonField,
                              val description: TextField)
