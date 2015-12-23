package com.featurefm.riversong.health

object HealthState extends Enumeration {
  type HealthState = Value
  val OK, DEGRADED, CRITICAL = Value
}

import HealthState._

/**
 * This message is used to ask for health information
 */
case object CheckHealth

/**
 * The health check is used to define a subset of functionality that
 * can be asked of it's state. It can contain optional child checks.
 * @param state The current state of the check (defaults to OK)
 * @param extra Any extra information
 */
case class HealthInfo(state: HealthState = OK,
                      details: String,
                      extra: Option[AnyRef] = None,
                      checks: Seq[(String,HealthInfo)] = List.empty)
