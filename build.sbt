// build.sbt
//
version in ThisBuild := "0.0.0-SNAPSHOT"
organization in ThisBuild := "gmail.akauppi"
scalaVersion in ThisBuild := "2.12.1"     // "2.11.8" if we need to compile in DistributedLog (0.4.0)

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-encoding", "utf8",
  "-feature",
  "-unchecked",
  //"-Xfatal-warnings",
  //"-Xlint",
  //"-Ywarn-dead-code",
  //"-Ywarn-numeric-widen",
  //"-Ywarn-value-discard",
  //"-Xfuture",
  "-language", "postfixOps"
)

//--- Dependencies ---

val akkaVersion = "2.4.16"
val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion

val akkaHttpVersion = "10.0.0"
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test

/*** disabled
// Note: DL is a Java library but exported with reference to Scala version (2.10 or 2.11) since it uses those
//    as internal dependencies.
//
val dlVersion = "0.4.0-incubating-SNAPSHOT"
val distributedLog = Seq(
  "com.twitter" %% "distributedlog-core" % dlVersion,
  "com.twitter" %% "distributedlog-client" % dlVersion,
  "com.twitter" %% "distributedlog-messaging" % dlVersion,
  "com.twitter" %% "distributedlog-protocol" % dlVersion
)

// So it finds DistributedLog artefacts
//
resolvers += Resolver.mavenLocal

// 'mvn install' did not provide .jar for this:
//
// See -> https://issues.apache.org/jira/browse/DL-148
//
libraryDependencies += "com.twitter.common" % "net-util" % "0.0.102"
***/

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test

val dockerItScala = "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-RC2" % "test"

/***
libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.1.6"
)
***/

lazy val `three-sleeves-api` = project
  .settings(
    libraryDependencies ++= Seq(
      akkaHttp,
      //
      akkaHttpTestkit,
      scalaTest,
      dockerItScala
    ) //++ distributedLog
  )
