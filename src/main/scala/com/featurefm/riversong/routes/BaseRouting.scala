package com.featurefm.riversong.routes

import java.util.concurrent.TimeoutException

import akka.event.Logging
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.featurefm.riversong.Json4sProtocol
import com.featurefm.riversong.metrics.Instrumented
import com.featurefm.riversong.metrics.MetricsDefinition.{httpRequestDuration, _}
import com.featurefm.riversong.tracing.ContextPropagation
import io.prometheus.client.SimpleTimer

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Created by yardena on 8/6/15.
 */
trait BaseRouting extends RiverSongRouting with Directives with Json4sProtocol with Instrumented {

  implicit val context = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass.getName)

  def status(e: Throwable, code: StatusCode): StatusCode = if (e.isInstanceOf[IllegalArgumentException]) code else StatusCodes.InternalServerError

  def status(e: Throwable): StatusCode = status(e, StatusCodes.BadRequest)

  def measured(name: String): Directive0 = measured(_ => name)

  def measured(nameGenerator: HttpRequest => String = measurementNameDefault): Directive0 = {
    extractRequestContext.flatMap { ctx =>
      val method = ctx.request.method.value
      val path = nameGenerator(ctx.request)
      val timer = new SimpleTimer

      mapResponse { response =>
        val time = timer.elapsedSeconds()
        val code = response.status.value
        httpRequestDuration.labels(method, path, code).observe(time)
        response
      }
    }
  }

  def tracing(): Directive1[ContextPropagation] = extractRequestContext.flatMap[Tuple1[ContextPropagation]] { ctx =>
    import ContextPropagation._
    provide(ContextPropagation(ctx.request.headers.filter(x => dtHeadersSet.contains(x.lowercaseName))))
  }

  def measurementNameDefault(req: HttpRequest) = s"${req.uri.path}"

  def onCompleteMeasured[T](name: String)(future: => Future[T]): Directive1[Try[T]] = onCompleteMeasured(_ => name)(future)

  def onCompleteMeasured[T](nameGenerator: HttpRequest => String = measurementNameDefault)(future: => Future[T]): Directive1[Try[T]] = {
    import akka.http.scaladsl.util.FastFuture._

    Directive { inner => ctx =>
      val method = ctx.request.method.value
      val path = nameGenerator(ctx.request)
      val timer = new SimpleTimer

      val f = future.fast.transformWith(t => inner(Tuple1(t))(ctx))

      f.onComplete { res =>
        val time = timer.elapsedSeconds()
        val code = res match {
          case Success(Complete(hr: HttpResponse)) => hr.status.value
          case _ => "Error"
        }
        httpRequestDuration.labels(method, path, code).observe(time)
      }
      f
    }
  }

}
