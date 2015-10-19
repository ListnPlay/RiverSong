package com.featurefm.riversong.routes

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import com.featurefm.riversong.metrics.Instrumented

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends Instrumented {

  def status(e: Throwable, code: StatusCode) = if (e.isInstanceOf[IllegalArgumentException]) code else StatusCodes.InternalServerError

  def status(e: Throwable) = status(e, StatusCodes.BadRequest)

  def routes: Route

}
