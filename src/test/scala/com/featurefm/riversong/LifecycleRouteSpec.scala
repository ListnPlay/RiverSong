package com.featurefm.riversong

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.featurefm.riversong.message.Message
import com.featurefm.riversong.routes.LifecycleRouting
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.JObject
import org.scalatest.{Matchers, FlatSpec}
import org.json4s.jackson.JsonMethods._

/**
 * Created by yardena on 8/13/15.
 */
class LifecycleRouteSpec extends FlatSpec with Matchers with ScalatestRouteTest with Json4sProtocol {

  def actorRefFactory = system

  lazy val routing = new LifecycleRouting

  "GET /status" should "return success" in {
    Get("/status") ~> routing.routes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Message] === Message("Server is up")
    }
  }

  "GET /config" should "return full config" in {
    Get("/config") ~> routing.routes ~> check {
      status shouldEqual StatusCodes.OK
      val str = compact(render(responseAs[JObject]))
      val config = ConfigFactory.parseString(str)
      val myConfig = CoreConfig.getConfig()
      config.getInt("akka.http.server.listen_port") shouldEqual myConfig.getInt("akka.http.server.listen_port")
    }
  }

  "GET /config/akka/http/server" should "return server config" in {
    Get("/config/akka/http/server") ~> routing.routes ~> check {
      status shouldEqual StatusCodes.OK
      val str = compact(render(responseAs[JObject]))
      val config = ConfigFactory.parseString(str)
      val myConfig = CoreConfig.getConfig()
      config.getInt("listen_port") shouldEqual myConfig.getInt("akka.http.server.listen_port")
    }
  }

  "GET /config/akka/http/server/listen_port" should "return listen_port" in {
    Get("/config/akka/http/server") ~> routing.routes ~> check {
      status shouldEqual StatusCodes.OK
      val str = compact(render(responseAs[JObject]))
      val config = ConfigFactory.parseString(str)
      val myConfig = CoreConfig.getConfig()
      config.getInt("listen_port") shouldEqual myConfig.getInt("akka.http.server.listen_port")
    }
  }

  "GET /metrics" should "return success" in {
    Get("/metrics") ~> routing.routes ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

}
