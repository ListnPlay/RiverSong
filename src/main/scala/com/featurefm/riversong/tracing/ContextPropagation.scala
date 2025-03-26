package com.featurefm.riversong.tracing

import akka.http.scaladsl.model.HttpHeader

case class ContextPropagation(
  headers: Seq[HttpHeader] = Seq()
)

object ContextPropagation{
  val dtHeadersSet = Set(
    "x-request-id", "x-b3-traceid", "x-b3-spanid", "x-b3-parentspanid", "x-b3-sampled", "x-b3-flags", "x-ot-span-context", "x-cloud-trace-context", "traceparent", "grpc-trace-bin", "x-ffm-user-id", "x-ffm-impersonated-to-user-id", "x-ffm-impersonated-from-admin"
  )
}
