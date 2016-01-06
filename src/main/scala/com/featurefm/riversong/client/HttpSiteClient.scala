package com.featurefm.riversong.client

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl._
import com.codahale.metrics.Timer
import com.featurefm.riversong.Json4sProtocol

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.Try

/**
 * Created by yardena on 1/4/16.
 */
class HttpSiteClient private (secure: Boolean = false)(host: String, port: Int = if (secure) 443 else 80)
                             (implicit val system: ActorSystem) extends HttpClientInterface with Json4sProtocol {

  protected val log = Logging(system, getClass)

  lazy val name: String = s"$host:$port"

  private val httpFlow = if (secure) Http().cachedHostConnectionPoolTls[Timer.Context](host, port)
                    else Http().cachedHostConnectionPool[Timer.Context](host, port)

  val flows = TrieMap[String,Flow[HttpRequest, Try[HttpResponse], Http.HostConnectionPool]]()

  def getTimedFlow(name: String) = flows.getOrElseUpdate(name, makeTimedFlow(name))

  def makeTimedFlow(name: String) = {
    val req = (r: HttpRequest) => (r, metrics.timer(name).timerContext())
    val res = (t: (Try[HttpResponse], Timer.Context)) => { t._2.stop(); t._1 }

    BidiFlow.fromFunctions(req, res).joinMat(httpFlow)(Keep.right)
  }

  def send(request: HttpRequest)(implicit naming: HttpSiteClient.NamedHttpRequest): Future[HttpResponse] = {
    Source.single(request).via(getTimedFlow(naming(request))).runWith(Sink.head).map(_.get)
  }

}

object HttpSiteClient extends HttpClientFactory with MetricImplicits {

  def http(host: String, port: Int = 80)(implicit system: ActorSystem) = new HttpSiteClient(secure = false)(host, port)

  def https(host: String, port: Int = 443)(implicit system: ActorSystem) = new HttpSiteClient(secure = true)(host, port)
}
