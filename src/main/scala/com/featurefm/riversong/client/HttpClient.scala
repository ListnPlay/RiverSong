package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

/**
 * Created by yardena on 11/1/15.
 */
class HttpClient private (flow: => Flow[HttpRequest, HttpResponse, Any], host: String, port: Int)(implicit val system: ActorSystem)
  extends HttpClientInterface {

  protected val log = Logging(system, getClass)

  lazy val name: String = s"$host:$port"

  def send(request: HttpRequest)(implicit naming: NamedHttpRequest): Future[HttpResponse] = {
    Source.single(request).via(flow).runWith(Sink.head)
  }

}

object HttpClient extends HttpClientFactory[HttpClient] with MetricImplicits {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem) = {
    require(host.startsWith("http://") || host.indexOf("://") < 0, "Protocol must be HTTP")
    new HttpClient(Http().outgoingConnection(host, port), host, port)
  }

  def https(host: String, port: Int = 443)(implicit system: ActorSystem) = {
    require(host.startsWith("https://") || host.indexOf("://") < 0, "Protocol must be HTTPS")
    new HttpClient(Http().outgoingConnectionHttps(host, port), host, port)
  }
}
