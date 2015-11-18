package com.featurefm.riversong.metrics

import akka.actor.ActorSystem
import nl.grons.metrics.scala.{MetricName, InstrumentedBuilder, Timer}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by yardena on 9/2/15.
 */
trait Instrumented extends InstrumentedBuilder {

  implicit val system: ActorSystem

  override lazy val metricBaseName = MetricName(getClass.getSimpleName)

  /**
   * The MetricRegistry where created metrics are registered.
   */
  val metricRegistry = Metrics().metricRegistry

  lazy val timers: collection.concurrent.Map[String,Timer] = TrieMap()

  def timer(name: String): Timer = {
    val timer = metrics.timer(name)
    timers.putIfAbsent(name, timer).getOrElse(timer)
  }

  def time[A](name: String)(f: => A): A = {
    timer(name).time(f)
  }

  def timeEventually[A](name: String)(future: => Future[A])(implicit context: ExecutionContext): Future[A] = {
    val ctx = timer(name).timerContext()
    val f = future
    f.onComplete(_ => ctx.stop())
    f
  }
}