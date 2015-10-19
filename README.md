# RiverSong
Common base for microservices:
* Akka-Http based server
* macwire for dependency injection
* json4s for serialization
* metrics and reporters

## How to use

    class DummyServiceAssembly(implicit system: ActorSystem) extends ServiceAssembly {
      //construct necessary objects
      override def routes: Route = ??? //set your routes here
    }

    object DummyManager extends MainService("Dummy") with App {
      override def assembly: ServiceAssembly = new DummyServiceAssembly
    }

## What do you get out of the box

`GET /status`

`GET /metrics?jvm={true|false}&pattern={metrics-key-regex}`

Periodic reporters for Slf4j, StatsD, InfluxDb and DataDog.

Requests are logged (unless you turn it off in configuration).

`Instrumented` trait to help you report custom metrics (`time` and `timeEventually` for synchronous code and `Future`s respectively are especially useful), `BaseRouting` trait for metrics and routing utilities. See `LifecycleRouting` class for some examples.

## WORK IN PROGRESS!
![Spoilers](http://cachebingo.titanbet.co.uk/sites/default/files/tumblr_static_tumblr_static_river_.jpg)
