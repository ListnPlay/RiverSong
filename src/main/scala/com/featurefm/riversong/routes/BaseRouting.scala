package com.featurefm.riversong.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.featurefm.riversong.metrics.Instrumented

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends Instrumented {

  def status(e: Throwable) = if (e.isInstanceOf[IllegalArgumentException]) StatusCodes.BadRequest else StatusCodes.InternalServerError

  def routes: Route

}
