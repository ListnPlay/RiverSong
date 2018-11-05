package com.featurefm.riversong.health

import akka.actor.{Actor, ActorLogging}
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.health.HealthState._
import com.featurefm.riversong.metrics.InstrumentedActor
import com.softwaremill.macwire.Wired

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success}

/**
  * Created by yardena on 8/21/16.
  *
  * subscribe to SetHealth events by
  *  system.eventStream.subscribe(self, classOf[SetHealth])
  */
class HealthMonitorActor(healthChecks: List[HealthCheck]) extends Actor with ActorLogging with InstrumentedActor with Configurable {

  val checkHealthInterval = config.getInt("health-check.interval-seconds")

  import context.dispatcher

  system.scheduler.schedule(Duration.Zero, checkHealthInterval.seconds, self, CheckHealth)

  override def receive: Receive = {
    case CheckHealth =>
      Future.firstCompletedOf(Seq(
        akka.pattern.after((checkHealthInterval / 2).max(2).seconds, using = system.scheduler) { Future successful SetHealth(false) },
        Future.sequence(healthChecks.map(_.getHealth))
          .map(x =>
            SetHealth(!x.map(_.state).contains(CRITICAL)))
      )) andThen {
        case Success(r) => context.system.eventStream.publish(r)
        case Failure(f) =>
          log.error(f, "Error checking health")
          context.system.eventStream.publish(SetHealth(false))
      }
  }
}
