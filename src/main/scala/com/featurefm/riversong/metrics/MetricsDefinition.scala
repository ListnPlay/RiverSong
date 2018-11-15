package com.featurefm.riversong.metrics

import java.util.concurrent.Callable

import io.prometheus.client.Summary


object MetricsDefinition {

  val invocationDuration: Summary = Summary.build()
    .name("invocation_duration_seconds")
    .help("invocation duration")
    .labelNames("name")
    .register()

  val httpRequestDuration: Summary = Summary.build()
    .name("http_request_duration_seconds")
    .help("http request duration")
    .labelNames("method", "path", "code")
    .register()

  val clientHttpRequestDuration: Summary = Summary.build()
    .name("client_http_request_duration_seconds")
    .help("Client http request duration")
    .labelNames("method", "host", "path", "code")
    .register()

  // this is needed for someMetric.time(Callable) to accept scala function/block
  import scala.language.implicitConversions
  implicit def functionToCallable[R](f: => R): Callable[R] = new Callable[R] {
    def call: R = f
  }
}
