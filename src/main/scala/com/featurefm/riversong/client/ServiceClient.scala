package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.featurefm.riversong.{Json4sProtocol, Message}
import com.featurefm.riversong.metrics.Instrumented
import org.json4s.jackson.Serialization
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._

import scala.concurrent.Future

/**
 * Created by yardena on 11/1/15.
 */
class ServiceClient private (flow: => Flow[HttpRequest, HttpResponse, Any])(implicit val system: ActorSystem)
  extends Instrumented {

  import Json4sProtocol._

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executor = system.dispatcher
  implicit val serialization = Serialization // or native.Serialization

  val log = Logging(system, getClass)

  def send(request: HttpRequest): Future[HttpResponse] = timeEventually(s"${request.method.value} ${request.uri}") {
    Source.single(request).via(flow).runWith(Sink.head)
  }

  //TODO extract
  def failWith(response: HttpResponse): Future[Nothing] =
    Unmarshal(response.entity).to[Message] map { m: Message =>
      if (response.status == BadRequest)
        throw new IllegalArgumentException(m.message)
      else
        throw new RuntimeException(m.message)
    }

  //TODO extract
  def status: Future[Boolean] = send(Get("/status")) map { _.status match {
    case OK => true
    case _ => false
  }}

}

object ServiceClient {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem): ServiceClient = {
    require(host.startsWith("http://") || host.indexOf("://") < 0, "Protocol must be HTTP")
    new ServiceClient(Http().outgoingConnection(host, port))
  }
  def https(host: String, port: Int = 443)(implicit system: ActorSystem): ServiceClient = {
    require(host.startsWith("https://") || host.indexOf("://") < 0, "Protocol must be HTTPS")
    new ServiceClient(Http().outgoingConnectionTls(host, port))
  }
}
