package com.featurefm.riversong

import akka.actor.{ActorSystem, DeadLetter, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.{Rejection, RejectionHandler, RequestContext, Route}
import akka.stream.ActorMaterializer
import com.featurefm.riversong.message.Message
import com.featurefm.riversong.metrics.reporting.MetricsReportingManager
import com.featurefm.riversong.metrics.{DeadLetterMetrics, Instrumented}
import com.featurefm.riversong.routes.RiverSongRouting
import nl.grons.metrics.scala.MetricName
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import org.omg.CosNaming.NamingContextPackage.NotFound

import scala.compat.Platform
import scala.util.{Failure, Success}

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

  def services: Seq[RiverSongRouting] = List(assembly.lifecycle)

  private[this] val F1 = new PeriodFormatterBuilder().appendMinutes().appendSuffix("m").appendSeconds().appendSuffix("s").printZeroAlways().appendMillis().appendSuffix("ms").toFormatter

  private[this] val F2 = new PeriodFormatterBuilder().appendDays().appendSuffix("d").appendHours().appendSuffix("h").appendMinutes().appendSuffix("m").printZeroAlways().appendSeconds().appendSuffix("s").toFormatter


  private def wrapper(req: HttpRequest): Any => Option[LogEntry] = {
    val beginning = Platform.currentTime

    {
      case Complete(res) =>
        val duration = new Period(Platform.currentTime - beginning)
        Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> ${res.status} [${duration.toString(F1)}]", Logging.InfoLevel))
      case Rejected(seq) if seq.isEmpty =>
        Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> ${StatusCodes.NotFound}", Logging.InfoLevel))
      case Rejected(_) =>
        Some(LogEntry(s"${req.method.value} ${req.uri.path} ~> Rejected", Logging.InfoLevel))
    }
  }

  private def buildRoutes(r: RiverSongRouting*) = r.map(_.routes).reduce(_ ~ _)

  private[this] lazy val routes: Route =
    if (config.getBoolean("akka.http.server.request_log"))
      logRequestResult(wrapper _)(measuredRoutes)
    else
      measuredRoutes

  private[this] lazy val measuredRoutes: Route = {

    val defaultRejectionHandler = RejectionHandler.default
    import Json4sProtocol._

    def prefixEntity(entity: ResponseEntity): ResponseEntity = entity match {
      case HttpEntity.Strict(contentType, data) =>
        HttpEntity(ContentTypes.`application/json`, serialization.write(Message(data.utf8String)))
      case _ =>
        throw new IllegalStateException("Unexpected entity type")
    }

    val myRejectionHandler =
      RejectionHandler.newBuilder()
        .handleAll[Rejection] { rejections =>
        mapResponseEntity(prefixEntity) {
          defaultRejectionHandler(rejections) getOrElse complete(StatusCodes.InternalServerError)
        }
      }.handleNotFound {
        mapResponseEntity(prefixEntity) {
          defaultRejectionHandler(Nil) getOrElse complete(StatusCodes.InternalServerError)
        }
      }.result()

    val rawRoutes: Route = buildRoutes(services:_*)

    { ctx: RequestContext => requestCounter.inc(); ctx } andThen handleRejections(myRejectionHandler) { rawRoutes }
  }

  Http().bindAndHandle(routes, host, port) onComplete {
    case Success(bind: Http.ServerBinding) =>
      log.info(s"Server ${bind.localAddress} started")
      val startTime = Platform.currentTime
      metrics.gauge("uptime"){ new Period(Platform.currentTime - startTime).toString(F2) }
    case Failure(e) =>
      log.info("Server could not start, shutting down")
      system.terminate()
  }

  // Start the metrics reporters
  system.actorOf(MetricsReportingManager.props())

  system.eventStream.subscribe(system.actorOf(Props(classOf[DeadLetterMetrics]),"dead-letters-metric"), classOf[DeadLetter])

}