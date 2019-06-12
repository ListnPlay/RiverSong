package com.featurefm.riversong.health

import akka.actor.{Actor, ActorLogging}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.featurefm.riversong.{Configurable, Json4sProtocol}
import com.featurefm.riversong.health.HealthState._
import com.featurefm.riversong.metrics.InstrumentedActor
import com.softwaremill.macwire.Wired
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.JObject
import org.json4s.jackson.JsonMethods

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success}

/**
  * Created by yardena on 8/21/16.
  *
  * subscribe to SetHealth events by
  *  system.eventStream.subscribe(self, classOf[SetHealth])
  */
class HealthMonitorActor(healthChecks: List[HealthCheck]) extends Actor with ActorLogging with InstrumentedActor with Configurable {

  val checkHealthInterval = config.getInt("health-check.interval-seconds")

  import context.dispatcher

  implicit val mat = ActorMaterializer()

  system.scheduler.schedule(Duration.Zero, checkHealthInterval.seconds, self, CheckHealth)

  override def receive: Receive = {
    case CheckHealth =>
      val timeOutAll = (checkHealthInterval - 1).max(2)
      val timeOutOne = (checkHealthInterval / 2).max(1)
      Source
        .fromIterator(() => healthChecks.iterator)
        .flatMapMerge(4, { s =>
          Source
            .fromFuture(s.getHealth)
            .completionTimeout(timeOutOne.seconds)
            .recover {
              case _: scala.concurrent.TimeoutException => HealthInfo(HealthState.CRITICAL, s"Timed out after $timeOutOne seconds")
              case e => HealthInfo(HealthState.CRITICAL, e.toString)
            }
            .map(s.healthCheckName -> _)
        })
        .takeWithin(timeOutAll.seconds)
        .runFold(List.empty[(String, HealthInfo)])(_ :+ _)
        .map { res =>
          val problems = res.filter{ case (_, info) => info.state == CRITICAL }
          if (problems.nonEmpty) {
            val msg = problems.map{ case (service, info) =>
              info.extra match {
                case Some(x: JObject) =>
                  s"'$service':{'error':'${info.details}','details': ${JsonMethods.compact(x)}}"
                case Some(x) => s"'$service':{'error':'${info.details}','details':'$x'}"
                case _ => s"'$service':'${info.details}'"
              }
            }.mkString("{", ",", "}")
            log.warning(s"Health-check failed for: $msg")
          }
          if (res.size < healthChecks.size) {
            val missing = healthChecks.map(_.healthCheckName).toSet.diff(res.map(_._1).toSet)
            log.error(s"Could not get health of following services within $timeOutAll seconds: ${missing.mkString("[",",","]")}")
            SetHealth(false)
          } else {
            SetHealth(problems.isEmpty)
          }
        } andThen {
          case Success(r) =>
            context.system.eventStream.publish(r)
          case Failure(f) => //should never happen
            log.error(f, "Error checking health")
            context.system.eventStream.publish(SetHealth(false))
      }
  }
}
