package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented
import nl.grons.metrics.scala.MetricName

import scala.concurrent.Future

/**
 * Created by yardena on 11/1/15.
 */
class HttpClient private (flow: => Flow[HttpRequest, HttpResponse, Any], host: String, port: Int)(implicit val system: ActorSystem)
  extends Json4sProtocol with Instrumented {

  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  protected val log = Logging(system, getClass)

  override lazy val metricBaseName: MetricName = MetricName(this.getClass.getSimpleName, s"$host:$port")

  def send(request: HttpRequest, requestName: String = "*"): Future[HttpResponse] = timeEventually(s"${request.method.value} $requestName") {
    Source.single(request).via(flow).runWith(Sink.head)
  }

}

object HttpClient {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem): HttpClient = {
    require(host.startsWith("http://") || host.indexOf("://") < 0, "Protocol must be HTTP")
    new HttpClient(Http().outgoingConnection(host, port), host, port)
  }
  def https(host: String, port: Int = 443)(implicit system: ActorSystem): HttpClient = {
    require(host.startsWith("https://") || host.indexOf("://") < 0, "Protocol must be HTTPS")
    new HttpClient(Http().outgoingConnectionTls(host, port), host, port)
  }
}
