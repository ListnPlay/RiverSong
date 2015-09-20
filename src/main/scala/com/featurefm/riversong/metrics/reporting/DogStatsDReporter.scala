package com.featurefm.riversong.metrics.reporting

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.Config
import org.coursera.metrics.datadog.DatadogReporter.Expansion
import org.coursera.metrics.datadog.DefaultMetricNameFormatter
import org.coursera.metrics.datadog.transport.{Transport, UdpTransport}

import scala.collection.convert.wrapAsScala.asScalaBuffer


class DogStatsDReporter(implicit val system: ActorSystem, val config: Config) extends ScheduledReporter {

  val log = Logging(system, getClass)

  private lazy val reporter = getReporter
  private lazy val transport = getTransport

  private[reporting] val prefix = config.getString("metric-prefix")
  private[reporting] val apiKey = config.getString("api-key")

  private[reporting] val tags = config.getStringList("tags") ++ Seq(
    s"app:${application.replace(" ", "-").toLowerCase}",
    s"version:$version")

  /**
   * Stop the scheduled metric reporting
   */
  override def stop(): Unit = {
    super.stop()
    if (transport != null) {
      transport.close()
    }
  }

  /**
   * This is the method that gets called so that the  metrics
   * reporting can occur.
   */
  def report(): Unit = {

    reporter.report(metrics.metricRegistry.getGauges,
      metrics.metricRegistry.getCounters,
      metrics.metricRegistry.getHistograms,
      metrics.metricRegistry.getMeters,
      metrics.metricRegistry.getTimers)
  }

  private[reporting] def getReporter: org.coursera.metrics.datadog.DatadogReporter = {

    import scala.collection.JavaConversions._

    log.info("Initializing the DogStatsD metrics reporter")
    org.coursera.metrics.datadog.DatadogReporter.forRegistry(metrics.metricRegistry)
        .withExpansions(Expansion.ALL)
        .withHost(host)
        .withMetricNameFormatter(new DefaultMetricNameFormatter())
        .withPrefix(prefix)
        .withTags(tags)
        .withTransport(transport)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build()
  }

  private[reporting] def getTransport: Transport = {
    new UdpTransport.Builder().build()
  }

}
