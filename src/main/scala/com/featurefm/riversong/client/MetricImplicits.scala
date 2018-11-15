package com.featurefm.riversong.client

import akka.http.scaladsl.model.HttpRequest

/**
  * Created by yardena on 1/6/16.
  */
trait MetricImplicits {

  case class FixedNaming(name: String) extends NamedHttpRequest {
    override def apply(request: HttpRequest): String = s"$name"
  }

  implicit object MethodAndPathNamedRequest extends NamedHttpRequest {
    override def apply(request: HttpRequest): String = s"${request.uri.path}"
  }
}

object MetricImplicits extends MetricImplicits
