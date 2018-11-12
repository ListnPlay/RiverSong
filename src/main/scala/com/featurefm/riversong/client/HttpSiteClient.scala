package com.featurefm.riversong.client

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.{OverflowStrategy, Supervision}
import akka.stream.scaladsl._
import com.featurefm.riversong.metrics.MetricsDefinition
import io.prometheus.client.SimpleTimer

import scala.concurrent.{Future, Promise}

/**
 * Created by yardena on 1/4/16.
 */
class HttpSiteClient private (secure: Boolean = false)
                             (host: String, port: Int = if (secure) 443 else 80,
                              config: Option[ConnectionPoolSettings] = None)
                             (implicit val system: ActorSystem) extends HttpClientInterface {

  protected val log = Logging(system, getClass)

  lazy val name: String = s"$host:$port"

  val httpFlow = if (secure) {
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

  val pool = Source.empty.viaMat(httpFlow)(Keep.right).to(Sink.head).run()

  def shutdown(): Future[Done] =
    pool.shutdown()

  def decider: Supervision.Decider = { //instead of Supervision.resumingDecider
    e =>
      log.error(e, "Error processing event")
      Supervision.Resume
  }

  val timedFlow: FlowType = Flow[InContext[HttpRequest]]
    .map { x =>
      val naming = implicitly[NamedHttpRequest]
      val name = x.context.getOrElse("name", naming(x.unwrap)).toString
      x.with_("timer", new SimpleTimer)
        .with_("method", x.unwrap.method.value)
        .with_("path", name)
        .toTuple
    }
    .via(httpFlow)
    .map { x =>
      val y: ResponseInContext = InContext.fromTuple(x)
      val method = y.get[String]("method")
      val path = y.get[String]("path")
      val code = y.unwrap.map(_.status.value).getOrElse("")
      val time = y.get[SimpleTimer]("timer").elapsedSeconds()
      MetricsDefinition.clientHttpRequestDuration.labels(method, host, path, code).observe(time)
      y
    }
    .addAttributes(supervisionStrategy(decider))

  @deprecated(message = "use timedFlow directly or send(HttpRequest,String)", since = "0.8.3/0.7.5")
  def getTimedFlow(name: String): FlowType =
    Flow.fromFunction((r: InContext[HttpRequest]) => r.with_("name", name)).via(timedFlow)

  private val channel = Source
    .actorRef[InContext[HttpRequest]](10000, OverflowStrategy.dropNew) //todo make buffer size configurable
    .via(timedFlow)
    .map { x =>
      x.get[Promise[HttpResponse]]("promise").complete(x.unwrap)
      x
    }
    .to(Sink.ignore)
    .run

  def send(request: HttpRequest)(implicit naming: NamedHttpRequest): Future[HttpResponse] = {
    val p = Promise[HttpResponse]()
    channel ! InContext.wrap(request).with_("promise", p).with_("name", naming(request))
    p.future
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

