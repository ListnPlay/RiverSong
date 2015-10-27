package com.featurefm.riversong.routes

import akka.event.Logging
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.featurefm.riversong.metrics.Instrumented
import org.json4s.jackson.Serialization

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends Instrumented {

  implicit val serialization = Serialization
  implicit val context = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass)

  def status(e: Throwable, code: StatusCode): StatusCode = if (e.isInstanceOf[IllegalArgumentException]) code else StatusCodes.InternalServerError

  def status(e: Throwable): StatusCode = status(e, StatusCodes.BadRequest)

  def routes: Route

}
