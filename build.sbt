import sbt.Keys._

resolvers ++= Seq(
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype repo"                at "https://oss.sonatype.org/content/groups/scala-tools/",
  "Sonatype releases"            at "https://oss.sonatype.org/content/repositories/releases",
  "hseeberger at bintray"        at "http://dl.bintray.com/hseeberger/maven"
)

val log4jVersion  = "2.4.1"

val akkaVersion   = "2.4.1"

val streamsVersion = "2.0.1"

val json4sVersion = "3.3.0"

val macWireVersion = "2.2.2"

val jacksonVersion = "2.6.3"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "org.apache.logging.log4j" %  "log4j-core"        % log4jVersion,
  "org.apache.logging.log4j" %  "log4j-api"         % log4jVersion,
  "org.apache.logging.log4j" %  "log4j-slf4j-impl"  % log4jVersion,
  "org.slf4j"                %  "slf4j-api"         % "1.7.12",
  "com.typesafe.akka"        %% "akka-actor"        % akkaVersion exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"        %% "akka-slf4j"        % akkaVersion exclude("org.slf4j", "slf4j-api") exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"        %% "akka-stream-experimental"          % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11") exclude("com.typesafe", "config"),
  "com.typesafe.akka"        %% "akka-http-core-experimental"       % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11") exclude("com.typesafe", "config"),
  "com.typesafe.akka"        %% "akka-http-experimental"            % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11") exclude("com.typesafe", "config"),
  "com.typesafe.akka"        %% "akka-http-spray-json-experimental" % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11") exclude("com.typesafe", "config"),
  "com.fasterxml.jackson.core" % "jackson-core"        % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "org.json4s"               %% "json4s-jackson"    % json4sVersion exclude("com.fasterxml.jackson.core", "jackson-core") exclude("com.fasterxml.jackson.core", "jackson-annotations"),
  "org.json4s"               %% "json4s-ext"        % json4sVersion,
  "de.heikoseeberger"        %% "akka-http-json4s"  % "1.4.1" exclude("com.typesafe.akka", "akka-actor") exclude("org.json4s", "json4s-core_2.11") exclude("com.typesafe", "config"),

  "com.softwaremill.macwire" %% "macros"            % macWireVersion % "provided",
  "com.softwaremill.macwire" %% "util"              % macWireVersion,
  "com.softwaremill.macwire" %% "proxy"             % macWireVersion,

  "io.dropwizard.metrics"    %  "metrics-core"      % "3.1.2" exclude("org.slf4j", "slf4j-api"),
  "io.dropwizard.metrics"    %  "metrics-jvm"       % "3.1.2" exclude("org.slf4j", "slf4j-api"),
  "nl.grons"                 %% "metrics-scala"     % "3.5.2" exclude("io.dropwizard.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "com.github.jjagged"       %  "metrics-statsd"    % "1.0.0" exclude("com.codahale.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "com.novaquark"            %  "metrics-influxdb"  % "0.3.0" exclude("com.codahale.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "org.coursera"             %  "metrics-datadog"   % "1.1.2" exclude("io.dropwizard.metrics", "metrics-core") exclude("com.fasterxml.jackson.core", "jackson-core") exclude("com.fasterxml.jackson.core", "jackson-annotations") exclude("com.fasterxml.jackson.core", "jackson-databind"),

  "org.scalatest"            %% "scalatest"         % "2.2.5" % "test",
  "com.typesafe.akka"        %% "akka-http-testkit-experimental" % streamsVersion % "test"
)

lazy val root = (project in file(".")).settings(
    name := "river-song",
    organization := "com.featurefm",
    version := "0.3.7",
    scalaVersion := "2.11.7",
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
