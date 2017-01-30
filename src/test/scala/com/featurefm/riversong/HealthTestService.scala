package com.featurefm.riversong

import com.featurefm.riversong.health.HealthState.{CRITICAL, DEGRADED, OK}
import com.featurefm.riversong.health.{HealthCheck, HealthInfo, HealthState}
import com.featurefm.riversong.routes.RiverSongRouting
import com.softwaremill.macwire._

import scala.concurrent.Future

/**
  * Created by yardena on 1/30/17.
  */
object HealthTestService extends MainService {
  override lazy val assembly = new ServiceAssembly with SickAssembly
  val wired: Wired = wiredInModule(assembly)
  override def services = wired.lookup(classOf[RiverSongRouting])
  registerHealthChecks(wired)

}

trait SickAssembly extends ServiceAssembly {
  import com.softwaremill.macwire._

  lazy val cold: Cold = wire[Cold]
  lazy val flue: Flue = wire[Flue]

}


class Cold extends HealthCheck {
  override lazy val healthCheckName: String = "cold"
  override def getHealth: Future[HealthInfo] = Future successful HealthInfo(DEGRADED, "sneeze")
}

class Flue extends HealthCheck {
  override lazy val healthCheckName: String = "flue"
  override def getHealth: Future[HealthInfo] = Future successful HealthInfo(CRITICAL, "cough")
}
