package org.sackfix.socket

import akka.actor.Actor
import akka.{actor => classic}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.ActorSystem
import akka.io.Tcp.{Event, Received, Write}
import akka.testkit.TestActorRef
import akka.util.ByteString
import org.sackfix.boostrap._
import org.sackfix.codec.DecodingFailedData
import org.sackfix.common.message.SfMessage
import org.sackfix.common.validated.fields.SfFixMessageBody
import org.sackfix.field.{SessionRejectReasonField, TextField}
import org.sackfix.session.SfSessionActor.{ConnectionEstablishedMsgIn, SendRejectMessageOut}
import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during 2016.
  */
class SfSocketHandlerActorTest extends AnyFlatSpec {
  val SOH=1.toChar

  // Required for the TestActorRef
  implicit val system = classic.ActorSystem("SackFixFixServer")
  val typedSystem: ActorSystem[Nothing] = system.toTyped

  val commsActorRef = TestActorRef(new Actor {
    def receive = {
      case akka.io.Tcp.Write(data:ByteString, ack:Event) =>
        println("Comms actor got sent "+ data.utf8String)
      case _ => println("No idea what happened")
    }
  })

  behavior of "SfClientSocketHandlerActor"

  it should "decode messages" in {
    val themessages =s"""
   |8=FIX.4.4${SOH}9=82${SOH}35=A${SOH}34=1${SOH}49=EXEC${SOH}52=20121105-23:24:06${SOH}60=20121105-23:24:06${SOH}56=BANZAI${SOH}98=0${SOH}108=30${SOH}10=011${SOH}
   |8=FIX.4.4${SOH}9=82${SOH}35=A${SOH}34=1${SOH}49=BANZAI${SOH}52=20121105-23:24:06${SOH}60=20121105-23:24:06${SOH}56=EXEC${SOH}98=0${SOH}108=30${SOH}10=011${SOH}
   |8=FIX.4.4${SOH}9=124${SOH}35=D${SOH}34=3${SOH}49=BANZAI${SOH}52=20121105-23:24:06${SOH}60=20121105-23:24:42${SOH}56=EXEC${SOH}11=1352157882577${SOH}21=1${SOH}38=10000${SOH}40=1${SOH}54=1${SOH}55=MSFT${SOH}59=0${SOH}10=070${SOH}
   |8=FIX.4.4${SOH}9=124${SOH}35=D${SOH}34=4${SOH}49=BANZAI${SOH}52=20121105-23:24:06${SOH}60=20121105-23:24:55${SOH}56=EXEC${SOH}11=1352157895032${SOH}21=1${SOH}38=10000${SOH}40=1${SOH}54=1${SOH}55=ORCL${SOH}59=0${SOH}10=055${SOH}
   |8=FIX.4.4${SOH}9=129${SOH}35=D${SOH}34=5${SOH}49=BANZAI${SOH}52=20121105-23:24:06${SOH}60=20121105-23:25:12${SOH}56=EXEC${SOH}11=1352157912357${SOH}21=1${SOH}38=10000${SOH}40=2${SOH}44=10${SOH}54=1${SOH}55=SPY${SOH}59=0${SOH}10=011${SOH}
   |8=FIX.4.4${SOH}9=125${SOH}35=F${SOH}34=6${SOH}49=BANZAI${SOH}52=20121105-23:24:06${SOH}60=20121105-23:25:16${SOH}56=EXEC${SOH}11=1352157916437${SOH}38=10000${SOH}41=1352157912357${SOH}54=1${SOH}55=SPY${SOH}10=206${SOH}
   |8=FIX.4.4${SOH}9=125${SOH}35=F${SOH}34=7${SOH}49=BANZAI${SOH}52=20121105-23:24:06${SOH}60=20121105-23:25:25${SOH}56=EXEC${SOH}11=1352157925309${SOH}38=10000${SOH}41=1352157912357${SOH}54=1${SOH}55=SPY${SOH}10=205${SOH}
   |8=FIX.4.4${SOH}9=49${SOH}35=0${SOH}34=2${SOH}49=BANZAI${SOH}52=20121105-23:24:37${SOH}56=EXEC${SOH}10=231${SOH}
   |8=FIX.4.4${SOH}9=49${SOH}35=0${SOH}34=2${SOH}49=EXEC${SOH}52=20121105-23:24:37${SOH}56=BANZAI${SOH}10=231${SOH}
   |8=FIX.4.4${SOH}9=139${SOH}35=8${SOH}34=3${SOH}49=EXEC${SOH}52=20121105-23:24:42${SOH}56=BANZAI${SOH}6=0${SOH}11=1352157882577${SOH}14=0${SOH}17=1${SOH}20=0${SOH}31=0${SOH}32=0${SOH}37=1${SOH}38=10000${SOH}39=0${SOH}54=1${SOH}55=MSFT${SOH}150=2${SOH}151=0${SOH}10=062${SOH}
   |8=FIX.4.4${SOH}9=153${SOH}35=8${SOH}34=4${SOH}49=EXEC${SOH}52=20121105-23:24:42${SOH}56=BANZAI${SOH}6=12.3${SOH}11=1352157882577${SOH}14=10000${SOH}17=2${SOH}20=0${SOH}31=12.3${SOH}32=10000${SOH}37=2${SOH}38=10000${SOH}39=2${SOH}54=1${SOH}55=MSFT${SOH}150=2${SOH}151=0${SOH}10=233${SOH}
   |8=FIX.4.4${SOH}9=139${SOH}35=8${SOH}34=5${SOH}49=EXEC${SOH}52=20121105-23:24:55${SOH}56=BANZAI${SOH}6=0${SOH}11=1352157895032${SOH}14=0${SOH}17=3${SOH}20=0${SOH}31=0${SOH}32=0${SOH}37=3${SOH}38=10000${SOH}39=0${SOH}54=1${SOH}55=ORCL${SOH}150=2${SOH}151=0${SOH}10=052${SOH}
   |8=FIX.4.4${SOH}9=153${SOH}35=8${SOH}34=6${SOH}49=EXEC${SOH}52=20121105-23:24:55${SOH}56=BANZAI${SOH}6=12.3${SOH}11=1352157895032${SOH}14=10000${SOH}17=4${SOH}20=0${SOH}31=12.3${SOH}32=10000${SOH}37=4${SOH}38=10000${SOH}39=2${SOH}54=1${SOH}55=ORCL${SOH}150=2${SOH}151=0${SOH}10=223${SOH}
   |8=FIX.4.4${SOH}9=138${SOH}35=8${SOH}34=7${SOH}49=EXEC${SOH}52=20121105-23:25:12${SOH}56=BANZAI${SOH}6=0${SOH}11=1352157912357${SOH}14=0${SOH}17=5${SOH}20=0${SOH}31=0${SOH}32=0${SOH}37=5${SOH}38=10000${SOH}39=0${SOH}54=1${SOH}55=SPY${SOH}150=2${SOH}151=0${SOH}10=255${SOH}
   |8=FIX.4.4${SOH}9=82${SOH}35=3${SOH}34=8${SOH}49=EXEC${SOH}52=20121105-23:25:16${SOH}56=BANZAI${SOH}45=6${SOH}58=Unsupported message type${SOH}10=003${SOH}
   |8=FIX.4.4${SOH}9=82${SOH}35=3${SOH}34=9${SOH}49=EXEC${SOH}52=20121105-23:25:25${SOH}56=BANZAI${SOH}45=7${SOH}58=Unsupported message type${SOH}10=005${SOH}
   |""".stripMargin.replaceAll("[\\r\\n]", "")

    val msg:ByteString=ByteString(themessages)

    val received = ArrayBuffer.empty[String]
    val failedMsgs = ArrayBuffer.empty[String]

    val sessionLookup = new SfSessionLookup

    val sessionActorRef =  system.spawn(StubSessionActor(received, failedMsgs), "StubSessionActor")
    val sessId1 = new SfSessionId("Fix.4.4", "EXEC", "BANZAI")
    val sessId2 = new SfSessionId("Fix.4.4", "BANZAI", "EXEC")
    sessionLookup.sessionCache.add(sessId1, sessionActorRef)
    sessionLookup.sessionCache.add(sessId2, sessionActorRef)


    val businessComsStub = new BusinessCommsHandler {
      val businessMessages = ArrayBuffer.empty[SfFixMessageBody]

      override def handleFix(f: SfBusinessFixInfo) = f match {
        case msg: BusinessFixMessage =>
          businessMessages += msg.message.body
        case msg: FixSessionOpen =>
        case msg: FixSessionClosed =>
      }
    }
    val actorRef = system.spawn(SfSocketHandlerActor(SfAcceptor, commsActorRef, sessionLookup, "remotehostname", businessComsStub, None), "SfSocketHandlerActor")

    actorRef ! Received(ByteString.fromString(themessages))

    Thread.sleep(2500)
    val expected = List(
      s"8=FIX.4.4${SOH}9=86${SOH}35=A${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=1${SOH}52=20121105-23:24:06.000${SOH}98=0${SOH}108=30${SOH}60=20121105-23:24:06${SOH}10=205${SOH}",
      s"8=FIX.4.4${SOH}9=86${SOH}35=A${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=1${SOH}52=20121105-23:24:06.000${SOH}98=0${SOH}108=30${SOH}60=20121105-23:24:06${SOH}10=205${SOH}",
      s"8=FIX.4.4${SOH}9=134${SOH}35=D${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=3${SOH}52=20121105-23:24:06.000${SOH}11=1352157882577${SOH}21=1${SOH}55=MSFT${SOH}54=1${SOH}60=20121105-23:24:42.000${SOH}38=10000.0${SOH}40=1${SOH}59=0${SOH}10=033${SOH}",
      s"8=FIX.4.4${SOH}9=134${SOH}35=D${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=4${SOH}52=20121105-23:24:06.000${SOH}11=1352157895032${SOH}21=1${SOH}55=ORCL${SOH}54=1${SOH}60=20121105-23:24:55.000${SOH}38=10000.0${SOH}40=1${SOH}59=0${SOH}10=018${SOH}",
      s"8=FIX.4.4${SOH}9=141${SOH}35=D${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=5${SOH}52=20121105-23:24:06.000${SOH}11=1352157912357${SOH}21=1${SOH}55=SPY${SOH}54=1${SOH}60=20121105-23:25:12.000${SOH}38=10000.0${SOH}40=2${SOH}44=10.0${SOH}59=0${SOH}10=061${SOH}",
      s"8=FIX.4.4${SOH}9=135${SOH}35=F${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=6${SOH}52=20121105-23:24:06.000${SOH}41=1352157912357${SOH}11=1352157916437${SOH}55=SPY${SOH}54=1${SOH}60=20121105-23:25:16.000${SOH}38=10000.0${SOH}10=169${SOH}",
      s"8=FIX.4.4${SOH}9=135${SOH}35=F${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=7${SOH}52=20121105-23:24:06.000${SOH}41=1352157912357${SOH}11=1352157925309${SOH}55=SPY${SOH}54=1${SOH}60=20121105-23:25:25.000${SOH}38=10000.0${SOH}10=168${SOH}",
      s"8=FIX.4.4${SOH}9=53${SOH}35=0${SOH}49=BANZAI${SOH}56=EXEC${SOH}34=2${SOH}52=20121105-23:24:37.000${SOH}10=160${SOH}",
      s"8=FIX.4.4${SOH}9=53${SOH}35=0${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=2${SOH}52=20121105-23:24:37.000${SOH}10=160${SOH}",
      s"8=FIX.4.4${SOH}9=155${SOH}35=8${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=3${SOH}52=20121105-23:24:42.000${SOH}37=1${SOH}11=1352157882577${SOH}17=1${SOH}150=2${SOH}39=0${SOH}55=MSFT${SOH}54=1${SOH}38=10000.0${SOH}32=0.0${SOH}31=0.0${SOH}151=0.0${SOH}14=0.0${SOH}6=0.0${SOH}20=0${SOH}10=046${SOH}",
      s"8=FIX.4.4${SOH}9=165${SOH}35=8${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=4${SOH}52=20121105-23:24:42.000${SOH}37=2${SOH}11=1352157882577${SOH}17=2${SOH}150=2${SOH}39=2${SOH}55=MSFT${SOH}54=1${SOH}38=10000.0${SOH}32=10000.0${SOH}31=12.3${SOH}151=0.0${SOH}14=10000.0${SOH}6=12.3${SOH}20=0${SOH}10=034${SOH}",
      s"8=FIX.4.4${SOH}9=155${SOH}35=8${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=5${SOH}52=20121105-23:24:55.000${SOH}37=3${SOH}11=1352157895032${SOH}17=3${SOH}150=2${SOH}39=0${SOH}55=ORCL${SOH}54=1${SOH}38=10000.0${SOH}32=0.0${SOH}31=0.0${SOH}151=0.0${SOH}14=0.0${SOH}6=0.0${SOH}20=0${SOH}10=036${SOH}",
      s"8=FIX.4.4${SOH}9=165${SOH}35=8${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=6${SOH}52=20121105-23:24:55.000${SOH}37=4${SOH}11=1352157895032${SOH}17=4${SOH}150=2${SOH}39=2${SOH}55=ORCL${SOH}54=1${SOH}38=10000.0${SOH}32=10000.0${SOH}31=12.3${SOH}151=0.0${SOH}14=10000.0${SOH}6=12.3${SOH}20=0${SOH}10=024${SOH}",
      s"8=FIX.4.4${SOH}9=154${SOH}35=8${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=7${SOH}52=20121105-23:25:12.000${SOH}37=5${SOH}11=1352157912357${SOH}17=5${SOH}150=2${SOH}39=0${SOH}55=SPY${SOH}54=1${SOH}38=10000.0${SOH}32=0.0${SOH}31=0.0${SOH}151=0.0${SOH}14=0.0${SOH}6=0.0${SOH}20=0${SOH}10=239${SOH}",
      s"8=FIX.4.4${SOH}9=86${SOH}35=3${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=8${SOH}52=20121105-23:25:16.000${SOH}45=6${SOH}58=Unsupported message type${SOH}10=197${SOH}",
      s"8=FIX.4.4${SOH}9=86${SOH}35=3${SOH}49=EXEC${SOH}56=BANZAI${SOH}34=9${SOH}52=20121105-23:25:25.000${SOH}45=7${SOH}58=Unsupported message type${SOH}10=199${SOH}"
     )

    val expectedFailed = List("","","","","","","","","")

    assert(received.size == expected.size)
    for (i <- 0 until expected.size) assert(SessionTestTimeUtil.stripTimeAndCheckSum(expected(i)) == SessionTestTimeUtil.stripTimeAndCheckSum(received(i)))

//    for (i <- 0 until expectedFailed.size) assert(expectedFailed(i) == failedMsgs(i))
  }
}
