package com.featurefm.riversong.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Route, Directives}
import com.featurefm.riversong.{Json4sProtocol, Message}
import com.featurefm.riversong.metrics.{Metrics, MetricsWriter}
import org.json4s.jackson.Serialization

/**
 * Created by yardena on 8/12/15.
 */
class LifecycleRouting(implicit val system: ActorSystem) extends Directives with BaseRouting {

  lazy val writer = new MetricsWriter(Metrics(system).metricRegistry)

  import Json4sProtocol._

  val statusChecks = metrics.meter("GET /status")

  def routes: Route =
    path("status") {
      get {
        complete {
          statusChecks.mark()
          Message("Server is up")
        }
      }
    } ~
    path("metrics") {
      get {
        parameters('jvm ? "true", 'pattern.?) { (jvm, pattern) =>
          complete {
            time("GET /metrics") {
              writer.getMetrics(jvm.toBoolean, pattern)
            }
          }
        }
      }
    }

}
