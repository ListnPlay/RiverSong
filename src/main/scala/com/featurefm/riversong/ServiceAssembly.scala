package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.featurefm.riversong.routes.{BaseRouting, LifecycleRouting}

/**
 * Created by yardena on 9/20/15.
 */
abstract class ServiceAssembly(implicit val system: ActorSystem, implicit val mat: Materializer) extends Configurable { // with Instrumented
  import com.softwaremill.macwire._
  lazy val lifecycle: LifecycleRouting = wire[LifecycleRouting]

  def routes: Route //todo use wired instead of this
  // introduce RiverSongRouting trait { def routes: Route } and in MainService do:
  // val wired = wiredInModule(assembly)
  // buildRoutes(wired.lookup(classOf[RiverSongRouting]):_*)

  def buildRoutes(r: BaseRouting*) = r.map(_.routes).reduce(_ ~ _)

//  override lazy val metricBaseName = MetricName(system.name)

}
