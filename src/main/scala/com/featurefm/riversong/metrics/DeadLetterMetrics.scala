package com.featurefm.riversong.metrics

import akka.actor.{DeadLetter, ActorSystem, Actor}
import nl.grons.metrics.scala.MetricName

/**
 * Created by yardena on 12/15/15.
 */
class DeadLetterMetrics extends Actor with Instrumented {
  override implicit lazy val system: ActorSystem = context.system
  override lazy val metricBaseName: MetricName = MetricName("dead-letters")

  private def getSafeClassName(obj: Any): String = {
    try {
      val simpleName = obj.getClass.getSimpleName
      if (simpleName != null && simpleName.nonEmpty) {
        simpleName
      } else {
        obj.getClass.getName.split('.').lastOption.getOrElse("UnknownClass")
      }
    } catch {
      case _: InternalError | _: Exception =>
        // Fallback to full class name, then extract the last part
        obj.getClass.getName.split('.').lastOption.getOrElse("UnknownClass")
    }
  }

  def receive = {
    case DeadLetter(msg, from, to) =>
      metrics.counter(s"message.${getSafeClassName(msg)}").inc(1)
      metrics.counter(s"from.${from.path.name}").inc(1)
      metrics.counter(s"to.${to.path.name}").inc(1)
  }
}