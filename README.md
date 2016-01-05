# RiverSong
Common base for microservices:
* [akka-http] (http://akka.io/docs/#akka-streams-and-http) based server
* [akka-http] (http://akka.io/docs/#akka-streams-and-http) based client for consuming other services
* [macwire] (https://github.com/adamw/macwire) for dependency injection
* [json4s/jackson] (https://github.com/json4s/json4s#jackson) for serialization (with [akka-http-json] (https://github.com/hseeberger/akka-http-json))
* [metrics] (http://metrics.dropwizard.io/) and reporters

## How to use

### sbt
To include it in your project add `"com.featurefm" %% "river-song" % "0.3.5"`
Only Scala 2.11 is currently supported.

### code
    trait AllMyServices extends ServiceAssembly {
      //construct necessary objects, some of them will be implementations of BaseRouting
      //routes should built from all routes provided by instances of BaseRouting that you want to expose
      
      ...

      lazy val serviceA = wire[MyService]
      lazy val serviceB = wire[AnotherService]
      lazy val serviceC = wire[YetAnotherService]
      
      val wired: Wired = wiredInModule(assembly)
      override def services = wired.lookup(classOf[RiverSongRouting])
      wired.lookup(classOf[HealthCheck]) foreach Health().addCheck

    }
and

    object GeronimoService extends MainService("Geronimo") with App {
      override def assembly: ServiceAssembly = new ServiceAssembly with AllMyServices
    }

Run `GeronimoService`, by default it will start HTTP server on port 8080. Services that wish to expose routes need to implement `RiverSongRouting`. Services that wish to expose health information should implement `com.featurefm.riversong.health.HealthCheck` interface.
 If you want public methods automatically timed, use `TimerInterceptor` like this:
 
    lazy val serviceA = time(wire[MyService])

Host and port of the server are controlled by configuration:

    akka {
      ... 
      
      http.server {
        listen_ip : "0.0.0.0"
        listen_port: 8080
      }
      ...
    }


## What do you get out of the box

`GET /` returns uptime and number of requests

`GET /status` returns 200 if server is up

`GET /metrics?jvm={true|false}&pattern={metrics-key-regex}`

`GET /config` and `GET /config/{path}`

`GET /health` which will become more meaningful if you register application sub-components by extending `com.featurefm.riversong.health.HealthCheck`

Periodic reporters for Slf4j, StatsD, InfluxDb and DataDog.

Requests are logged (unless you turn it off in configuration). Example output:

    2016-01-04T15:47:57,320 INFO  [...] ActorSystemImpl - GET /status ~> 200 OK [8ms]

`Instrumented` trait to help you report custom metrics (`time` and `timeEventually` for synchronous code and `Future`s respectively are especially useful), `BaseRouting` trait for metrics and routing utilities. See `LifecycleRouting` class for some examples.

For Actors, it is recommended to implement `InstrumentedActor`, it will protect you from gauge collisions when Actor is restarted and will automatically count restarts. The framework also automatically counts dead letters.

## WORK IN PROGRESS!
![Spoilers](http://cachebingo.titanbet.co.uk/sites/default/files/tumblr_static_tumblr_static_river_.jpg)

## Credits:
Heavily inspired by https://github.com/vonnagy/service-container


