package com.featurefm.riversong.client

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented
import nl.grons.metrics.scala.MetricName

import scala.concurrent.Future
import scala.util.Try

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

  import akka.pattern.after
  def send(request: HttpRequest, timeout: Timeout, requestName: Option[String] = None): Future[HttpResponse] = Future.firstCompletedOf(List(
    send(request, requestName.getOrElse(MethodAndPathNamedRequest(request))),
    after(timeout.duration, using = system.scheduler)(Future failed new TimeoutException(s"Request ${requestName.getOrElse(MethodAndPathNamedRequest(request))} to service $name has timed out after ${timeout.duration.toString()}"))
  ))

  def parse[T](f: HttpResponse => Future[T])(response: HttpResponse): Future[T] = f(response) andThen {
    case _ => Try(response.discardEntityBytes())
  }

}

trait HttpClientFactory[C <: HttpClientInterface] {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem): C
  def https(host: String, port: Int = 443)(implicit system: ActorSystem): C

  def apply(host: String, port: Int = 80)(implicit system: ActorSystem): C = http(host, port)
  def secure(host: String, port: Int = 443)(implicit system: ActorSystem): C = https(host, port)

}
