import akka.actor.{ActorSystem, Cancellable}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object OutputTruncationHttps extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val log = Logging(system, this.getClass)

  val connectionPool = Http().cachedHostConnectionPoolHttps[Int]("api.deezer.com", 443)
  //settings = ConnectionPoolSettings("akka.http.host-connection-pool.idle-timeout = 1s")

  Source.tick(0.seconds, 7.seconds, Unit)
    .scan(0)((prev, _) => prev + 1)
    .map(attempt => Get("/") -> attempt)
    .via(connectionPool)
    .mapAsync(1) {
      case (Success(r), attempt) =>
        Unmarshal(r.entity).to[String].map(_ => s"Succesfully unmarshalled" -> attempt)
      case (Failure(e), attempt) =>
        Future.successful(s"No response obtained for request $attempt. ${e.getMessage}" -> attempt)
    }
    .runForeach {
      case (msg, attempt) => log.info("Materialized #{}:{}",attempt, msg)
    }
}