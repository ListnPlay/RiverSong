package com.featurefm.riversong.metrics

import akka.actor.{DeadLetter, ActorSystem, Actor}
import nl.grons.metrics.scala.MetricName

/**
 * Created by yardena on 12/15/15.
 */
class DeadLetterMetrics extends Actor with Instrumented {
  override implicit lazy val system: ActorSystem = context.system
  override lazy val metricBaseName: MetricName = MetricName("dead-letters")

  def receive = {
    case DeadLetter(msg, from, to) =>
      metrics.counter(s"message.${msg.getClass.getSimpleName}").inc(1)
      metrics.counter(s"from.${from.path.name}").inc(1)
      metrics.counter(s"to.${to.path.name}").inc(1)
  }

}
