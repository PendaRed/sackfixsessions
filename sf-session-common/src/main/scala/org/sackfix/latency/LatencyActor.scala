package org.sackfix.latency

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.sackfix.latency.LatencyActor._

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import scala.collection.mutable

/**
  * Created by Jonathan during 2017.
  */
object LatencyActor {
  def apply(maxNumCorrelationIds:Int): Behavior[LatencyCommand] =
    Behaviors.setup(context => new LatencyActor(context, maxNumCorrelationIds))

  sealed trait LatencyCommand
  case class RecordLatencyMsgIn(aggregationTag:String, correlationId:String, stageName:String, timeStampNanos:Long, removeAnyPreviousRecord:Boolean= false) extends LatencyCommand
  case class RecordMsgLatencyMsgIn(seqNum:Int, stageName:String, timeStampNanos:Long, removeAnyPreviousRecord:Boolean= false) extends LatencyCommand
  case class RecordMsgLatenciesMsgIn(messages:List[RecordMsgLatencyMsgIn]) extends LatencyCommand
  case class ServeLatencyReportMsgIn(replyTo: ActorRef[ServeLatencyReportReply]) extends LatencyCommand
  // Add a possibly last message here to - less work passing msgs about
  case class LogCorrelationMsgIn(additionalLog:Option[RecordMsgLatencyMsgIn], correlationId:String, removeDate:Boolean) extends LatencyCommand
  case class ServeLatencyReportReply(report:String, tstamp:Long)
}
class LatencyActor(context: ActorContext[LatencyCommand], val maxNumCorrelationIds:Int) extends AbstractBehavior[LatencyCommand](context) {

  // aggregation =>  stage->time,count
  val lookupByAggregation = mutable.Map.empty[String, mutable.Map[String, (Long, Int)]]

  // correlation =>  stage->time, count
  val lookupByCorrelation = mutable.Map.empty[String, mutable.Map[String, (Long, Int)]]
  val correlationStartTime = mutable.Map.empty[String, Long]

  // stage =>  aggregation->time,count
  val lookupByStage = mutable.Map.empty[String, mutable.Map[String, (Long, Int)]]

  // Correlations are unique and will grow, so need to be able to time them out oldest first
  val fifoCorrelations: mutable.Queue[String] = mutable.Queue.empty[String]

  val df = new SimpleDateFormat("HH:mm:ss.SSS")

  override def onMessage(msg: LatencyCommand): Behavior[LatencyCommand] = {
    msg match {
      case info:RecordLatencyMsgIn => recordTStampInfo(info)

      case infos:RecordMsgLatenciesMsgIn =>
        infos.messages.foreach(i=>
          recordTStampInfo(RecordLatencyMsgIn("SF", ""+i.seqNum, i.stageName, i.timeStampNanos)))

      case RecordMsgLatencyMsgIn(seqNum:Int,stageName:String,timeStampNanos:Long, removeAnyPreviousRecord:Boolean) =>
        recordTStampInfo(RecordLatencyMsgIn("SF", ""+seqNum, stageName, timeStampNanos, removeAnyPreviousRecord))

      case LogCorrelationMsgIn(additionalLog, correlationId, removeData:Boolean) =>
        additionalLog match {
          case Some(RecordMsgLatencyMsgIn(seqNum:Int,stageName:String,timeStampNanos:Long,removeAnyPreviousRecord:Boolean)) =>
            recordTStampInfo(RecordLatencyMsgIn("SF", ""+seqNum, stageName, timeStampNanos,removeAnyPreviousRecord))
          case _ =>
        }
        val m = getCorrelationInfo(correlationId)
        context.log.debug(m)
        if (removeData) removeCorrelationData(correlationId)
      case m:ServeLatencyReportMsgIn =>
        m.replyTo ! ServeLatencyReportReply(getCorrelationDump, System.nanoTime())
    }
    Behaviors.same
  }

  def dump(): Unit = {
//    val allStages = lookupByStage.keySet.toList.sorted
//    val allCorrelationIds = lookupByCorrelation.keySet
//    val allAggregations = lookupByAggregation.keySet
    println(
      s"""
         |$getCorrelationDump
         |
         |$getStageDump
         |
         |$getAggregationDump
       """.stripMargin)
  }

  def getCorrelationInfo(correlationId:String):String = {
    val startTime = correlationStartTime.getOrElse(correlationId, System.nanoTime())
    var prevTime = 0

    val details = lookupByCorrelation.get(correlationId) match {
      case None=> ""
      case Some(lookupByStage:mutable.Map[String, (Long, Int)]) =>
        // Sorry, wanted to play..so I did.
        s"$correlationId : "+ {
          lookupByStage.keySet.toList.sorted.map( (stage:String) => {
            val totCnt = lookupByStage(stage)
            if (totCnt._2 > 0) {
              f"$stage ${(totCnt._1 / totCnt._2)/1000}micros"
            } else s"$stage No Info"
          }).mkString(", ") + " Durations: " +
            lookupByStage.keySet.toList.sorted.foldLeft( Tuple2("",0) )( (acc:Tuple2[String, Int], stage:String) => {
              val totCnt = lookupByStage(stage)
              if (totCnt._2 > 0) {
                val averageMicros:Int = ((totCnt._1 / totCnt._2)/1000).toInt
                val duration = averageMicros-acc._2
                (acc._1+s",$duration",averageMicros)
              } else (acc._1+",-", acc._2)
            })._1
        }
    }
    details
  }

  def getCorrelationDump :String = dump("Correlations", fifoCorrelations.toList, lookupByCorrelation)

  def getStageDump :String = dump("Stages", lookupByStage.keySet.toList.sorted, lookupByStage)

  def getAggregationDump :String = dump("Aggregations", lookupByAggregation.keySet.toList.sorted, lookupByAggregation)


  private def dump(title:String, keys:List[String], dataLookup:mutable.Map[String, mutable.Map[String, (Long, Int)]]) :String = {
    val str =
      keys.map(mainId => {
        s" $mainId ${
          dataLookup.get(mainId) match {
            case Some(innerMap) =>
              innerMap.keySet.toList.sorted.map((innerID: String) => {
                val totCnt = innerMap(innerID)
                if (totCnt._2>0) {
                  f"$innerID ${(totCnt._1 / totCnt._2)/1000}micros"
                } else s"$innerID No Info"
              }).mkString(", ")
            case None => ""
          }
        }"
      }).mkString("\n")

    s"$title @ [${LocalDateTime.now.toString}]\n" + str

  }

  def removeCorrelationData(correlationId:String): Option[Long] = {
    lookupByCorrelation.remove(correlationId)
    correlationStartTime.remove(correlationId)
  }

  def recordTStampInfo(info:RecordLatencyMsgIn): Unit = {
    if (info.removeAnyPreviousRecord) {
      removeCorrelationData(info.correlationId)
    }
    if (!correlationStartTime.contains(info.correlationId)) {
      while (maxNumCorrelationIds<=fifoCorrelations.size) {
        val removeId = fifoCorrelations.dequeue()
        removeCorrelationData(removeId)
      }
      fifoCorrelations += info.correlationId
      correlationStartTime(info.correlationId) = info.timeStampNanos
    }


    val startTime = correlationStartTime.getOrElse(info.correlationId, System.nanoTime())
    val duration = info.timeStampNanos-startTime
    val aggregateDuration = lookupByAggregation.getOrElse(info.aggregationTag,createNewAggregation(info, duration))
    val aggregateStage = lookupByStage.getOrElse(info.stageName,createNewStage(info, duration))
    val correlation = lookupByCorrelation.getOrElse(info.correlationId, createNewCorrelation(info, duration))


    aggregateDuration(info.stageName) = add(duration,aggregateDuration.getOrElse(info.stageName,(0L,0)))
    aggregateStage(info.aggregationTag) = add(duration,aggregateStage.getOrElse(info.aggregationTag,(0L,0)))
    correlation(info.stageName) = add(duration, correlation.getOrElse(info.stageName,(0L,0)))
  }

  private def add(duration:Long, value:(Long, Int)) : (Long,Int) = {
    (value._1 + duration, value._2 +1)
  }

  private def createNewAggregation(info:RecordLatencyMsgIn, duration:Long):mutable.Map[String, (Long,Int)] = {
    val newVal = mutable.Map(info.stageName -> (0L, 0))
    lookupByAggregation(info.aggregationTag) = newVal
    newVal
  }
  private def createNewStage(info:RecordLatencyMsgIn, duration:Long):mutable.Map[String, (Long,Int)] = {
    val newVal = mutable.Map(info.aggregationTag -> (0L, 0))
    lookupByStage(info.stageName) = newVal
    newVal
  }
  private def createNewCorrelation(info:RecordLatencyMsgIn, duration:Long):mutable.Map[String, (Long,Int)] = {
    val newVal = mutable.Map(info.stageName -> (0L, 0))
    lookupByCorrelation(info.correlationId) = newVal
    newVal
  }
}
