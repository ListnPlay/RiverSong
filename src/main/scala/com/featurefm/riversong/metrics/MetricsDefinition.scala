package com.featurefm.riversong.metrics

import java.util.concurrent.Callable

import io.prometheus.client.Summary


object MetricsDefinition {

  val invocationDuration: Summary = Summary.build()
    .name("invocation_duration_seconds")
    .help("invocation duration")
    .quantile(0.5, 0.05)
    .quantile(0.8, 0.03)
    .quantile(0.9, 0.01)
    .quantile(0.99, 0.01)
    .labelNames("name")
    .register()

  val httpRequestDuration: Summary = Summary.build()
    .name("http_request_duration_seconds")
    .help("http request duration")
    .quantile(0.5, 0.05)
    .quantile(0.8, 0.03)
    .quantile(0.9, 0.01)
    .quantile(0.99, 0.01)
    .labelNames("method", "path", "code")
    .register()

  val clientHttpRequestDuration: Summary = Summary.build()
    .name("client_http_request_duration_seconds")
    .help("Client http request duration")
    .quantile(0.5, 0.05)
    .quantile(0.8, 0.03)
    .quantile(0.9, 0.01)
    .quantile(0.99, 0.01)
    .labelNames("method", "host", "path", "code")
    .register()

  // this is needed for someMetric.time(Callable) to accept scala function/block
  import scala.language.implicitConversions
  implicit def functionToCallable[R](f: => R): Callable[R] = new Callable[R] {
    def call: R = f
  }
}
