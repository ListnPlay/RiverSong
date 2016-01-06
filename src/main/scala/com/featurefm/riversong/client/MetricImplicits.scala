package com.featurefm.riversong.client

import akka.http.scaladsl.model.HttpRequest

/**
  * Created by yardena on 1/6/16.
  */
trait MetricImplicits {
  trait NamedHttpRequest extends ((HttpRequest) => String)

  case class FixedNaming(name: String) extends NamedHttpRequest {
    override def apply(request: HttpRequest): String = s"${request.method.value} $name"
  }

  implicit object MethodAndPathNamedRequest extends NamedHttpRequest {
    override def apply(request: HttpRequest): String = s"${request.method.value} ${request.uri.path}"
  }
}
