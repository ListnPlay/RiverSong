package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.featurefm.riversong.health.{HealthState, HealthInfo, HealthCheck}
import com.featurefm.riversong.{Json4sProtocol, Configurable}
import com.featurefm.riversong.message.Message

import scala.concurrent.Future
import scala.util.{Try, Failure}

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

  def failWith(response: HttpResponse): Future[Nothing] =
      if (response.status == BadRequest)
        Unmarshal(response.entity).to[Message] map {
          m: Message => throw new IllegalArgumentException(m.message)
        } recover {
          case e => throw new IllegalArgumentException(s"$serviceName-manager returned an error '${response.status.value}'")
        }
      else
        Unmarshal(response.entity).to[Message] map {
          m: Message => throw new RuntimeException(m.message)
        } recover {
          case e => throw new RuntimeException(s"$serviceName-manager returned an error '${response.status.value}'")
        }

  def status: Future[Boolean] = http.send(Get("/status"), "status") map { _.status match {
    case OK => true
    case _ => false
  }}

  override def getHealth: Future[HealthInfo] = status map { res =>
    HealthInfo(HealthState.OK, "")
  } recover { case e =>
    HealthInfo(if (isServiceCritical) HealthState.CRITICAL else HealthState.DEGRADED, s"http://$host:$port ~> ${e.getMessage}")
  }

}
