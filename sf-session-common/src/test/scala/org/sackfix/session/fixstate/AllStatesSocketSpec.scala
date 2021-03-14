package org.sackfix.session.fixstate

import org.sackfix.session._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Created by Jonathan during 2017.
  */
class AllStatesSocketSpec extends AnyFlatSpec {

  behavior of "EveryState"

  val session = new SfSessionStub

  val states = List[SfSessState](ActiveNormalSession,
    AwaitingConnection,
    new AwaitingProcessingResponseToResendRequest(10,20),
    new AwaitingProcessingResponseToTestRequest("reqId"),
    DetectBrokenNetworkConnection,
    DisconnectedConnectionToday,
    DisconnectedNoConnectionToday,
    DisconnectSocketNow,
    new HandleResendRequest(ActiveNormalSession),
    new HandleTestRequest(ActiveNormalSession),
    InitiateConnection,
    InitiateLogoutProcess("test reason"),
    InitiationLogonReceived,
    InitiationLogonResponse,
    InitiationLogonSent,
    NetworkConnnectionEstablished,
    new NoMessagesReceivedInInterval,
    ReceiveLogoutMessage,
    new ReceiveMsgSeqNumTooHigh(1,10),
    WaitingForLogonAck
  )

  it should "handle the standard death events in every state" in {
    states.foreach( s => {
      s.receiveSocketEvent (session, SfSessionSocketCloseEvent) match {
        case Some(AwaitingConnection) =>  // pass
        case Some(otherState) =>
          fail("Did not expect state "+otherState+ "  In state "+s.stateName)
        case _ => fail("Expected Some(DetectBrokenNetworkConnection) In state "+s.stateName)
      }
      s.receiveSocketEvent (session, SfSessionNetworkConnectionEstablishedEvent) match {
        case Some(NetworkConnnectionEstablished) => assert(s == AwaitingConnection)
        case Some(InitiateConnection) => assert(s == DisconnectedNoConnectionToday || s==DisconnectedConnectionToday)
        case None => // pass
        case _ => fail("Expected a different result, so investigate! In state "+s.stateName)
      }
      s.receiveSocketEvent (session, SfSessionServerSocketOpenEvent) match {
        case Some(AwaitingConnection) =>
          assert (s==DisconnectedConnectionToday || s==DisconnectedNoConnectionToday)
        case None => // pass
        case _ => fail("Expected a different result, so investigate! In state "+s.stateName)
      }
    })
  }
}
