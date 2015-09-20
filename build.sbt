resolvers ++= Seq(
  "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype repo"                at "https://oss.sonatype.org/content/groups/scala-tools/",
  "Sonatype releases"            at "https://oss.sonatype.org/content/repositories/releases",
  "Sonatype snapshots"           at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype staging"             at "http://oss.sonatype.org/content/repositories/staging",
  "Java.net Maven2 Repository"   at "http://download.java.net/maven/2/",
  "spray repo"                   at "http://repo.spray.io"
)

val sprayVersion  = "1.3.3"

val slf4jVersion  = "1.7.12"

val akkaVersion   = "2.3.13"

val json4sVersion = "3.2.11"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "org.slf4j"                %  "slf4j-api"         % slf4jVersion,
  "org.slf4j"                %  "log4j-over-slf4j"  % slf4jVersion,
  "ch.qos.logback"           %  "logback-classic"   % "1.1.3",
  "com.typesafe.akka"        %% "akka-actor"        % akkaVersion exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"        %% "akka-slf4j"        % akkaVersion exclude("org.slf4j", "slf4j-api") exclude("org.scala-lang", "scala-library"),
  "io.spray"                 %% "spray-can"         % sprayVersion,
  "io.spray"                 %% "spray-routing"     % sprayVersion,
  "io.spray"                 %% "spray-json"        % "1.3.2",
  "org.json4s"               %% "json4s-jackson"    % json4sVersion,
  "org.json4s"               %% "json4s-ext"        % json4sVersion,

  "com.softwaremill.macwire" %% "macros"            % "1.0.5",
  "com.softwaremill.macwire" %% "runtime"           % "1.0.5",

  "io.dropwizard.metrics"    %  "metrics-jvm"       % "3.1.2",
  "nl.grons"                 %% "metrics-scala"     % "3.5.2" excludeAll ExclusionRule("com.codahale.metrics"),
  "com.github.jjagged"       %   "metrics-statsd"   % "1.0.0" excludeAll ExclusionRule("com.codahale.metrics"),
  "com.novaquark"            %   "metrics-influxdb" % "0.3.0" excludeAll ExclusionRule("com.codahale.metrics"),
  "org.coursera"             %   "metrics-datadog"  % "1.1.2" excludeAll ExclusionRule("com.codahale.metrics"),

  "org.scalatest"            %% "scalatest"         % "2.2.1"  % "test",
  "io.spray"                 %% "spray-testkit"     % "1.3.1"  % "test"
)

lazy val root = (project in file(".")).settings(
    name := "RiverSong",
    organization := "com.featurefm",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.7")
