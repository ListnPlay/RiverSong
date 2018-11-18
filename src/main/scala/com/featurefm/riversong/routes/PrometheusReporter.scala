package com.featurefm.riversong.routes

import java.io.StringWriter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType, HttpCharsets, MediaType}
import com.featurefm.riversong.metrics.Metrics
import com.typesafe.config.Config
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports

import scala.collection.JavaConverters._

class PrometheusReporter(system: ActorSystem, config: Config) {

  lazy val registry = CollectorRegistry.defaultRegistry

  // add jvm metrics
  if (config.getBoolean("metrics.jvm"))
    DefaultExports.initialize()

  // Add Dropwizard adapter exporter
  registry.register(new DropwizardExports(Metrics(system).metricRegistry))

  def renderMetrics(names: Seq[String]): String = {
    val writer = new StringWriter
    TextFormat.write004(writer, registry.filteredMetricFamilySamples(names.toSet.asJava))
    writer.toString
  }

  lazy val contentType = ContentType {
    MediaType.customWithFixedCharset(
      "text",
      "plain",
      HttpCharsets.`UTF-8`,
      params = Map("version" -> "0.0.4"))
  }
}
