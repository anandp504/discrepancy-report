import sbt._
import sbt.Keys._

object Build extends Build {

  val commonSettings = Seq(
    version := "1.0.0",
    organization := "com.collective",
    scalaVersion := "2.11.4",
    scalacOptions ++= List(
      "-encoding", "UTF-8",
      "-target:jvm-1.8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Xfatal-warnings"
    ),
    resolvers ++= Seq(
      "ConJars" at "http://conjars.org/repo"
    ),
    fork in run := true,
    javaOptions in run ++= Seq("-Xms256m", "-Xmx2048m", "-XX:+UseConcMarkSweepGC")
  )

  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  //val adLibsV = "1.35.1"
  val adLibsV = "2.10.0"

  lazy val discrepancyReport = Project("discrepancy-report", file("."))
    .settings(commonSettings: _*)
    .settings(
      name := "Segment Fetcher",
      libraryDependencies ++= Seq(
        "com.typesafe.akka"       %%  "akka-actor"           % akkaV,
        "com.typesafe.akka"       %%  "akka-testkit"         % akkaV     % "test",
        "org.json4s"              %%  "json4s-jackson"       % "3.2.10",
        "org.specs2"              %%  "specs2-core"          % "2.3.11"  % "test",
        "org.slf4j"               %   "slf4j-api"            % "1.7.7",
        "ch.qos.logback"          %   "logback-core"         % "1.1.2",
        "ch.qos.logback"          %   "logback-classic"      % "1.1.2",
        "io.spray"                %%  "spray-can"            % sprayV,
        "io.spray"                %%  "spray-client"         % sprayV,
        "io.spray"                %%  "spray-httpx"          % sprayV,
        "io.spray"                %%  "spray-http"           % sprayV,
        "io.spray"                %%  "spray-routing"        % sprayV,
        "io.spray"                %%  "spray-util"           % sprayV,
        "io.spray"                %%  "spray-io"             % sprayV,
        "org.parboiled"           %   "parboiled-core"       % "1.1.6",
        "org.parboiled"           %%  "parboiled-scala"      % "1.1.6",
        "org.scalatest"           %%  "scalatest"            % "2.2.2"   % "test",
        "com.pragmasoft"          %%  "spray-funnel"         % "1.1-spray1.3",
        "com.google.api-ads"      %  "ads-lib"              % adLibsV,
        "com.google.api-ads"      %  "ads-lib-axis"         % adLibsV,
        "com.google.api-ads"      %  "dfp-axis"             % adLibsV,
        "com.google.http-client"  %  "google-http-client"   % "1.19.0",
        "com.google.http-client"  %  "google-http-client-jackson2"     % "1.19.0",
        "joda-time"               % "joda-time"             % "2.5",
        "org.joda"                % "joda-convert"          % "1.7",
        "com.norbitltd"               % "spoiwo"            % "1.0.6"
      )
    )

}