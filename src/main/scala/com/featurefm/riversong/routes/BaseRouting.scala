package com.featurefm.riversong.routes

import akka.event.Logging
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends Json4sProtocol with Instrumented {

  implicit val context = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass)

  def status(e: Throwable, code: StatusCode): StatusCode = if (e.isInstanceOf[IllegalArgumentException]) code else StatusCodes.InternalServerError

  def status(e: Throwable): StatusCode = status(e, StatusCodes.BadRequest)

  def routes: Route

}
