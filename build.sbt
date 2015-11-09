resolvers ++= Seq(
  "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype repo"                at "https://oss.sonatype.org/content/groups/scala-tools/",
  "Sonatype releases"            at "https://oss.sonatype.org/content/repositories/releases",
  "Sonatype snapshots"           at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype staging"             at "http://oss.sonatype.org/content/repositories/staging",
  "Java.net Maven2 Repository"   at "http://download.java.net/maven/2/",
  "hseeberger at bintray"        at "http://dl.bintray.com/hseeberger/maven",
  "spray repo"                   at "http://repo.spray.io"
)

val log4jVersion  = "2.4.1"

val akkaVersion   = "2.4.0"

val streamsVersion = "1.0"

val json4sVersion = "3.3.0"

val macWireVersion = "1.0.5"

val jacksonVersion = "2.6.3"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "org.apache.logging.log4j" %  "log4j-core"        % log4jVersion,
  "org.apache.logging.log4j" %  "log4j-api"         % log4jVersion,
  "org.apache.logging.log4j" %  "log4j-slf4j-impl"  % log4jVersion,
  "org.slf4j"                %  "slf4j-api"         % "1.7.12",
  "com.typesafe.akka"        %% "akka-actor"        % akkaVersion exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"        %% "akka-slf4j"        % akkaVersion exclude("org.slf4j", "slf4j-api") exclude("org.scala-lang", "scala-library"),
  "com.typesafe.akka"        %% "akka-stream-experimental"          % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11"),
  "com.typesafe.akka"        %% "akka-http-core-experimental"       % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11"),
  "com.typesafe.akka"        %% "akka-http-experimental"            % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11"),
  "com.typesafe.akka"        %% "akka-http-spray-json-experimental" % streamsVersion exclude("com.typesafe.akka", "akka-actor_2.11"),
  "com.fasterxml.jackson.core" % "jackson-core"        % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "org.json4s"               %% "json4s-jackson"    % json4sVersion exclude("com.fasterxml.jackson.core", "jackson-core") exclude("com.fasterxml.jackson.core", "jackson-annotations"),
  "org.json4s"               %% "json4s-ext"        % json4sVersion,
  "de.heikoseeberger"        %% "akka-http-json4s"  % "1.1.0" exclude("com.typesafe.akka", "akka-actor") exclude("org.json4s", "json4s-core_2.11"),

  "com.softwaremill.macwire" %% "macros"            % macWireVersion,
  "com.softwaremill.macwire" %% "runtime"           % macWireVersion,

  "io.dropwizard.metrics"    %  "metrics-core"      % "3.1.2" exclude("org.slf4j", "slf4j-api"),
  "io.dropwizard.metrics"    %  "metrics-jvm"       % "3.1.2" exclude("org.slf4j", "slf4j-api"),
  "nl.grons"                 %% "metrics-scala"     % "3.5.2" exclude("io.dropwizard.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "com.github.jjagged"       %  "metrics-statsd"    % "1.0.0" exclude("com.codahale.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "com.novaquark"            %  "metrics-influxdb"  % "0.3.0" exclude("com.codahale.metrics", "metrics-core") exclude("org.slf4j", "slf4j-api"),
  "org.coursera"             %  "metrics-datadog"   % "1.1.2" exclude("io.dropwizard.metrics", "metrics-core") exclude("com.fasterxml.jackson.core", "jackson-core") exclude("com.fasterxml.jackson.core", "jackson-annotations") exclude("com.fasterxml.jackson.core", "jackson-databind"),

  "org.scalatest"            %% "scalatest"         % "2.2.5" % "test",
  "com.typesafe.akka"        %% "akka-http-testkit-experimental" % "1.0" % "test"
)

lazy val root = (project in file(".")).settings(
    name := "river-song",
    organization := "com.featurefm",
    version := "0.1.5",
    scalaVersion := "2.11.7")
