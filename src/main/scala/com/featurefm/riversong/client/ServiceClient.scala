package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, ResponseEntity, StatusCode}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.pattern.CircuitBreaker
import akka.stream.Materializer
import akka.util.Timeout
import com.featurefm.riversong.health.{HealthCheckWithCritical, HealthInfo, HealthState}
import com.featurefm.riversong.message.Message
import com.featurefm.riversong.{Configurable, Json4sProtocol}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by yardena on 11/8/15.
 */
trait ServiceClient extends Configurable with Json4sProtocol with HealthCheckWithCritical {

  lazy val host = config.getString(s"services.$serviceName.host")
  lazy val port = config.getInt(s"services.$serviceName.port")

  val system: ActorSystem
  val log = Logging(system, getClass.getName)

  lazy val http = HttpSiteClient(host, port)(system)

  implicit lazy val executor = http.executor
  implicit lazy val materializer = http.materializer

  def statusToException(code: StatusCode): (String) => Exception = msg => code match {
    case BadRequest => new IllegalArgumentException(msg)
    case _ => new RuntimeException(msg)
  }

  def send(request: HttpRequest, timeout: FiniteDuration, requestName: Option[String] = None): Future[HttpResponse] =
    http.send(request, Timeout(timeout), requestName)

  def readAs[T](response: ResponseEntity)
               (implicit um: Unmarshaller[ResponseEntity, T], ec: ExecutionContext = null, mat: Materializer): Future[T] =
    http.readAs[T](response)

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

  override def getHealth: Future[HealthInfo] = status map {
    case code if code.isSuccess() => HealthInfo(HealthState.OK, s"http://$host:$port ~> $code")
    case code =>
      HealthInfo(isDown, s"http://$host:$port ~> $code")
  } recover { case e =>
    HealthInfo(isDown, s"http://$host:$port ~> UNREACHABLE", Some(e.toString))
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
