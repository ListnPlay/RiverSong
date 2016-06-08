package com.featurefm.riversong.routes

import akka.util.Timeout
import com.featurefm.riversong.health._
import com.featurefm.riversong.health.HealthState.HealthState
import org.joda.time.DateTime
import org.json4s.{Extraction, JValue}
import org.json4s.ext.EnumNameSerializer

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

trait HealthProvider extends BaseRouting {

  val myFormats = json4sJacksonFormats + new EnumNameSerializer(HealthState)

  implicit val timeout = Timeout(5.seconds)

  /**
   * Run the health checks and return the current system state
    *
    * @return a future to an instance of ``ContainerHealth``
   */
  def runChecks: Future[InternalContainerHealth] = {

    log.debug("Checking the system's health")

    // Ask for the health of each check
    val p = Promise[InternalContainerHealth]()

    sendHealthRequests onComplete {
      case Success(checks) =>
        val alerts: mutable.Buffer[HealthRollup] = mutable.Buffer()
        // Rollup alerts for any critical or degraded checks
        checks.foreach(checkStatuses(alerts))
        // Rollup the statuses
        val overallHealth = rollupStatuses(alerts)
        val res = ContainerHealth(system.name, DateTime.now, overallHealth.state, overallHealth.details, checks)
        p success res
        if (res.state == HealthState.CRITICAL) {
          import org.json4s.jackson.JsonMethods._
          log.warning(compact(render(serialize(res))))
        }
      case Failure(e) =>
        log.error("An error occurred while fetching the system's health", e)
        p success ContainerHealth(system.name, DateTime.now, HealthState.CRITICAL, e.getMessage)
    }

    p.future
  }

  /**
   * Rollup the overall status and critical alerts for each check
   */
  private def rollupStatuses(alerts: mutable.Buffer[HealthRollup]): HealthRollup = {
    // Check if all checks are running
    if (alerts.isEmpty) {
      HealthRollup(HealthState.OK, "All sub-systems report perfect health")
    }
    else {
      val status = if (alerts.forall(c => c.state == HealthState.DEGRADED)) HealthState.DEGRADED else HealthState.CRITICAL
      val details = for (c <- alerts) yield c.details

      HealthRollup(status, details.mkString("; "))
    }
  }

  /**
   * Rollup alerts for all checks that have a CRITICAL or DEGRADED state
   */
  private def checkStatuses(alerts: mutable.Buffer[HealthRollup])(tuple: (String,HealthInfo)) {

    val (name, info) = tuple

    def alert(state: HealthState): Boolean = state == HealthState.CRITICAL || state == HealthState.DEGRADED

    def healthDetails(info: HealthInfo): String = name + "[" + info.state + "] - " + info.details

    if (alert(info.state)) {
      alerts += HealthRollup(info.state, healthDetails(info))
    }
  }

  /**
   * Send off all of the health checks so the system can gather them
    *
    * @return a `Future` which contains a sequence of `HealthInfo`
   */
  private def sendHealthRequests: Future[Seq[(String,HealthInfo)]] = {

    val future = Future.traverse(Health(system).getChecks) { h =>
      Try(h.getHealth).recover{case e => Future.failed(e)}.get map (h.healthCheckName -> _)
    }

    val p = Promise[Seq[(String,HealthInfo)]]()
    future.onComplete({
      case Failure(t) =>
        log.error("Error fetching the system's health health", t)
        p failure t
      case Success(answers) =>
        p success answers
    })

    p.future
  }

  def serialize(health: InternalContainerHealth): JValue = {
    Extraction.decompose(health)(myFormats)
  }

  case class HealthRollup(state: HealthState, details: String)

}
