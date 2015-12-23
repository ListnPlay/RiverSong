package com.featurefm.riversong

import akka.actor.{ActorLogging, Props, Actor, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import com.featurefm.riversong.health._
import com.featurefm.riversong.metrics.InstrumentedActor
import com.featurefm.riversong.routes.BaseRouting

import scala.concurrent.Future

/**
 * Created by yardena on 11/9/15.
 */
object TestService extends MainService {
  override lazy val assembly: ServiceAssembly = new ServiceAssembly() {

    lazy val ping = new Ping

    override def routes: Route = lifecycle.routes ~ ping.routes

    Health(TestService.this.system).addCheck(new HealthCheck {
      override val healthCheckName: String = "test"
      override def getHealth: Future[HealthInfo] = Future successful HealthInfo(HealthState.OK, "everything is fine")
    })

  }
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

class Ping(implicit val system: ActorSystem) extends BaseRouting {
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
    }
  }
}
