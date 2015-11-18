package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult.{Rejected, Complete}
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.stream.ActorMaterializer
import com.featurefm.riversong.metrics.reporting.MetricsReportingManager
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.Directives._
import nl.grons.metrics.scala._
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.Period

import scala.compat.Platform
import scala.concurrent.Promise

/**
 * Created by yardena on 9/20/15.
 */
abstract class MainService(val name: String = "Spoilers") extends App with Configurable { self: App =>
  implicit val system = ActorSystem(name)
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass)

  val host = config.getString("akka.http.server.listen_ip")
  val port = config.getInt("akka.http.server.listen_port")

  private lazy val requestCounter = assembly.metrics.counter("requests")

  def assembly:ServiceAssembly

  private def wrapper(req: HttpRequest): Any => Option[LogEntry] = {
    case Complete(res) =>
      Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> ${res.status}", Logging.InfoLevel))
    case Rejected(seq) if seq.isEmpty =>
      Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> ${StatusCodes.NotFound}", Logging.InfoLevel))
    case Rejected(_) =>
      Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> Rejected", Logging.InfoLevel))
  }

  private lazy val routes: Route =
    if (config.getBoolean("akka.http.server.request_log"))
      logRequestResult(wrapper _)(measuredRoutes) //rawRoutes
    else
      measuredRoutes//rawRoutes

  private[this] def measuredRoutes: Route = {
    val rawRoutes: Route = assembly.routes ~ assembly.lifecycle.routes

    { ctx: RequestContext => requestCounter.inc(); ctx } andThen rawRoutes
  }

  Http().bindAndHandle(routes, host, port) onSuccess {
    case bind: Http.ServerBinding =>
      log.info(s"Server ${bind.localAddress} started")
      val startTime = Platform.currentTime
      val formatter = new PeriodFormatterBuilder().appendDays().appendSuffix("d").appendHours().appendSuffix("h").appendMinutes().appendSuffix("m").appendSeconds().appendSuffix("s").toFormatter
      assembly.metrics.gauge("uptime"){ new Period(Platform.currentTime - startTime).toString(formatter) }
  }

  // Start the metrics reporters
  system.actorOf(MetricsReportingManager.props())

}
