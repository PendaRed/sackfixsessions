<a href="https://pendared.github.io/sackfix/"><img src ="https://pendared.github.io/sackfix/assets/sf_logo.png" /></a>

# SackFix

A Scala Fix Engine implementation.  It is a full implementation of the session level protocol, tested using the sackfix tester project, supporting acceptor and initiators using AKKA and Scala.

To get started simply download this project and start the acceptor and then the initiator - [follow this guide](http://www.sackfix.org/runningtheexamples.html).   The SackFix suite consists of

* [Examples](https://github.com/PendaRed/sackfixexamples): This is all you need!
* [Tester](https://github.com/PendaRed/sackfixtests): A very simple test suite to stress out any Session level implementation.
* [Session](https://github.com/PendaRed/sackfixsessions): All of the statemachines and message handling for the Fix Session.  ie the business logic lives here.
* [Messages](https://github.com/PendaRed/sackfixmessages): Code generated Fix Messages for all versions of fix.
* [Common](https://github.com/PendaRed/sackfix): The code generator and common classes - including all the code generated Fields.

Full documentation is at the [github pages](https://pendared.github.io/sackfix/).

## Versions

Upgraded in 2021 to akka typed and scala 2.13.

| Version | Year | built with |
|---------|------|------------|
| 0.1.0  | 2017 | JDK 1.8, Scala 2.11, SBT 0.13.12, Akka 2.4.16 |
| 0.1.3  | 2021 | JDK 1.8, Scala 2.13.5, SBT 1.4.7, Akka 2.6.13 |

Feel free to upgrade and generate your own version.

# SackFix Sessions

SackFix is a Scala session layer fix implememtion.   This project includes the full session level implementation, with decoders for Fix TCP comms, statemachines, message stores, loggers and latency recorders.   It is the heart of the project.   And yet, you never need
to look at it!  Download sackfixexamples, thats all you need.

Upgraded in 2021 to akka typed and scala 2.13, see the sackfix project for version information.

Please visit the [github pages](http://https://pendared.github.io/sackfix/) for instructions on how to run the initiator and acceptor.

Best wishes,
Jonathan

<a href="https://pendared.github.io/sackfix/"><img src ="https://pendared.github.io/sackfix/assets/sackfix.png" /></a>
