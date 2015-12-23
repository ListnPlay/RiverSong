package com.featurefm.riversong.health

import com.featurefm.riversong.health.HealthState.HealthState
import org.joda.time.DateTime
import HealthState._

case class InternalContainerHealth(applicationName: String,
                           time: DateTime,
                           state: HealthState,
                           details: String,
                           checks: Seq[NamedHealthInfo]) {}

object ContainerHealth {
  def apply(applicationName: String,
            time: DateTime,
            state: HealthState,
            details: String,
            checks: Seq[(String,HealthInfo)] = Seq.empty): InternalContainerHealth = InternalContainerHealth (
              applicationName, time, state, details,
              checks = for ((n,i) <- checks) yield  NamedHealthInfo(n, i)
  )
}

private [health] case class NamedHealthInfo(name: String,
                      state: HealthState = OK,
                      details: String,
                      extra: Option[AnyRef] = None,
                      checks: Seq[NamedHealthInfo] = List.empty)

private [health] object NamedHealthInfo {
  def apply(name: String, info: HealthInfo): NamedHealthInfo = NamedHealthInfo(
    name, info.state, info.details, info.extra,
    checks = for ((n,i) <- info.checks) yield NamedHealthInfo(n, i)
  )
}
