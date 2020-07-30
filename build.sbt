import sbt.Keys._
import sbt.librarymanagement.Resolver

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.bintrayRepo("hseeberger", "maven"),
  Resolver.bintrayRepo("readytalk", "maven"),
  Resolver.bintrayRepo("listnplay", "maven")
)

val akkaVersion     = "2.5.23"

val akkaHttpVersion = "10.1.8"

val json4sVersion   = "3.5.3"

val macWireVersion  = "2.3.3"

val jacksonVersion  = "2.8.5"

val slf4jVersion    = "1.7.21"

val prometheusVersion = "0.5.0"

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation")
parallelExecution in Test := false
fork in Test := true
javaOptions in Test ++= Seq("-Dlogback.configurationFile=test-logback.xml")

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"               % akkaVersion exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"         %% "akka-stream"              % akkaVersion exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"         %% "akka-slf4j"               % akkaVersion exclude("org.slf4j", "slf4j-api") exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"         %% "akka-http"                % akkaHttpVersion exclude("com.typesafe", "config"),
  
  "com.typesafe.akka"         %% "akka-stream-kafka"        % "1.1.0",

  "com.fasterxml.jackson.core" % "jackson-core"             % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations"      % jacksonVersion,
  "org.json4s"                %% "json4s-jackson"           % json4sVersion exclude("com.fasterxml.jackson.core", "jackson-core") exclude("com.fasterxml.jackson.core", "jackson-annotations"),
  "org.json4s"                %% "json4s-ext"               % json4sVersion exclude("joda-time","joda-time") exclude("org.joda","joda-convert"),
  "de.heikoseeberger"         %% "akka-http-json4s"         % "1.20.1" exclude("com.typesafe.akka", "akka-http_2.11") exclude("com.typesafe.akka", "akka-stream_2.11"),

  "ch.qos.logback"             % "logback-classic"          % "1.1.7",
  "org.logback-extensions"     % "logback-ext-loggly"       % "0.1.4" exclude("ch.qos.logback", "logback-classic"),
  "org.slf4j"                  %  "slf4j-api"               % slf4jVersion,
  "org.slf4j"                  %  "log4j-over-slf4j"        % slf4jVersion,
  "com.getsentry.raven"        % "raven-logback"            % "7.6.0" exclude("com.fasterxml.jackson.core", "jackson-core"),

  "com.softwaremill.macwire"  %% "macros"                   % macWireVersion % "provided",
  "com.softwaremill.macwire"  %% "util"                     % macWireVersion,
  "com.softwaremill.macwire"  %% "proxy"                    % macWireVersion exclude("org.scalatest", "scalatest_2.11"),

  "com.github.nscala-time"    %% "nscala-time"              % "2.18.0",
  "com.github.nscala-money"   %% "nscala-money"             % "0.13.0",

  "io.prometheus"              %  "simpleclient"            % prometheusVersion,
  "io.prometheus"              %  "simpleclient_hotspot"    % prometheusVersion,
  "io.prometheus"              %  "simpleclient_common"     % prometheusVersion,
  "io.prometheus"              %  "simpleclient_dropwizard" % prometheusVersion,
  
  "io.dropwizard.metrics"      % "metrics-core"             % "3.1.2" exclude("org.slf4j", "slf4j-api"),
  "io.dropwizard.metrics"      % "metrics-jvm"              % "3.1.2" exclude("org.slf4j", "slf4j-api"),
  "nl.grons"                  %% "metrics-scala"            % "3.5.5" exclude("io.dropwizard.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),

  "org.codehaus.janino"        % "janino"                   % "3.0.1", //who needs it?

  //------------------------------------ T E S T ----------------------------------------------

  "org.apache.kafka"         %% "kafka"                     % "2.1.1"         % Test,
  "org.scalatest"            %% "scalatest"                 % "3.0.5"         % Test,
  "io.github.embeddedkafka"  %% "embedded-kafka"            % "2.1.1"         % Test,
  "org.mockito"              %% "mockito-scala"             % "1.1.3"         % Test,
  "com.typesafe.akka"        %% "akka-testkit"              % akkaVersion     % Test,
  "com.typesafe.akka"        %% "akka-stream-testkit"       % akkaVersion     % Test,
  "com.typesafe.akka"        %% "akka-http-testkit"         % akkaHttpVersion % Test
)

lazy val scala212 = "2.12.9"
lazy val scala211 = "2.11.12"
lazy val supportedScalaVersions = List(scala211, scala212)

lazy val root = (sbt.project in file(".")).settings(
    name := "river-song",
    organization := "com.featurefm",
    version := "0.12.7",
    crossScalaVersions := supportedScalaVersions,
    bintrayOrganization := Some("listnplay"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    publishMavenStyle := true,
    pomAllRepositories := true,
    pomExtra := <scm>
                  <url>https://github.com/ListnPlay/RiverSong</url>
                  <connection>git@github.com:ListnPlay/RiverSong.git</connection>
                </scm>
                <developers>
                  <developer>
                    <id>ymeymann</id>
                    <name>Yardena Meymann</name>
                    <url>https://github.com/ymeymann</url>
                  </developer>
                </developers>
    )
