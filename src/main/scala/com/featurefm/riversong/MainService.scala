package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.event.Logging
import com.featurefm.riversong.metrics.reporting.MetricsReportingManager
import spray.http.{HttpResponse, HttpRequest}
import spray.routing.SimpleRoutingApp
import spray.routing.directives.LogEntry

/**
 * Created by yardena on 9/20/15.
 */
abstract class MainService(val name: String = "Spoilers") extends SimpleRoutingApp with Configurable { self: App =>
  implicit val system = ActorSystem(name)
  val log = Logging(system, getClass)

  val host = config.getString("spray.listen_ip")
  val port = config.getInt("spray.listen_port")

  def assembly:ServiceAssembly

  //from https://github.com/knoldus/spray-akka-starter/blob/master/src/main/scala/com/knoldus/hub/StartHub.scala
  private def requestMethodAndResponseStatusAsInfo(req: HttpRequest): Any => Option[LogEntry] = {
    case res: HttpResponse =>
      //      val verbose = config.getBoolean("spray.request_log_verbose")
      Some(LogEntry(
        //        if (verbose) s"${req.method} ${req.uri} ${req.entity.data.asString} ~> ${res.message.status} ${res.entity.data.asString}"
        //        else
        s"${req.method} ${req.uri} ~> ${res.message.status}", Logging.InfoLevel))
    case _ => None // other kind of responses
  }

  startServer(interface = host, port = port) {
    val rawRoutes = assembly.routes
    if (config.getBoolean("spray.request_log"))
      logRequestResponse(requestMethodAndResponseStatusAsInfo _)(rawRoutes)
    else
      rawRoutes
  }

  // Start the metrics reporters
  system.actorOf(MetricsReportingManager.props())

}
