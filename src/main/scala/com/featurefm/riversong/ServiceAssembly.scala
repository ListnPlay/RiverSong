package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.featurefm.riversong.metrics.TimerInterceptor
import com.featurefm.riversong.routes.LifecycleRouting
import com.softwaremill.macwire.aop.Interceptor

/**
 * Created by yardena on 9/20/15.
 */
abstract class ServiceAssembly(implicit val system: ActorSystem, implicit val mat: Materializer) extends Configurable { // with Instrumented
  import com.softwaremill.macwire._
  lazy val lifecycle: LifecycleRouting = wire[LifecycleRouting]

  def timed: Interceptor = new TimerInterceptor
}
