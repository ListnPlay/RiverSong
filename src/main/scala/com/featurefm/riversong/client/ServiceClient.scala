package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.featurefm.riversong.{Json4sProtocol, Configurable}
import com.featurefm.riversong.message.Message

import scala.concurrent.Future

/**
 * Created by yardena on 11/8/15.
 */
trait ServiceClient extends Configurable with Json4sProtocol {

  val system: ActorSystem

  val serviceName: String

  protected val log = Logging(system, getClass)

  lazy val host = config.getString(s"services.$serviceName.host")
  lazy val port = config.getInt(s"services.$serviceName.port")

  lazy val http = HttpClient.http(host, port)(system)

  implicit lazy val executor = http.executor
  implicit lazy val materializer = http.materializer

  def failWith(response: HttpResponse): Future[Nothing] =
    Unmarshal(response.entity).to[Message] map { m: Message =>
      if (response.status == BadRequest)
        throw new IllegalArgumentException(m.message)
      else
        throw new RuntimeException(m.message)
    }

  def status: Future[Boolean] = http.send(Get("/status")) map { _.status match {
    case OK => true
    case _ => false
  }}

}
