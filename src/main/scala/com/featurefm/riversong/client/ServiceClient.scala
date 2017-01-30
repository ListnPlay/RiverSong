package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.featurefm.riversong.health.{HealthCheck, HealthInfo, HealthState}
import com.featurefm.riversong.{Configurable, Json4sProtocol}
import com.featurefm.riversong.message.Message

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by yardena on 11/8/15.
 */
trait ServiceClient extends Configurable with Json4sProtocol with HealthCheck {

  val system: ActorSystem

  val serviceName: String

  def isServiceCritical: Boolean

  override lazy val healthCheckName: String = serviceName

  protected val log = Logging(system, getClass)

  lazy val host = config.getString(s"services.$serviceName.host")
  lazy val port = config.getInt(s"services.$serviceName.port")

  lazy val http = HttpSiteClient(host, port)(system)

  implicit lazy val executor = http.executor
  implicit lazy val materializer = http.materializer

  def statusToException(code: StatusCode): (String) => Exception = msg => code match {
    case BadRequest => new IllegalArgumentException(msg)
    case _ => new RuntimeException(msg)
  }

  def failWith(response: HttpResponse): Future[Nothing] = {
    val createError = statusToException(response.status)
    Unmarshal(response.entity).to[Message] transform (
      { m => throw createError(m.message)},
      { e =>
        response.discardEntityBytes()
        createError(s"$serviceName-manager returned an error '${response.status.value}'")
      }
    )
  }

  val healthCallTimeout = config.getInt("services.call-timeout-ms").milliseconds  //2.seconds

  def status: Future[StatusCode] = http.send(Get("/status"), Timeout(healthCallTimeout)) map { x =>
    x.discardEntityBytes()
    x.status
  }

  private def isDown = if (isServiceCritical) HealthState.CRITICAL else HealthState.DEGRADED

  override def getHealth: Future[HealthInfo] = status map {
    case code if code.isSuccess() => HealthInfo(HealthState.OK, s"http://$host:$port ~> $code")
    case code =>
      HealthInfo(isDown, s"http://$host:$port ~> $code")
  } recover { case e =>
    HealthInfo(isDown, s"http://$host:$port ~> UNREACHABLE", Some(e.getMessage))
  }

  def startSelfHealthWatch(): Unit = {
    val breaker =
      new CircuitBreaker(
        system.scheduler,
        maxFailures = config.getInt("services.max-failures"),
        callTimeout = healthCallTimeout,
        resetTimeout = config.getInt("services.reset-timeout-seconds").seconds //30.seconds
      ).onOpen {
        http.shutdown()
        ()
      }

    val interval = config.getInt("services.health-check-interval-seconds").seconds //30.seconds

    system.scheduler.schedule(interval, interval) {
      breaker.withCircuitBreaker(getHealth.filter(_.state != HealthState.CRITICAL))
    }
  }

}
