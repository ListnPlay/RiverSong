package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.featurefm.riversong.metrics.Instrumented
import org.json4s.jackson.Serialization
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._

import scala.concurrent.Future

/**
 * Created by yardena on 11/1/15.
 */
class ServiceClient private (flow: => Flow[HttpRequest, HttpResponse, Any])(implicit val system: ActorSystem, implicit val mat: Materializer)
  extends Instrumented {

//  def this(host: String, port: Int) {
//    this(Http().outgoingConnection(host, port))
//  }

  implicit val executor = system.dispatcher
  implicit val serialization = Serialization // or native.Serialization

  val log = Logging(system, getClass)

  def send(request: HttpRequest): Future[HttpResponse] = timeEventually(s"${request.method.value} ${request.uri}") {
    Source.single(request).via(flow).runWith(Sink.head)
  }

  def status: Future[Boolean] = send(Get("/status")) map { _.status match {
    case OK => true
    case _ => false
  }}

}

object ServiceClient {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem, mat: Materializer): ServiceClient = {
    require(host.startsWith("http://") || host.indexOf("://") < 0, "Protocol must be HTTP")
    new ServiceClient(Http().outgoingConnection(host, port))
  }
  def https(host: String, port: Int = 443)(implicit system: ActorSystem, mat: Materializer): ServiceClient = {
    require(host.startsWith("https://") || host.indexOf("://") < 0, "Protocol must be HTTPS")
    new ServiceClient(Http().outgoingConnectionTls(host, port))
  }
}
