# SackFix Sessions

Sackfix is a pure Scala FIX implementation.  It was developed as a learning project, but is a full implementation of the 
 session level protocol, and you can write your own Business message handles very simply.

# Performance?
SackFix is not low latency, and it is a heavy user of GC.  

Typically when running with full debug logging and tracing of all fix messages in raw and human readable form it is 5ms 
per message before it arrives at your OMS.  With all debug and tracing off I got under 500 micro seconds - and then I 
stopped optimising - as I said its not low latency and it is high GC.  This was a test on a very small i5 laptop.

If you need some fix engine for reconciliation or post trade then it should do you fine.

# Testing in the real world?

SackFix is new in 2017, its a home project, which I have tested against the test spec.  It has never run anywhere else, please get back to me if you use it, it would be nice to hear it is being used.

Having said that, I wrote a test suite, an initiator and an acceptor and left it running for days.  Seems to work... 
but there is nothing like the real world.

# Technical details

## Versions

JDK 1.8, Scala 2.11, SBT 0.13.12, Akka 2.4.16.   Feel free to upgrade.

## Structure

Sackfix has multiple projects:
