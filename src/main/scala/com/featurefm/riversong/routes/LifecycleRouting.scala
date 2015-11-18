package com.featurefm.riversong.routes

import java.net.URLDecoder

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, ServiceUnavailable}
import akka.http.scaladsl.server.{Route, Directives}
import com.codahale.metrics.Metric
import com.featurefm.riversong.{ServiceAssembly, Configurable}
import com.featurefm.riversong.health.{ContainerHealth, HealthState}
import com.featurefm.riversong.message.Message
import com.featurefm.riversong.metrics.Metrics
import com.typesafe.config.{ConfigObject, ConfigRenderOptions}
import org.joda.time.DateTime
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

import scala.util.{Failure, Success, Try}

/**
 * Created by yardena on 8/12/15.
 */
class LifecycleRouting(implicit val system: ActorSystem) extends Directives
        with BaseRouting with Configurable with HealthProvider {

  lazy val writer = new MetricsWriter(Metrics(system).metricRegistry)

  import nl.grons.metrics.scala.Implicits._

  private[this] val uptimeMetrics = (name: String, m: Metric) => name.endsWith(".uptime")
  private[this] val requestsMetrics = (name: String, m: Metric) => name.endsWith(".requests")

  def uptime: String = {
    metrics.registry.getGauges(uptimeMetrics).values().iterator().next().getValue.toString
  }
  def count: Long = {
    metrics.registry.getCounters(requestsMetrics).values().iterator().next().getCount
  }

  def okMessage: Message = Try {
    Message(s"Server is up for $uptime and has served $count requests")
  } getOrElse Message("Server is up just now, thanks for checking in")

  def routes: Route =
    path("status") {
      get {
        measured() {
          complete {
            okMessage
          }
        }
      }
    } ~
    pathPrefix("config") {
      pathEndOrSingleSlash {
        get {
          measured() {
            complete {
              write(config.root())
            }
          }
        }
      } ~
      path(Segment) { p =>
        get {
          measured ("GET /config") {
            getConfig(p) match {
              case Some(res) => complete(res)
              case None => complete(StatusCodes.NotFound, None)
            }
          }
        }
      } ~
      path(Segments(2, 128)) { p =>
        get {
          measured ("GET /config") {
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
          measured () {
            complete {
              writer.getMetrics(jvm.toBoolean, pattern)
            }
          }
        }
      }
    } ~
    path("health") {
      get {
        onCompleteMeasured () (runChecks) {
          case Success(check) =>
            check.state match {
              case HealthState.OK => complete(serialize(check))
              case HealthState.DEGRADED => complete(serialize(check))
              case HealthState.CRITICAL => complete(ServiceUnavailable, serialize(check))
            }

          case Failure(t) =>
            log.error("An error occurred while fetching the system's health", t)
            complete(InternalServerError, ContainerHealth(system.name, DateTime.now, HealthState.CRITICAL, t.getMessage, Nil))
        }
      }
    }

  private def write(conf: ConfigObject): JValue = parse(conf.render(ConfigRenderOptions.concise()))

  def getConfig(key: String): Option[JValue] = {
    if (config.hasPath(key))
      Some(Try(write(config.getObject(key))).getOrElse(key.substring(key.lastIndexOf('.') + 1) -> matchAny(config.getValue(key).unwrapped)))
    else None
  }
  def getConfig(path: List[String]): Option[JValue] = {
    val key = path.map(URLDecoder.decode(_, "UTF-8")).mkString(".")
    getConfig(key)
  }

}
