package com.featurefm.riversong

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.featurefm.riversong.client.ServiceClient
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

/**
  * Created by yardena on 12/17/16.
  */
class ServiceClientSpec extends TestKit(ActorSystem("TestKit")) with DefaultTimeout with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

  TestService.main(Array.empty)

  lazy val client = new ServiceClientTest()

  classOf[ServiceClient].getSimpleName should "call sleepy once" in {
    whenReady (client.callSleepy) { println }
  }

/*

  it should "recover from connection overflow" in {
    import client._
    import scala.concurrent.duration._
    Source.tick(5.milliseconds, 5.milliseconds, ())
      .completionTimeout(30.seconds)
      .mapAsync(100)(_ => client.callSleepy)
      .runWith(Sink.foreach(x => println(x)))
//    for (i <- 1 to 1000) {
//      whenReady(client.callSleepy) { println }
//    }
    Thread.sleep(30*1000)
  }
*/

  override protected def afterAll(): Unit = {
    system.terminate()
  }

}

class ServiceClientTest(implicit val system: ActorSystem) extends ServiceClient {

  override val serviceName = "test"

  override lazy val config: Config = CoreConfig.getConfig(Some(ConfigFactory.load("test.conf")))

  override def isServiceCritical: Boolean = true

  startSelfHealthWatch()

  import http.MethodAndPathNamedRequest

  def callSleepy = http.send(Get("/sleepy")) flatMap { response =>
    response.status match {
      case OK => Unmarshal(response.entity).to[String]
      case _ => failWith(response)
    }
  }
}
