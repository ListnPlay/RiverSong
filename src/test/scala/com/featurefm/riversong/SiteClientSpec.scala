package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.ActorMaterializer
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.featurefm.riversong.client.{HttpClient, HttpSiteClient, MetricImplicits}
import com.featurefm.riversong.metrics.reporting.Slf4jReporter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by yardena on 1/6/16.
  */
class SiteClientSpec extends TestKit(ActorSystem("TestKit")) with DefaultTimeout with ImplicitSender
      with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures with Configurable {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

  val client1 = HttpSiteClient.secure("www.google.com")
//  val client2 = HttpSiteClient("localhost", 8090)

  val oldClient1 = HttpClient.secure("www.google.com")
//  val oldClient2 = HttpClient("localhost", 8090)

  val url1 = "/search?q=scala"
//  val url2 = "/status"

  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  "SiteClient" should "be able to connect to google.com" in {
    {
      import client1.MethodAndPathNamedRequest
      val f = client1.send(Get(url1))
      whenReady(f) { result =>
        result.status.intValue() shouldBe 200
      }
      Await.ready(f flatMap (_.discardEntityBytes().future()), 4.second)
    }

    {
      import oldClient1.MethodAndPathNamedRequest
      val f2 = oldClient1.send(Get(url1))
      whenReady(f2) { result =>
        result.status.intValue() shouldBe 200
      }
      Await.ready(f2 flatMap (_.discardEntityBytes().future()), 4.second)
    }

  }

//  it should "be able to connect to localhost" in {
//    val f = client2.send(Get(url2), "first")
//    whenReady(f) { result =>
//      result.status shouldBe OK
//    }
//    val f2 = oldClient2.send(Get(url2), "first-old")
//    whenReady(f2) { result =>
//      result.status shouldBe OK
//    }
//  }

//  it should "display good performance" in {
//    import system.dispatcher
//
//    Future.sequence((1 to 100).map {
//      case i if i % 4 == 1  => oldClient1.send(Get(url1))
//      case i if i % 4 == 2  => client1.send(Get(url1))
//      case i if i % 4 == 3  => client2.send(Get(url2))
//      case i if i % 4 == 0  => oldClient2.send(Get(url2))
//    }).futureValue
//
//  }

  override protected def afterAll(): Unit = {
    new Slf4jReporter()(system, config.getConfig("metrics.reporters.Slf4j")).report()
    Await.ready(client1.shutdown() flatMap { _ => system.terminate() }, 4.seconds)
  }

}
