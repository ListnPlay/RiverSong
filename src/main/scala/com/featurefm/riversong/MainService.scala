package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.event.Logging
import com.featurefm.riversong.metrics.reporting.MetricsReportingManager
import spray.routing.SimpleRoutingApp

/**
 * Created by yardena on 9/20/15.
 */
abstract class MainService(val name: String = "Spoilers") extends SimpleRoutingApp with Configurable { self: App =>
  implicit val system = ActorSystem(name)
  val log = Logging(system, getClass)

  val host = config.getString("spray.listen_ip")
  val port = config.getInt("spray.listen_port")

  def assembly:ServiceAssembly

  startServer(interface = host, port = port) {
    assembly.routes
  }

  // Start the metrics reporters
  system.actorOf(MetricsReportingManager.props())

}
