package org.sackfix.session

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.sackfix.field._
import org.sackfix.fix44.{ExecutionReportMessage, InstrumentComponent}
import org.sackfix.session.SfSessionActor._
import org.sackfix.session.fixstate.MessageFixtures
import org.sackfix.session.heartbeat.SfHeartbeaterActor.{AddListenerMsgIn, HbCommand}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import scala.concurrent.duration._
// adds support for actors to a classic actor system and context
import akka.actor.typed.scaladsl.adapter._
/**
  * Created by Jonathan during 2016.
  */
class SfSessionActorSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A SfSessionActor" should {

    "handle a normal session" in {
      val probe1 = testKit.createTestProbe[HbCommand]
      val store = new SfMessageStoreStub()
      val sessionId = SfSessionId(beginString = "Fix4.2",
        senderCompId = "TargFGW",
        targetCompId = "SendFGW")

      val tcpProbeRef = testKit.createTestProbe().ref

      val sessionActor = testKit.spawn(SfSessionActor(SfAcceptor, Some(store), sessionId, 20, probe1.ref, None, new SessionOpenTodayStoreStub))
      val sessOutRouter = new SfSessOutEventRouterStub(sessionActor, tcpProbeRef.toClassic)

      val logonMessage = MessageFixtures.Logon
      sessionActor ! ConnectionEstablishedMsgIn(sessOutRouter, Some(logonMessage), None)

      // so, it should register with the heartbeater
      val hbListener = probe1.expectMessageType[AddListenerMsgIn](200.millis)

      Thread.sleep(100)
      // It replies with a login message
      assert(sessOutRouter.fixMessages.size == 1)

      val expectedReplyLogon = "8=Fix4.2\u00019=75\u000135=A\u000149=TargFGW\u000156=SendFGW\u000134=1\u000152=TSTAMPREMOVED\u000198=0\u0001108=20\u0001141=Y\u000110=\u0001"
      assert(expectedReplyLogon == stripTimeAndCheckSum(sessOutRouter.fixMessages(0)))
      Thread.sleep(100)

      // Sequence number should be 2 now, and it should not store session messages, only business ones
      store.readMessage(sessionId, 1) match {
        case None => // pass
        case Some(fixStr) => fail("Expected the Logon NOT to be stored")
      }
      assert(store.getInfo(sessionId).mySeq == 2)
      assert(store.getInfo(sessionId).theirSeq == 2)

      val orderSingle = MessageFixtures.newOrderSingleNowTime(2, "ClientOrderId")
      sessionActor ! FixMsgIn(orderSingle)

      Thread.sleep(200)
      assert(store.getInfo(sessionId).mySeq == 2)
      assert(store.getInfo(sessionId).theirSeq == 3)

      val executionReportBody = new ExecutionReportMessage(orderIDField = OrderIDField("1"),
        execIDField = ExecIDField("exec1"),
        execTypeField = ExecTypeField(ExecTypeField.New),
        ordStatusField = OrdStatusField(OrdStatusField.New),
        instrumentComponent = InstrumentComponent(symbolField = SymbolField("JPG.GB")),
        sideField = SideField(SideField.Buy),
        leavesQtyField = LeavesQtyField(200),
        cumQtyField = CumQtyField(0),
        avgPxField = AvgPxField(0))
      sessionActor ! BusinessFixMsgOut(executionReportBody, "correlation1!")

      Thread.sleep(200)

      val execReportStr = "8=Fix4.2\u00019=120\u000135=8\u000149=TargFGW\u000156=SendFGW\u000134=2\u000152=TSTAMPREMOVED\u000137=1\u000117=exec1\u0001150=0\u000139=0\u000155=JPG.GB\u000154=1\u0001151=200.0\u000114=0.0\u00016=0.0\u000110=\u0001"
      // Sequence number should be 3 now, and it should store  business ones
      store.readMessage(sessionId, 2) match {
        case None => fail("Expected the executionReport to be stored")
        case Some(fixStr) =>
          assert(execReportStr == stripTimeAndCheckSum(fixStr))
      }
      assert(store.getInfo(sessionId).mySeq == 3)
      assert(store.getInfo(sessionId).theirSeq == 3)

      // The out router should have sent out the exec report
      assert(2 == sessOutRouter.fixMessages.size)
      assert(execReportStr == stripTimeAndCheckSum(sessOutRouter.fixMessages(1)))

      // When I sent it gets a nothing sent timeout it should then get a heartbeat
      sessionActor ! NothingSentFor(1)
      Thread.sleep(200)
      // The out router should have sent out a heartbeat
      assert(3 == sessOutRouter.fixMessages.size)
      assert("8=Fix4.2\u00019=57\u000135=0\u000149=TargFGW\u000156=SendFGW\u000134=3\u000152=TSTAMPREMOVED\u000110=\u0001" == stripTimeAndCheckSum(sessOutRouter.fixMessages(2)))

      // When  I receive nothing for a heartbeat then I should demand an answer
      sessionActor ! NothingReceivedFor(1)
      // BEWARE, lots of asynch in this test, so I had the assert fail with size 4 !=5 but then it
      // did toString on the array as part of the test failure output and it had 5 elements.....lovely.
      Thread.sleep(500)
      // The out router should have sent out a heartbeat
      assert(4 == sessOutRouter.fixMessages.size)
      val (reqId, msgStr) = stripTimeReqIdAndCheckSum(sessOutRouter.fixMessages(3))
      assert("8=Fix4.2\u00019=76\u000135=1\u000149=TargFGW\u000156=SendFGW\u000134=4\u000152=TSTAMPREMOVED\u0001112=\u000110=\u0001" == msgStr)
      // so send back a heartbeat message
      val replyHeartbeatMsg = MessageFixtures.heartbeat(reqId, 3)
      sessionActor ! FixMsgIn(replyHeartbeatMsg)

      Thread.sleep(200)
      assert(store.getInfo(sessionId).mySeq == 5)
      assert(store.getInfo(sessionId).theirSeq == 4)

      // Its open prior to logout
      assert(!sessOutRouter.informCloseCalled)

      val logoutMessage = MessageFixtures.logoutWithNowTime(4, "Logout at test end")
      sessionActor ! FixMsgIn(logoutMessage)

      // BEWARE, lots of asynch in this test, so I had the assert fail with size 4 !=5 but then it
      // did toString on the array as part of the test failure output and it had 5 elements.....lovely.
      Thread.sleep(500)
      // It replies with a logout message
      assert(sessOutRouter.fixMessages.size == 5)
      assert("8=Fix4.2\u00019=57\u000135=5\u000149=TargFGW\u000156=SendFGW\u000134=5\u000152=TSTAMPREMOVED\u000110=\u0001" == stripTimeAndCheckSum(sessOutRouter.fixMessages(4)))

      // Its open prior to logout
      assert(!sessOutRouter.socketCloseCalled)
      sessionActor ! TcpSaysSocketIsClosedMsgIn(tcpProbeRef.toClassic)

      Thread.sleep(200)
      // So business layer cannot speak, but socket is still waiting
      assert(sessOutRouter.informCloseCalled)
      assert(sessOutRouter.socketCloseCalled)

      // Now close down the entire listener socket - since I'm closed already has no impact
      sessionActor ! AcceptorSocketClosedMsgIn
      Thread.sleep(200)


      //      sessionActor.sendAMessage(MessageFixtures.LogonMessageBody, probe1.ref)
      //
      //      val fixMsg = store.readMessage(seqNo)
      //      val msg = probe1.expectMsgType[Write](500 millis)
      //
      //      // message is of the form below, so strip out the time element
      //      // 8=Fix4.29=6935=A49=SendFGW56=TargFGW34=252=20170105-11:38:05.44298=0108=3010=242
      //      val msgStr = stripTimeAndCheckSum(msg.data.utf8String)
      //      assert(msgStr == "8=Fix4.2\u00019=69\u000135=A\u000149=SendFGW\u000156=TargFGW\u000134=2\u000152=TSTAMPREMOVED\u000198=0\u0001108=30\u000110=\u0001")
    }

    //    "handleMessage which is well formed" in {
    //      val session = system.actorOf(SfSessionImpl("./test/resources/tmp/acceptor", "Fix4.2", "SendFGW", "TargFGW", false, 30)
    //      val store = new SfMessageStoreStub(session)
    //      session.setTheirSeq(20)
    //
    //      val probe1 = TestProbe()
    //      session.sessionState = ActiveNormalSession
    //      session.handleMessage(MessageFixtures.newOrderSingle(20, "Ord1234"), "dummyHost", probe1.ref)
    //    }
    //  }
    //
    //
    //  def handleMessage(incomingMessage:SfMessage, socketConnectHost:String, outboundConnection:ActorRef): Unit = {
    //    validateFixMsgDetails(incomingMessage) match {
    //      case true =>
    //        lastTheirSeqNum= incomingMessage.header.msgSeqNumField.value
    //        sessionState = sessionState.receiveEvent(this, new SfSessionFixMessageEvent(incomingMessage),
    //          handleAction(socketConnectHost, outboundConnection))
    //      case false => // The message is garbled, so ignore it, do not increment incoming seq num
    //    }
    //  }


    // 8=Fix4.29=6935=A49=SendFGW56=TargFGW34=252=20170105-11:38:05.44298=0108=3010=242
    def stripTimeAndCheckSum(str: String): String = {
      stripTimeReqIdAndCheckSum(str) match {
        case (reqId, retStr) => retStr
      }
    }

    /**
      * @return (requestId, string with time sensitive removed)
      */
    def stripTimeReqIdAndCheckSum(str: String): (String, String) = {
      val SOH = 1.toChar
      val utcTimeStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss[.SSS]")

      val pairs = str.split(SOH)
      var reqId = ""
      val returnStr = pairs.map(keyVal => {
        val kv = keyVal.split("=")
        try {
          // checksum should be removed as time str changes it
          if (kv(0) == "10") "10="
          else if (kv(0) == "112") {
            reqId = kv(1)
            "112="
          } else {
            utcTimeStamp.parse(kv(1))
            // no expection, so its a date, so only return the v part
            kv(0) + "=TSTAMPREMOVED"
          }
        } catch {
          case ex: DateTimeParseException => keyVal
        }
      }).mkString("" + SOH) + "" + SOH

      (reqId, returnStr)
    }
  }
}
