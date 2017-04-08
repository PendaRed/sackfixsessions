package org.sackfix.session.fixstate

import org.sackfix.common.message.SfMessage
import org.sackfix.fix44.SequenceResetMessage
import org.sackfix.session.fixstate.AwaitingConnection.{logger, stateName}
import org.sackfix.session._

/**
  * Created by Jonathan on 01/01/2017.
  */
case class AwaitingProcessingResponseToResendRequest(val beginSeqNum:Int, val endSeqNum:Int) extends SfSessState(12, "Awaiting Processing Response To Resend Request",
    initiator = true, acceptor = true, isSessionOpen=true, isSessionSocketOpen=true) {
  var replaySeqNum = beginSeqNum

  override protected[fixstate] def receiveFixMsg(fixSession:SfSession, msgIn:SfMessage,actionCallback:SfAction=>Unit): Option[SfSessState] = {
    // So, we expect lower sequence numbers than the session currently has, but we expect them to increase
    // Any gapfill  may set the seq num to be within the sequence.
    // eg resend message 5 and 6 please, sessions nextSeqNum=7.
    // get SeqReset seqNum=5, gapFill=Y, NewSeqNum=6
    //     fix message seqNum=6
    // Note, if the GapFill has a lower seqNum than nextSeqNum just ignore it.
    val msgSeqNum = msgIn.header.msgSeqNumField.value
    if (msgSeqNum==replaySeqNum) {
      msgIn.body match {
        case reset:SequenceResetMessage =>
          val isGapFill:Boolean = reset.gapFillFlagField.map(f=>f.value).getOrElse(false)
          val newSeq = reset.newSeqNoField.value
          if (newSeq<replaySeqNum) {
            // Spec suggests this should be treated as 'a serious error'.
            logger.error(s"[{${fixSession.idStr}}] During a resend request I have received a SeqRest-GapFill attempting to lower the sequence number to [$newSeq], expected next seq num of [$replaySeqNum].")
          } else {
            replaySeqNum = newSeq
            if (newSeq>fixSession.getExpectedTheirSeqNum) {
              // erm, reset the session and move to normal working..
              fixSession.nextTheirSeqNum = newSeq
            }
          }
        case msgBody =>
          if (!fixSession.isMessageFixSessionMessage(msgBody.msgType)) {
            // send it back to the business
            actionCallback(SfActionBusinessMessage(msgIn))
          }
      }
      replaySeqNum+=1
      // If the seqnum is >= what the session expects then the replay has finished.
      if (replaySeqNum>=fixSession.nextTheirSeqNum) {
        // When we asked for a replay, say they send msg 100, we asked for 90 to 100.  So this is 100.
        // Effectively we got msg 100 twice, so the session should now expect msg 101.
        fixSession.incrementTheirSeq
        Some(ActiveNormalSession)
      } else None
    } else {
      // Spec if pretty useless here.  ie the replay is replaying too low it suggests you ignore
      // it as a duplicate, or too high then....erm?
      if (msgSeqNum>replaySeqNum) {
        logger.error(s"[${fixSession.idStr}] During processing of resend request they send seqNum [$msgSeqNum] when I expected [$replaySeqNum], so closing the seesion.")
        Some(InitiateLogoutProcess(s"During reset seq num received was [$msgSeqNum], expected [$replaySeqNum]"))
      } else {
        logger.info(s"[${fixSession.idStr}] During processing of resend request they send seqNum [$msgSeqNum] when I expected [$replaySeqNum], ignoring this message as a duplicate.")
        None
      }
    }
  }

}
