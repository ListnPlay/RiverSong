package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.ActorMaterializer
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented
import nl.grons.metrics.scala.MetricName

import scala.concurrent.Future

/**
  * Created by yardena on 1/6/16.
  */
trait HttpClientInterface extends Json4sProtocol with Instrumented with MetricImplicits {

  implicit val system: ActorSystem
  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  def name: String

  override lazy val metricBaseName: MetricName = MetricName(this.getClass.getSimpleName, name)

  def send(request: HttpRequest)(implicit naming: NamedHttpRequest): Future[HttpResponse]
  def send(request: HttpRequest, requestName: String): Future[HttpResponse] = send(request)(FixedNaming(requestName))

}

trait HttpClientFactory[C <: HttpClientInterface] {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem): C
  def https(host: String, port: Int = 443)(implicit system: ActorSystem): C

  def apply(host: String, port: Int = 80)(implicit system: ActorSystem): C = http(host, port)
  def secure(host: String, port: Int = 443)(implicit system: ActorSystem): C = https(host, port)

}
