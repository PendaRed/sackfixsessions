import sbt.Keys._

// Multi project build file.  For val xxx = project, xxx is the name of the project and base dir
// logging docs: http://doc.akka.io/docs/akka/2.4.16/scala/logging.html
lazy val commonSettings = Seq(
	organization := "org.sackfix",
	version := "0.1.0",
	scalaVersion := "2.11.7",
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % "runtime", // without %runtime did not work in intellij
	libraryDependencies += "org.sackfix" %% "sackfix-common" % "0.1.0" exclude("org.apache.logging.log4j","log4j-api") exclude("org.apache.logging.log4j","log4j-core"),
	libraryDependencies += "org.sackfix" %% "sackfix-messages-fix44" % "0.1.0" exclude("org.apache.logging.log4j","log4j-api") exclude("org.apache.logging.log4j","log4j-core"),
	libraryDependencies += "com.typesafe" % "config" % "1.3.0",
	libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "test",
  libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.4.16",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  libraryDependencies += "org.mockito" % "mockito-all" % "1.10.19"  % "test",
  // Configuring publish to Sonartype, http://www.scala-sbt.org/release/docs/Using-Sonatype.html
  useGpg := true,
  pomIncludeRepository := { _ => false },
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  homepage := Some(url("http://www.sackfix.org/")),
  developers := List(
    Developer(id = "PendaRed",
      name  = "Jonathan Gibbons",
      email = "Jonathan@sackfix.org",
      url = url("http://www.sackfix.org"))
  ),
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false
)

lazy val sfsessioncommon = (project in file("./sf-session-common")).
  settings(commonSettings: _*).
  settings(
    name := "sf-session-commmon",
    libraryDependencies ++=Seq(
      "org.apache.kafka" % "kafka-streams" % "0.10.0.0" exclude("log4j","log4j-api") exclude("log4j","log4j-core"),
      "org.apache.avro" % "avro" % "1.8.0",
      "org.apache.avro" % "avro-compiler" % "1.8.0",
      "org.apache.avro" % "avro-ipc" % "1.8.0"
    ),
    // Configuring publish to Sonartype, http://www.scala-sbt.org/release/docs/Using-Sonatype.html
    description :="Fix decoder, SessionActor, Session statemachine impl, initiator and acceptor framework.  Everything really.",
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/PendaRed/sackfixsessions.git"),
        "scm:git@github.com:PendaRed/sackfixsessions.git"
      )
    )
  )


lazy val sackfixsessions = (project in file(".")).aggregate(sfsessioncommon)
