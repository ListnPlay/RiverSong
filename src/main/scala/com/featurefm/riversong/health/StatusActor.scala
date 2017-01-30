package com.featurefm.riversong.health

import akka.actor.{Actor, ActorLogging}
import com.featurefm.riversong.metrics.InstrumentedActor

/**
  * Created by yardena on 1/30/17.
  */
class StatusActor extends Actor with ActorLogging with InstrumentedActor {

  import com.featurefm.riversong.health.HealthState._

  var state: HealthState = OK

  override def preStart(): Unit = {
    system.eventStream.subscribe(self, classOf[SetHealth])
  }

  override def receive: Receive = {
    case SetHealth(x) if x =>
      state = OK
    case SetHealth(x) if state == OK =>
      log.warning(s"${system.name} service is unhealthy")
      state = DEGRADED
    case SetHealth(x) =>
      log.error(s"${system.name} service is unhealthy")
      state = CRITICAL
    case GetHealth =>
      sender() ! !(state == CRITICAL)
  }

}
