package com.featurefm.riversong.health

import scala.concurrent.Future

trait HealthCheck {

  lazy val healthCheckName = getClass.getSimpleName

  /**
   * Fetch the health for this registered checker.
   * @return returns a future to the health information
   */
  def getHealth: Future[HealthInfo]

  lazy val isStatusAware: Boolean = false
}
