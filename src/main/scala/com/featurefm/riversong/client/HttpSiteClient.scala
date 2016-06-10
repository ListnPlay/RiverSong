package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision
import akka.stream.scaladsl._
import com.codahale.metrics.Timer
import com.featurefm.riversong.client.InContext.JustRequest

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
 * Created by yardena on 1/4/16.
 */
class HttpSiteClient private (secure: Boolean = false)
                             (host: String, port: Int = if (secure) 443 else 80, config: Option[ConnectionPoolSettings] = None)
                             (implicit val system: ActorSystem) extends HttpClientInterface {

  protected val log = Logging(system, getClass)

  lazy val name: String = s"$host:$port"

  private val httpFlow = if (secure) {
    config match {
      case Some(settings) =>
        Http().cachedHostConnectionPoolHttps[Context](host, port, settings = settings)
      case None =>
        Http().cachedHostConnectionPoolHttps[Context](host, port)
    }
  } else {
    config match {
      case Some(settings) =>
        Http().cachedHostConnectionPool[Context](host, port, settings = settings)
      case None =>
        Http().cachedHostConnectionPool[Context](host, port)
    }
  }

  private val flows = TrieMap[String, FlowType]()

  def getTimedFlow(name: String): FlowType = flows.getOrElseUpdate(name, makeTimedFlow(name))

  private val resumingDecider: Supervision.Decider = { //instead of Supervision.resumingDecider
    case e =>
      log.error(e, "Error processing event")
      Supervision.Resume
  }

  def makeTimedFlow(name: String): FlowType = {

    def attachTimerToRequest(x: RequestInContext): RequestInContext#Tuple = {
      x.with_("timer", metrics.timer(name).timerContext()).toTuple
    }

    def stopTimerReturnRequest(x: ResponseInContext#Tuple): ResponseInContext = {
      val y: ResponseInContext = InContext.fromTuple(x)
      y.get[Timer.Context]("timer").stop()
      y //y.without("timer")
    }

    Flow[InContext[HttpRequest]]
      .map(attachTimerToRequest)
      .via(httpFlow)
      .map(stopTimerReturnRequest)
      .addAttributes(supervisionStrategy(resumingDecider))
  }

  def send(request: HttpRequest)(implicit naming: HttpSiteClient.NamedHttpRequest): Future[HttpResponse] = {
    Source.single[RequestInContext](request).via(getTimedFlow(naming(request))).runWith(Sink.head).map(_.unwrap.get)
  }

}

object HttpSiteClient extends HttpClientFactory[HttpSiteClient] with MetricImplicits {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem) = new HttpSiteClient(secure = false)(host, port)

  def https(host: String, port: Int = 443)(implicit system: ActorSystem) = new HttpSiteClient(secure = true)(host, port)

  def site(host: String, port: Int = 80, config: Option[ConnectionPoolSettings] = None)(implicit system: ActorSystem) =
    new HttpSiteClient(secure = false)(host, port)

  def secureSite(host: String, port: Int = 443, config: Option[ConnectionPoolSettings] = None)(implicit system: ActorSystem) =
    new HttpSiteClient(secure = true)(host, port)

}

