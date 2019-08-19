package com.featurefm.riversong.metrics.reporting

import java.util.concurrent.TimeUnit

import akka.ConfigurationException
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.featurefm.riversong.metrics.Metrics
import com.typesafe.config.{Config, ConfigRenderOptions}

import scala.collection.JavaConverters._
import scala.collection.convert.WrapAsScala
import scala.concurrent.duration.FiniteDuration

object MetricsReportingManager {
  def props(): Props =
    Props(classOf[MetricsReportingManager])
}

/**
 * This is the reporting manage for metrics. It is responsible for launching all of the defined
 * reporters for the system.
 */
class MetricsReportingManager extends Actor with ActorLogging {

  // Get the metrics extension
  private val metrics = Metrics(context.system)

  private[reporting] var reporters = Seq.empty[ScheduledReporter]

  override def preStart(): Unit = {
    // Start the defined reporters
    startReporters()
  }

  override def receive: Receive = {
    case _ => //do nothing
  }

  override def postStop(): Unit = {
    // Stop the running reporters
    stopReporters()
  }

  /**
   * Load the defined reporters
   */
  private[reporting] def startReporters(): Unit = {
    try {
      val master = context.system.settings.config.getConfig("metrics.reporters")

      val definedReporters =
        for {
          entry <- master.root.entrySet.asScala
          if master.getConfig(entry.getKey).getBoolean("enabled")
        } yield {
          val config = master.getConfig(entry.getKey)
          val json = config.root.render(ConfigRenderOptions.defaults)

          val clazz = config.getString("class")

          metrics.system.dynamicAccess.createInstanceFor[ScheduledReporter](clazz,
            List(classOf[ActorSystem] -> context.system, classOf[Config] -> config)).map({
            reporter =>
              reporter.start(FiniteDuration(config.getDuration("reporting-interval", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS))
              reporter
          }).recover({
            case e => throw new ConfigurationException(
              "Metrics reporter specified in config can't be loaded [" + clazz +
                "] due to [" + e.toString + "]", e)
          }).get
        }

      reporters = definedReporters.toSeq

    } catch {
      case e: Exception =>
        log.error("Error while starting up metric reporters", e)
//        e.printStackTrace()
        throw new ConfigurationException("Could not start reporters due to [" + e.toString + "]")
    }
  }

  /**
   * Stop the running reporters
   */
  private[reporting] def stopReporters(): Unit = {
    reporters.foreach(_.stop())
    reporters = Seq.empty[ScheduledReporter]
  }

}
