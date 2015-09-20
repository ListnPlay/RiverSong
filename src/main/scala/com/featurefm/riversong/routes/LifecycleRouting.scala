package com.featurefm.riversong.routes

import akka.actor.ActorSystem
import com.featurefm.riversong.{Json4sProtocol, Message}
import com.featurefm.riversong.metrics.{Metrics, MetricsWriter}
import spray.routing.Directives

/**
 * Created by yardena on 8/12/15.
 */
class LifecycleRouting(implicit val system: ActorSystem) extends Directives with BaseRouting {

  import Json4sProtocol._
  lazy val writer = new MetricsWriter(Metrics(system).metricRegistry)

  def routes: spray.routing.Route =
    path("init") {
      get {
        complete(Message("Server is up"))
      }
    } ~
    path("metrics") {
      get {
        parameters('jvm ? "true", 'pattern.?) { (jvm, pattern) =>
          complete(writer.getMetrics(jvm.toBoolean, pattern))
        }
      }
    }

}
