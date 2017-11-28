package com.featurefm.riversong.metrics.reporting

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.Config

/**
 * Created by Ivan von Nagy on 1/21/15.
 */
class StatsDReporter(implicit val system: ActorSystem, val config: Config) extends ScheduledReporter {

  val log = Logging(system, getClass)

  private lazy val reporter = getReporter

  private val statsdHost = config.getString("host")
  private val port = config.getInt("port")
  private val prefix = config.getString("metric-prefix")

  /**
   * Stop the scheduled metric reporting
   */
  override def stop(): Unit = {
    super.stop()
    reporter.stop()
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

  private[reporting] def getReporter: com.readytalk.metrics.StatsDReporter = {

    log.info("Initializing the StatsD metrics reporter")

    com.readytalk.metrics.StatsDReporter.forRegistry(metrics.metricRegistry)
      .prefixedWith(this.prefix)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .convertRatesTo(TimeUnit.SECONDS)
      .build(statsdHost, port)

  }

}
