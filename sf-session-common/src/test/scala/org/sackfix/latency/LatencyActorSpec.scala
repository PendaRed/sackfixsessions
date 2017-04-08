package org.sackfix.latency

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.sackfix.latency.LatencyActor.{LogCorrelationMsgIn, RecordLatencyMsgIn, ServeLatencyReportMsgIn, ServeLatencyReportReply}
import org.sackfix.session.heartbeat.SfHeartbeater
import org.scalatest.{BeforeAndAfterAll, FlatSpec, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Jonathan during 2017.
  */
class LatencyActorSpec extends TestKit(ActorSystem("LatencyTests")) with ImplicitSender
      with WordSpecLike with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The Latency Actor" must {
    "store simple latencies" in {
      val hb = new SfHeartbeater(1000)

      val recorder = system.actorOf(LatencyActor.props(1))
      val now = System.nanoTime()
      recorder ! RecordLatencyMsgIn("agg", "seqnum1", "arrived", now - 100*1000000)
      recorder ! RecordLatencyMsgIn("agg", "seqnum1", "decoder", now - 50*1000000)
      recorder ! RecordLatencyMsgIn("agg", "seqnum1", "typed", now - 25*1000000)

      recorder ! ServeLatencyReportMsgIn

      val rep: AnyRef = receiveOne(1000 millis)
      assert(rep != null)
      val report = rep.asInstanceOf[ServeLatencyReportReply]
      println(report.report)
      assert(stripTime(report.report) ==
        "Correlations @ [REMOVED]\n seqnum1 arrived 0micros, decoder 50000micros, typed 75000micros")
    }
    "evict too many correlationIds" in {
      val hb = new SfHeartbeater(1000)

      val recorder = system.actorOf(LatencyActor.props(2))
      val now = System.nanoTime()
      recorder ! RecordLatencyMsgIn("agg", "seqnum1", "arrived", now - 100000000)
      recorder ! RecordLatencyMsgIn("agg", "seqnum2", "arrived", now - 50000000)
      recorder ! RecordLatencyMsgIn("agg", "seqnum3", "arrived", now - 25000000)

      recorder ! ServeLatencyReportMsgIn

      val rep: AnyRef = receiveOne(1000 millis)
      assert(rep != null)
      val report = rep.asInstanceOf[ServeLatencyReportReply]
      println(report.report)
      assert(stripTime(report.report) == "Correlations @ [REMOVED]\n seqnum2 arrived 0micros\n seqnum3 arrived 0micros")
    }
    "preserve original order of correlations" in {
      val hb = new SfHeartbeater(1000)

      val recorder = system.actorOf(LatencyActor.props(20))
      val now = System.nanoTime()
      recorder ! RecordLatencyMsgIn("agg", "Zeta", "arrived", now - 100000000)
      recorder ! RecordLatencyMsgIn("agg", "Alpha", "arrived", now - 50000000)
      recorder ! RecordLatencyMsgIn("agg", "Alpha", "done", now - 25000000)
      recorder ! RecordLatencyMsgIn("agg", "Zeta", "done", now - 20000000)

      recorder ! ServeLatencyReportMsgIn

      val rep: AnyRef = receiveOne(1000 millis)
      assert(rep != null)
      val report = rep.asInstanceOf[ServeLatencyReportReply]
      println(report.report)
      assert(stripTime(report.report) == "Correlations @ [REMOVED]\n Zeta arrived 0micros, done 80000micros\n Alpha arrived 0micros, done 25000micros")
    }
    "can log one correlation msg" in {
      val hb = new SfHeartbeater(1000)

      val recorder = system.actorOf(LatencyActor.props(20))
      val now = System.nanoTime()
      recorder ! RecordLatencyMsgIn("agg", "msg1", "arrived", now - 100000000)
      recorder ! RecordLatencyMsgIn("agg", "msg1", "decoded", now - 75000000)
      recorder ! RecordLatencyMsgIn("agg", "msg1", "msgmade", now - 50000000)
      recorder ! RecordLatencyMsgIn("agg", "msg1", "handled", now - 25000000)

      recorder ! LogCorrelationMsgIn(None, "msg1", true)
    }
  }

  def stripTime(str:String) :String = {
    val pos = str.indexOf('[')
    val pos2 = str.indexOf(']',pos)
    if (pos>0 && pos2>pos) {
      str.substring(0,pos+1)+"REMOVED"+str.substring(pos2)
    } else str
  }

}
