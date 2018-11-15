package com.featurefm.riversong.metrics

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.codahale.metrics.{Metric, MetricFilter}
import nl.grons.metrics.scala.{Gauge, InstrumentedBuilder, MetricName}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Created by yardena on 9/2/15.
 */
trait Instrumented extends InstrumentedBuilder {

  implicit val system: ActorSystem

  override lazy val metricBaseName = MetricName(getClass.getSimpleName)

  import MetricsDefinition._

  /**
   * The MetricRegistry where created metrics are registered.
   */
  lazy val metricRegistry = Metrics().metricRegistry

  def time[A](name: String)(f: => A): A = {
    invocationDuration.labels(name).time(f)
  }

  def timeEventually[A](name: String)(future: => Future[A])(implicit context: ExecutionContext): Future[A] = {
    val timer = invocationDuration.labels(name).startTimer()
    future.andThen { case _ => timer.observeDuration() }
  }

  def gauge[A](name: String, scope: String = null)(f: => A): Gauge[A] =
    Try {
      metrics.gauge(name, scope)(f)
    }.recover {
      case e: IllegalArgumentException =>
        metrics.registry.removeMatching(gaugeNamed(name))
        metrics.gauge(name, scope)(f)
    }.get

  def cachedGauge[A](name: String, timeout: FiniteDuration, scope: String = null)(f: => A): Gauge[A] =
    Try {
      metrics.cachedGauge(name, timeout, scope)(f)
    }.recover {
      case e: IllegalArgumentException =>
        metrics.registry.removeMatching(gaugeNamed(name))
        metrics.gauge(name, scope)(f)
    }.get

  def resetMetrics(): Unit = {
    metrics.registry.removeMatching(myMetrics)
  }

  def resetGauges(): Unit = {
    metrics.registry.removeMatching(myGauges)
  }

  import com.codahale.metrics.{Gauge => DropwizardGauge}
  import nl.grons.metrics.scala.Implicits.functionToMetricFilter
  private[this] val myMetrics: MetricFilter = (name: String, m: Metric) => name.startsWith(metricBaseName.name)
  private[this] val myGauges: MetricFilter = (name: String, m: Metric) =>
    m.isInstanceOf[DropwizardGauge[_]] && name.startsWith(metricBaseName.name)
  private[this] def gaugeNamed(gaugeName: String, scope: String = null): MetricFilter = (name: String, m: Metric) =>
    m.isInstanceOf[DropwizardGauge[_]] && (name == metricBaseName.append(gaugeName, scope).name)

}

trait InstrumentedActor extends Actor with Instrumented { this: ActorLogging =>

  override implicit lazy val system: ActorSystem = context.system

  @throws[Exception](classOf[Exception])
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    metrics.counter(s"restarts.${reason.getClass.getSimpleName}").inc()
    log.error(reason, message.map(_.toString).getOrElse(""))
    super.preRestart(reason, message)
  }
}

