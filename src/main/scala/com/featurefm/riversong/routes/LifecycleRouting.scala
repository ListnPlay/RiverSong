package com.featurefm.riversong.routes

import java.net.URLDecoder

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, ServiceUnavailable}
import akka.http.scaladsl.server.{Directives, Route}
import com.codahale.metrics.Metric
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.health.{ContainerHealth, GetHealth, HealthState, StatusActor}
import com.featurefm.riversong.message.{Message, Relation}
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

  lazy val prom = new PrometheusReporter(system, config)

  lazy val writer = new MetricsWriter(Metrics(system).metricRegistry)

  lazy val statusActor = system.actorOf(Props[StatusActor])

  val useHealth = config.getBoolean("health-check.use-in-status")

  import nl.grons.metrics.scala.Implicits._

  def uptime: String = {
    val uptimeMetrics = (name: String, m: Metric) => name.endsWith(".uptime")
    metrics.registry.getGauges(uptimeMetrics).values().iterator().next().getValue.toString
  }
  def count: Long = {
    val requestsMetrics = (name: String, m: Metric) => name.endsWith(".requests")
    metrics.registry.getCounters(requestsMetrics).values().iterator().next().getCount
  }

  def okMessage: Message = Try {
    Message(s"Server is up for $uptime and has served $count requests")
  } getOrElse Message("Server is up just now, thanks for checking in")

  def links = Seq(
    "home"    -> Relation("/"),
    "status"  -> Relation("/status"),
    "health"  -> Relation("/health"),
    "config"  -> Relation("/config"),
    "metrics" -> Relation("/metrics")
    )

  import akka.pattern.ask

  def routes: Route =
    pathEndOrSingleSlash {
      get {
        complete {
          okMessage withLinks (links:_*)
        }
      }
    } ~
    path("status") {
      get {
        if (useHealth) {
          onCompleteMeasured() {(statusActor ? GetHealth).mapTo[Boolean]} {
            case Success(true) => complete(Message("Server is up"))
            case Success(false) => complete(StatusCodes.ServiceUnavailable, Message("Server is unavailable"))
            case Failure(e) => complete(StatusCodes.InternalServerError, Message("Server error"))
          }
        } else {
          measured() { complete { Message("Server is up") } }
        }
      }
    } ~
    pathPrefix("config") {
      pathEndOrSingleSlash {
        get {
          complete {
            write(config.root())
          }
        }
      } ~
      path(Segment) { p =>
        get {
          getConfig(p) match {
            case Some(res) => complete(res)
            case None => complete(StatusCodes.NotFound, None)
          }
        }
      } ~
      path(Segments(2, 128)) { p =>
        get {
          getConfig(p) match {
            case Some(res) => complete(res)
            case None => complete(StatusCodes.NotFound, None)
          }
        }
      }
    } ~
    path("metrics") {
      get {
        parameter('name.*) { names =>
          complete {
            HttpResponse(entity = HttpEntity(prom.contentType, prom.renderMetrics(names.toList)))
          }
        }
      }
    } ~
    path("legacy-metrics") {
      get {
        parameters('jvm ? "false", 'pattern.?) { (jvm, pattern) =>
          complete {
            writer.getMetrics(jvm.toBoolean, pattern)
          }
        }
      }
    } ~
    path("health") {
      get {
        onComplete(runChecks) {
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
