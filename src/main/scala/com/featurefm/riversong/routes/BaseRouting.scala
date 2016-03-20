package com.featurefm.riversong.routes

import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented

import scala.concurrent.Future
import scala.util.Try

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends RiverSongRouting with Directives with Json4sProtocol with Instrumented {

  implicit val context = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass)

  def status(e: Throwable, code: StatusCode): StatusCode = if (e.isInstanceOf[IllegalArgumentException]) code else StatusCodes.InternalServerError

  def status(e: Throwable): StatusCode = status(e, StatusCodes.BadRequest)

  def measured(name: String): Directive0 = measured(_ => name)

  def measured(nameGenerator: HttpRequest => String = measurementNameDefault): Directive0 = {
    extractRequestContext.flatMap { ctx =>
      val t = metrics.timer(nameGenerator(ctx.request)).timerContext()
      mapResponse { response =>
        t.stop()
        response
      }
    }
  }
  
  def measurementNameDefault(req: HttpRequest) = s"${req.method.value} ${req.uri.path}"

  def onCompleteMeasured[T](name: String)(future: => Future[T]): Directive1[Try[T]] = onCompleteMeasured(_ => name)(future)

  def onCompleteMeasured[T](nameGenerator: HttpRequest => String = measurementNameDefault)(future: => Future[T]): Directive1[Try[T]] = {
    import akka.http.scaladsl.util.FastFuture._

    Directive { inner => ctx =>

      val tc = metrics.timer(nameGenerator(ctx.request)).timerContext()
      val f = future.fast.transformWith(t => inner(Tuple1(t))(ctx))
      f.onComplete(_ => tc.stop())
      f
    }
  }

}
