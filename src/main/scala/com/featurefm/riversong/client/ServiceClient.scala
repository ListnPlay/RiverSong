package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.featurefm.riversong.metrics.Instrumented
import org.json4s.jackson.Serialization
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._

import scala.concurrent.Future

/**
 * Created by yardena on 11/1/15.
 */
class ServiceClient(host: String, port: Int)(implicit val system: ActorSystem, implicit val mat: Materializer) extends Instrumented {

  implicit val executor = system.dispatcher
  implicit val serialization = Serialization // or native.Serialization

  val log = Logging(system, getClass)

  private lazy val connectionFlow = Http().outgoingConnection(host, port) //Flow[HttpRequest, HttpResponse, Any]

  def send(request: HttpRequest): Future[HttpResponse] = timeEventually(s"${request.method.value} ${request.uri}") {
    Source.single(request).via(connectionFlow).runWith(Sink.head)
  }

  def status: Future[Boolean] = send(Get("/status")) map { _.status match {
    case OK => true
    case _ => false
  }}

}
