package com.featurefm.riversong.health

trait HealthCheckWithCritical extends HealthCheck {

  val serviceName: String

  def isServiceCritical: Boolean

  override lazy val healthCheckName: String = serviceName

  protected def isDown: HealthState.Value = if (isServiceCritical) HealthState.CRITICAL else HealthState.DEGRADED

}
