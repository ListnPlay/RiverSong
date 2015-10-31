# RiverSong
Common base for microservices:
* Akka-Http based server
* macwire for dependency injection
* json4s for serialization
* metrics and reporters

## How to use

    trait AllMyServices extends ServiceAssembly {
      //construct necessary objects, some of them will be implementations of BaseRouting
      //routes should built from all routes provided by instances of BaseRouting that you want to expose
      
      ...

      lazy val serviceA = wire[MyService]
      lazy val serviceB = wire[AnotherService]
      lazy val serviceC = wire[YetAnotherService]
      
      override def routes: Route = buildRoutes(serviceA, serviceB, serviceC) //set your routes here
    }
and

    object GeronimoService extends MainService("Geronimo") with App {
      override def assembly: ServiceAssembly = new ServiceAssembly with AllMyServices
    }

Run `GeronimoService`, by default it will start HTTP server on port 8080. 

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

`GET /status`

`GET /metrics?jvm={true|false}&pattern={metrics-key-regex}`

Periodic reporters for Slf4j, StatsD, InfluxDb and DataDog.

Requests are logged (unless you turn it off in configuration).

`Instrumented` trait to help you report custom metrics (`time` and `timeEventually` for synchronous code and `Future`s respectively are especially useful), `BaseRouting` trait for metrics and routing utilities. See `LifecycleRouting` class for some examples.

## WORK IN PROGRESS!
![Spoilers](http://cachebingo.titanbet.co.uk/sites/default/files/tumblr_static_tumblr_static_river_.jpg)

## Credits:
Heavily inspired by https://github.com/vonnagy/service-container


