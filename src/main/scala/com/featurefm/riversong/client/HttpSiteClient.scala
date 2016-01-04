package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented
import nl.grons.metrics.scala.MetricName

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by yardena on 1/4/16.
 */
class HttpSiteClient private (secure: Boolean = false)(host: String, port: Int = if (secure) 443 else 80)
                    (implicit val system: ActorSystem) extends Json4sProtocol with Instrumented {

  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  protected val log = Logging(system, getClass)
  override lazy val metricBaseName: MetricName = MetricName(this.getClass.getSimpleName, s"$host:$port")

  val flow = if (secure) Http().cachedHostConnectionPoolTls[Int](host, port) else Http().cachedHostConnectionPool[Int](host, port)

  def send(request: HttpRequest, requestName: String = "*"): Future[HttpResponse] = timeEventually(s"${request.method.value} $requestName") {
    Source.single(request -> 0).via(flow).runWith(Sink.head) map {
      case (Success(res), i) => res
      case (Failure(e)  , i) => throw e
    }
  }

}

object HttpSiteClient {

  def apply(host: String, port: Int = 80)(implicit system: ActorSystem): HttpSiteClient = new HttpSiteClient(secure = false)(host, port)
  def http(host: String, port: Int = 80)(implicit system: ActorSystem): HttpSiteClient = new HttpSiteClient(secure = false)(host, port)

  def secure(host: String, port: Int = 443)(implicit system: ActorSystem): HttpSiteClient = new HttpSiteClient(secure = true)(host, port)
  def https(host: String, port: Int = 443)(implicit system: ActorSystem): HttpSiteClient = new HttpSiteClient(secure = true)(host, port)
}
