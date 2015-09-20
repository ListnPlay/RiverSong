package com.featurefm.riversong.routes

import com.featurefm.riversong.metrics.Instrumented
import spray.http.StatusCodes._
import spray.routing.Route

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends Instrumented {

  def status(e: Throwable) = if (e.isInstanceOf[IllegalArgumentException]) BadRequest else InternalServerError

  def routes: Route

}
