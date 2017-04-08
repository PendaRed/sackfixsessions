package org.sackfix.session

import java.time.format.{DateTimeFormatter, DateTimeParseException}

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field._
import org.sackfix.fix44.{ExecutionReportMessage, InstrumentComponent}
import org.sackfix.session.SfSessionActor._
import org.sackfix.session.fixstate.MessageFixtures
import org.sackfix.session.heartbeat.SfHeartbeaterActor.AddListenerMsgIn
import org.scalatest._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._


/**
  * Created by Jonathan during 2016.
  */
class SfSessionActorTimingSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll  {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A SfSessionActor" should {

    "get timed to deal with messages in a state machine" in {
      val numInTest = 10000
      val msgs = ArrayBuffer.empty[SfMessage]
      for (testNum <- 0 to numInTest + 400) {
        msgs += MessageFixtures.newOrderSingleNowTime(2+testNum, "ClientOrderId")
      }
      for (tstNum <- 0 to 3) {
        val probe1 = TestProbe()
        val tcpProbe = TestProbe()
        val store = new SfMessageStoreStub()
        val sessionId = SfSessionId(beginString = "Fix4.2",
          senderCompId = "TargFGW",
          targetCompId = "SendFGW")

        val sessionActor = system.actorOf(SfSessionActor.props(SfAcceptor, Some(store), sessionId, 20, probe1.ref, None, new SessionOpenTodayStoreStub))
        val sessOutRouter = new SfSessOutEventRouterTimingStub(sessionActor, tcpProbe.ref)

        val logonMessage = MessageFixtures.Logon
        sessionActor ! ConnectionEstablishedMsgIn(sessOutRouter, Some(logonMessage), None)

        // so, it should register with the heartbeater
        val hbListener = probe1.expectMsgType[AddListenerMsgIn](1200 millis)

        var testNum=0
        msgs.foldLeft(0)((testNum, msg: SfMessage) => {
          if (testNum == 200) sessOutRouter.startTimer(numInTest)
          sessionActor ! FixMsgIn(msg)
          if (tstNum == 1 || tstNum == 3) msg.fixStr
          if (tstNum == 2 || tstNum == 3) msg.toString()
          testNum + 1
        })
        val dur = sessOutRouter.waitForTestToStop()
        Thread.sleep(1000)
        if (tstNum==1 || tstNum==3) println("With fixStr")
        if (tstNum==2 || tstNum==3) println("With toString")
        println(s"Duration ${(dur/sessOutRouter.actualMessages)/1000} microseconds per call")
        println(s"$dur nano seconds for ${sessOutRouter.actualMessages} calls")
      }
    }
  }
class SfSessOutEventRouterTimingStub(override val sfSessionActor: ActorRef, override val tcpActor:ActorRef) extends SfSessOutEventRouter {
  val remoteHostDebugStr ="PerfTest"
  var msgsToBusiness:Int=0
  var actualMessages = 0
  var expectedNumBizMsgs =0
  var fixMsgsCnt = 0

  var startTimerNanos :Long= System.nanoTime()
  var stopTimerNanos :Long= Long.MaxValue

  def startTimer(numBizMsgs:Int) = synchronized {
    startTimerNanos = System.nanoTime()
    expectedNumBizMsgs = numBizMsgs
    msgsToBusiness=0
  }

  def waitForTestToStop(): Long = {
    while (msgsToBusiness<expectedNumBizMsgs) {
      Thread.sleep(100)
    }
    stopTimerNanos - startTimerNanos
  }
  override def confirmCorrectTcpActor(checkTcpActor: ActorRef):Boolean = true
  override def logOutgoingFixMsg(fixMsgStr: String) = fixMsgsCnt += 1
  override def informBusinessLayerSessionIsOpen = msgsToBusiness =0
  override def informBusinessLayerSessionIsClosed = {}
  override def informBusinessMessageArrived(msg:SfMessage) = synchronized {
    if (msgsToBusiness==expectedNumBizMsgs) {
      stopTimerNanos = System.nanoTime()
      actualMessages = msgsToBusiness
    }
    msgsToBusiness+=1
  }
  override def informBusinessMessageAcked(correlationId:String) = {}
  override def closeThisFixSessionsSocket = {}
  override def informBusinessRejectArrived(fixMsg: SfMessage): Unit = {}
}
}
