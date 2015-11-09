package com.featurefm.riversong.health

import com.featurefm.riversong.health.HealthState.HealthState
import org.joda.time.DateTime


case class ContainerHealth(applicationName: String,
                           time: DateTime,
                           state: HealthState,
                           details: String,
                           checks: Seq[HealthInfo] = Seq.empty) {}
