package com.featurefm.riversong.routes

import akka.util.Timeout
import com.featurefm.riversong.health._
import com.featurefm.riversong.health.HealthState.HealthState
import org.joda.time.DateTime
import org.json4s.{JValue, Extraction}
import org.json4s.ext.EnumNameSerializer

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait HealthProvider extends BaseRouting {

  val myFormats = json4sJacksonFormats + new EnumNameSerializer(HealthState)

  implicit val timeout = Timeout(5.seconds)

  val alerts: mutable.Buffer[HealthRollup] = mutable.Buffer()

  /**
   * Run the health checks and return the current system state
   * @return a future to an instance of ``ContainerHealth``
   */
  def runChecks: Future[ContainerHealth] = {

    log.debug("Checking the system's health")

    // Ask for the health of each check
    val future = sendHealthRequests
    val p = Promise[ContainerHealth]()

    future onComplete {
      case Success(checks) =>
        // Rollup alerts for any critical or degraded checks
        checks.foreach(checkStatuses)
        // Rollup the statuses
        val overallHealth = rollupStatuses(alerts)
        alerts.clear()
        p success ContainerHealth(system.name, DateTime.now, overallHealth.state, overallHealth.details, checks)
      case Failure(e) =>
        log.error("An error occurred while fetching the system's health", e)
        p success ContainerHealth(system.name, DateTime.now, HealthState.CRITICAL, e.getMessage, Nil)
    }

    p.future
  }

  /**
   * Rollup the overall status and critical alerts for each check
   * @param checks
   * @return
   */
  private def rollupStatuses(checks: mutable.Buffer[HealthRollup]): HealthRollup = {
    // Check if all checks are running
    if (alerts.isEmpty) {
      HealthRollup(HealthState.OK, "All sub-systems report perfect health")
    }
    else {
      val status = if (checks.forall(c => c.state == HealthState.DEGRADED)) HealthState.DEGRADED else HealthState.CRITICAL
      val details = for (c <- checks) yield c.details

      HealthRollup(status, details.mkString("; "))
    }
  }

  /**
   * Rollup alerts for all checks that have a CRITICAL or DEGRADED state
   * @param info
   */
  private def checkStatuses(info: HealthInfo) {
    def alert(state: HealthState): Boolean = {
      if (state == HealthState.CRITICAL || state == HealthState.DEGRADED) true else false
    }

    def healthDetails(info: HealthInfo): String = {
      info.name + "[" + info.state + "] - " + info.details
    }

    if (info.checks.isEmpty && alert(info.state)) {
      alerts += HealthRollup(info.state, healthDetails(info))
    }
    else if (alert(info.state)) {
      alerts += HealthRollup(info.state, healthDetails(info))
    }
  }

  /**
   * Send off all of the health checks so the system can gather them
   * @return a `Future` which contains a sequence of `HealthInfo`
   */
  private def sendHealthRequests: Future[Seq[HealthInfo]] = {

    val future = Future.traverse(Health(system).getChecks)(h => h.getHealth.mapTo[HealthInfo])

    val p = Promise[Seq[HealthInfo]]()
    future.onComplete({
      case Failure(t) =>
        log.error("Error fetching the system's health health", t)
        p failure t
      case Success(answers) =>
        p success answers
    })

    p.future
  }

  def serialize(health: ContainerHealth): JValue = {
    Extraction.decompose(health)(myFormats)
  }

  case class HealthRollup(state: HealthState, details: String)

}
