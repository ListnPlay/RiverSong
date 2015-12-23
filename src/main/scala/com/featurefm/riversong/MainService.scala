package com.featurefm.riversong

import akka.actor.{DeadLetter, Props, ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult.{Rejected, Complete}
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.stream.ActorMaterializer
import com.featurefm.riversong.metrics.{Instrumented, DeadLetterMetrics}
import com.featurefm.riversong.metrics.reporting.MetricsReportingManager
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.Directives._
import nl.grons.metrics.scala.MetricName
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.Period

import scala.compat.Platform

/**
 * Created by yardena on 9/20/15.
 */
abstract class MainService(val name: String = "Spoilers") extends App with Configurable with Instrumented { self: App =>
  implicit val system = ActorSystem(name)
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass)

  val host = config.getString("akka.http.server.listen_ip")
  val port = config.getInt("akka.http.server.listen_port")

  override lazy val metricBaseName = MetricName(system.name)
  private lazy val requestCounter = metrics.counter("requests")

  def assembly:ServiceAssembly

  private[this] val F = new PeriodFormatterBuilder().appendMinutes().appendSuffix("m").appendSeconds().appendSuffix("s").printZeroAlways().appendMillis().appendSuffix("ms").toFormatter

  private def wrapper(req: HttpRequest): Any => Option[LogEntry] = {
    val beginning = Platform.currentTime

    {
      case Complete(res) =>
        val duration = new Period(Platform.currentTime - beginning)
        Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> ${res.status} [${duration.toString(F)}]", Logging.InfoLevel))
      case Rejected(seq) if seq.isEmpty =>
        Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> ${StatusCodes.NotFound}", Logging.InfoLevel))
      case Rejected(_) =>
        Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> Rejected", Logging.InfoLevel))
    }
  }

  private lazy val routes: Route =
    if (config.getBoolean("akka.http.server.request_log"))
      logRequestResult(wrapper _)(measuredRoutes) //rawRoutes
    else
      measuredRoutes//rawRoutes

  private[this] lazy val measuredRoutes: Route = {
    val rawRoutes: Route = assembly.routes ~ assembly.lifecycle.routes

    { ctx: RequestContext => requestCounter.inc(); ctx } andThen rawRoutes
  }

  Http().bindAndHandle(routes, host, port) onSuccess {
    case bind: Http.ServerBinding =>
      log.info(s"Server ${bind.localAddress} started")
      val startTime = Platform.currentTime
      val formatter = new PeriodFormatterBuilder().appendDays().appendSuffix("d").appendHours().appendSuffix("h").appendMinutes().appendSuffix("m").printZeroAlways().appendSeconds().appendSuffix("s").toFormatter
      metrics.gauge("uptime"){ new Period(Platform.currentTime - startTime).toString(formatter) }
  }

  // Start the metrics reporters
  system.actorOf(MetricsReportingManager.props())

  system.eventStream.subscribe(system.actorOf(Props(classOf[DeadLetterMetrics]),"dead-letters-metric"), classOf[DeadLetter])

}