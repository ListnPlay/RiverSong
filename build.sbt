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

val log4jVersion  = "2.4.1"

val akkaVersion   = "2.3.13"

val json4sVersion = "3.2.11"

val macWireVersion = "1.0.5"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "org.apache.logging.log4j" %  "log4j-core"        % log4jVersion,
  "org.apache.logging.log4j" %  "log4j-api"         % log4jVersion,
  "org.apache.logging.log4j" %  "log4j-slf4j-impl"  % log4jVersion,
  "org.slf4j"                %  "slf4j-api"         % "1.7.12",
  "com.typesafe.akka"        %% "akka-actor"        % akkaVersion exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"        %% "akka-slf4j"        % akkaVersion exclude("org.slf4j", "slf4j-api") exclude("org.scala-lang", "scala-library"),
  "io.spray"                 %% "spray-can"         % sprayVersion,
  "io.spray"                 %% "spray-routing"     % sprayVersion,
  "io.spray"                 %% "spray-json"        % "1.3.2",
  "org.json4s"               %% "json4s-jackson"    % json4sVersion,
  "org.json4s"               %% "json4s-ext"        % json4sVersion,

  "com.softwaremill.macwire" %% "macros"            % macWireVersion,
  "com.softwaremill.macwire" %% "runtime"           % macWireVersion,

  "io.dropwizard.metrics"    %  "metrics-core"      % "3.1.2",
  "io.dropwizard.metrics"    %  "metrics-jvm"       % "3.1.2",
  "nl.grons"                 %% "metrics-scala"     % "3.5.2" exclude("io.dropwizard.metrics", "metrics-core"),
  "com.github.jjagged"       %  "metrics-statsd"    % "1.0.0" exclude("com.codahale.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "com.novaquark"            %  "metrics-influxdb"  % "0.3.0" exclude("com.codahale.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "org.coursera"             %  "metrics-datadog"   % "1.1.2" exclude("io.dropwizard.metrics", "metrics-core")
)

lazy val root = (project in file(".")).settings(
    name := "RiverSong",
    organization := "com.featurefm",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.7")
