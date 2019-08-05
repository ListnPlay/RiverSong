package com.featurefm.riversong.health

import akka.actor.ActorSystem
import akka.event.Logging

trait HealthCheckWithCritical extends HealthCheck {

  val system: ActorSystem

  protected val log = Logging(system, getClass)

  val serviceName: String

  def isServiceCritical: Boolean

  override lazy val healthCheckName: String = serviceName

  protected def isDown = if (isServiceCritical) HealthState.CRITICAL else HealthState.DEGRADED

}
