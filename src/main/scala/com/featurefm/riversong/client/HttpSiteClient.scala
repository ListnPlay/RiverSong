package com.featurefm.riversong.client

import akka.actor.Actor.Receive
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorSystem, Kill, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult, Supervision}
import akka.stream.scaladsl._
import com.codahale.metrics.Timer

import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}

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

  def decider: Supervision.Decider = { //instead of Supervision.resumingDecider
    e =>
      log.error(e, "Error processing event")
      Supervision.Resume
  }

  val timedFlow: FlowType = Flow[InContext[HttpRequest]]
    .map { x =>
      val naming = implicitly[NamedHttpRequest]
      val name = x.context.getOrElse("name", naming(x.unwrap)).toString
      x.with_("timer", metrics.timer(name).timerContext()).toTuple
    }
    .viaMat(httpFlow)(Keep.right)
    .map { x =>
      val y: ResponseInContext = InContext.fromTuple(x)
      y.get[Timer.Context]("timer").stop()
      y
    }
    .addAttributes(supervisionStrategy(decider))

  val channelRef = system.actorOf(Props(classOf[HttpChannelActor], timedFlow, name))

  def shutdown(): Unit =
    channelRef ! Kill //pool.shutdown()

  @deprecated(message = "use timedFlow directly or send(HttpRequest,String)", since = "0.8.3/0.7.5")
  def getTimedFlow(name: String): FlowType =
    Flow.fromFunction((r: InContext[HttpRequest]) => r.with_("name", name)).via(timedFlow)

  def send(request: HttpRequest)(implicit naming: NamedHttpRequest): Future[HttpResponse] = {
    val p = Promise[HttpResponse]()
    channelRef ! InContext.wrap(request).with_("promise", p).with_("name", naming(request)).toTuple
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

class HttpChannelActor(flow: Flow[InContext[HttpRequest], InContext[Try[HttpResponse]], HostConnectionPool], endpoint: String) extends Actor with ActorLogging with MetricImplicits {

  import scala.concurrent.duration._

  implicit val materializer = ActorMaterializer()
  import context.dispatcher

  private val pool = Source.empty.viaMat(flow)(Keep.right).to(Sink.head).run()

  private val channel = Source
    .queue[InContext[HttpRequest]](10000, OverflowStrategy.dropNew) //todo make buffer size configurable
    .via(flow)
    .map(x =>
      x.context.get("promise").foreach(
        _.asInstanceOf[Promise[HttpResponse]].complete(x.unwrap)
      )
    )
    .to(Sink.ignore)
    .run

  channel
    .watchCompletion()
    .onComplete {
      case Success(_) =>
        log.warning(s"http channel $endpoint closed")
      case Failure(e) =>
        log.error(e, s"http channel $endpoint failed, restarting it")
        self ! Kill
    }

  override def receive: Receive = {
    case (req: HttpRequest, ctx: Context) if ctx.get("promise").exists(_.isInstanceOf[Promise[_]]) =>
      val in: InContext[HttpRequest] = InContext.fromReqTuple((req, ctx))
      channel.offer(in).onComplete {
        case Failure(e) =>
          ctx("promise").asInstanceOf[Promise[HttpResponse]].failure(e)
        case Success(QueueOfferResult.Dropped) =>
          ctx("promise").asInstanceOf[Promise[HttpResponse]].failure(new RuntimeException(s"Too many requests to $endpoint"))
        case Success(QueueOfferResult.Failure(e)) =>
          ctx("promise").asInstanceOf[Promise[HttpResponse]].failure(e)
        case Success(QueueOfferResult.QueueClosed) =>
          ctx("promise").asInstanceOf[Promise[HttpResponse]].failure(new RuntimeException(s"Queue closed: $endpoint"))
        case _ => //do nothing
      }
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.warning(s"Restarting connection to $endpoint")
    Await.result(pool.shutdown(), 2.seconds)
    super.postStop()
  }
}

