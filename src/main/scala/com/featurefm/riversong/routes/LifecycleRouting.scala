package com.featurefm.riversong.routes

import java.net.URLDecoder

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Route, Directives}
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.message.Message
import com.featurefm.riversong.metrics.Metrics
import com.typesafe.config.{ConfigObject, ConfigRenderOptions}
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

import scala.util.Try

/**
 * Created by yardena on 8/12/15.
 */
class LifecycleRouting(implicit val system: ActorSystem) extends Directives with BaseRouting with Configurable {

  lazy val writer = new MetricsWriter(Metrics(system).metricRegistry)

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
    pathPrefix("config") {
      pathEndOrSingleSlash {
        get {
          complete {
            time("GET /config") {
              write(config.root())
            }
          }
        }
      } ~
      path(Segments(1, 128)) { p =>
        get {
          time ("GET /config") {
            getConfig(p) match {
              case Some(res) => complete(res)
              case None => complete(StatusCodes.NotFound, None)
            }
          }
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

  private def write(conf: ConfigObject): JValue = parse(conf.render(ConfigRenderOptions.concise()))
  def getConfig(path: List[String]): Option[JValue] = {
    val key = path.map(URLDecoder.decode(_, "UTF-8")).mkString(".")
    if (config.hasPath(key))
      Some(Try(write(config.getObject(key))).getOrElse(path.reverse.head -> matchAny(config.getValue(key).unwrapped)))
    else None
  }

}
