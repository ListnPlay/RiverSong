package com.featurefm.riversong.metrics.reporting

import java.net.InetAddress

import akka.actor.{ActorSystem, Cancellable}
import com.featurefm.riversong.metrics.Metrics
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

abstract class ScheduledReporter {

  implicit val system: ActorSystem
  implicit val config: Config
  val metrics = Metrics(system)
  val host = getHostInternal
  val application = "RiverSong" //todo retrieve using sbt properties
  val version = "1.0" //todo retrieve using sbt properties

  private[reporting] var schedule: Option[Cancellable] = None

  /**
   * Get the system host
   * @return the host name
   */
  private[reporting] def getHostInternal: String = {
    try {
      InetAddress.getLocalHost.getHostName.split("\\.")(0)
    }
    catch {
      case ex: Exception => {
        "Unknown"
      }
    }
  }


  /**
   * Start the metrics reporting. This will delay that first report by the
   * given interval and then will continually run at the same interval.
   * @param interval
   */
  def start(interval: FiniteDuration): Unit = {
    import system.dispatcher
    schedule = Some(system.scheduler.schedule(interval, interval)(report()))

  }

  /**
   * Stop the scheduled metric reporting
   */
  def stop(): Unit = {
    schedule.exists(_.cancel())
    schedule = None
  }

  /**
   * This is the method that gets called so that the  metrics
   * reporting can occur.
   */
  def report(): Unit

}
