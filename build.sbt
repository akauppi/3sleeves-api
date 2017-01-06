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
//val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion

val akkaHttpVersion = "10.0.0"
//val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
//val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test

val config = "com.typesafe" % "config" % "1.3.1"

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test

val dockerItScala = "com.whisk" %% "docker-testkit-scalatest" % "0.9.0-RC2" % "test"

/***
libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.1.6"
)
***/

lazy val `three-sleeves` = project.in(file("."))
  .aggregate(api, `test-suite`, `impl-mem`)

lazy val api = project
  .settings(
    libraryDependencies ++= Seq(
      akkaStream,
      config,
      //
      scalaTest
    )
  )

// Conformance suite - each 'impl-...' should use this in its tests.
//
lazy val `test-suite` = project
  .settings(
    libraryDependencies ++= Seq(
      akkaStream,
      config,
      //
      scalaTest
    )
  )
  .dependsOn(api)

// Nickname "Calot" - plain memory implementation for testing
//
lazy val `impl-mem` = project
  .settings(
    libraryDependencies ++= Seq(
      akkaStream,
      config,
      //
      scalaTest
    )
  )
  .dependsOn(api, `test-suite` % "compile->test")

// For experiments, not part of the project
//
lazy val playground = project
  .settings(
    libraryDependencies ++= Seq(
      akkaStream,
      //config,
      //
      scalaTest
    )
  )
