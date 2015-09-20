package com.featurefm.riversong

import akka.actor.ActorSystem
import com.featurefm.riversong.routes.LifecycleRouting
import spray.routing._

/**
 * Created by yardena on 9/20/15.
 */
abstract class ServiceAssembly(implicit val system: ActorSystem) extends Configurable {
  import com.softwaremill.macwire._
  lazy val lifecycle: LifecycleRouting = wire[LifecycleRouting]

  def routes: Route
}
