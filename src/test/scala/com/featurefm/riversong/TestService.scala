package com.featurefm.riversong

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.server.Route
import com.featurefm.riversong.health._
import com.featurefm.riversong.metrics.InstrumentedActor
import com.featurefm.riversong.routes.BaseRouting

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by yardena on 11/9/15.
 */
object TestService extends MainService {
  override lazy val assembly = new ServiceAssembly with MyAssembly

  override def services = super.services :+ assembly.ping

}

trait MyAssembly extends ServiceAssembly {
  import com.softwaremill.macwire._

  lazy val foo: FooService = timed(wire[FooService])

  lazy val ping: Ping = wire[Ping]

  Health().addCheck(new HealthCheck {
    override lazy val healthCheckName: String = "test"
    override def getHealth: Future[HealthInfo] = Future successful HealthInfo(HealthState.OK, "everything is fine")
  })

}

class MyActor extends Actor with ActorLogging with InstrumentedActor {
  val m = metrics.meter("messages")
  val g = gauge("messages_count") {
    m.count
  }
  override def receive: Receive = {
    case _ => m.mark()
  }

}

class Ping(foo: FooService)(implicit val system: ActorSystem) extends BaseRouting {
  var actor = system.actorOf(Props[MyActor])

  override def routes: Route = {
    path("ping") {
      get {
        actor ! "ping"
        complete("pong")
      }
    } ~
    path("restart") {
      get {
        system stop actor
        actor = system.actorOf(Props[MyActor])
        complete("done")
      }
    } ~
    path("foo") {
      onComplete(foo.testMe("World")) {
        case Success(s) => complete(s)
        case Failure(e) => complete("Error")
      }
    }
  }
}

class FooService {
  def testMe(s: String): Future[String] = Future successful s"Hello $s!"
}
