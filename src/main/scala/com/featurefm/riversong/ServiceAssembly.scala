package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.featurefm.riversong.metrics.Instrumented
import com.featurefm.riversong.routes.{BaseRouting, LifecycleRouting}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import nl.grons.metrics.scala.MetricName

/**
 * Created by yardena on 9/20/15.
 */
abstract class ServiceAssembly(implicit val system: ActorSystem, implicit val mat: Materializer) extends Configurable with Instrumented {
  import com.softwaremill.macwire._
  lazy val lifecycle: LifecycleRouting = wire[LifecycleRouting]

  def routes: Route

  def buildRoutes(r: BaseRouting*) = r.map(_.routes).reduce(_ ~ _)

  override lazy val metricBaseName = MetricName(system.name)

}
