package com.featurefm.riversong.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.compat.Platform
import scala.concurrent.Future

case class MyCaseClass(stringy: String, numbery: Int)


class KafkaSpec extends TestKit(ActorSystem("KafkaSpec")) with FlatSpecLike with EmbeddedKafka  with ScalaFutures with Matchers with BeforeAndAfterAll{

  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)
  implicit val serializer = new ByteArraySerializer()
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(2, Seconds)), scaled(Span(50, Millis)))

  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def beforeAll(): Unit = {
    super.beforeAll()
    EmbeddedKafka.start()
  }

  override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    super.afterAll()
  }

  "running with embedded kafka" should "work" in {

      // embedded kafka sanity test
      publishStringMessageToKafka("topic", "message")
      consumeFirstStringMessageFrom("topic") equals "message"
  }

  "producer kafka service" should "convert to bytes" in {
      val kafkaService = spy(new KafkaProducerService())
      kafkaService.initialize()
      Thread.sleep(2000)

      val p: Future[Long] = kafkaService.sendRaw[Seq[String]]("topic2", Seq("123", "456"))
      val b = """["123","456"]""".getBytes
      Mockito.verify(kafkaService, times(1)).send("topic2", b, KafkaService.hashKey(b))
  }

  "producer kafka service" should "send event messages" in {
      val kafkaService = new KafkaProducerService()
      kafkaService.initialize()
      Thread.sleep(2000)

      val p: Future[Long] = kafkaService.send("topic4", "content1".getBytes())
      assert(consumeFirstStringMessageFrom("topic4") == "content1")
      // test future
      whenReady(p) { res: Long =>
        assert(res == 0)
      }

      // few more messages
      val p2: Future[Long] = kafkaService.send("topic4", "content2".getBytes(), "message2")
      val p3: Future[Long] = kafkaService.send("topic4", "content3".getBytes(), "message1")
      val p4: Future[Long] = kafkaService.send("topic4", "content4".getBytes(), "message2")
      assert(consumeFirstStringMessageFrom("topic4") == "content2")
      whenReady(p2) { res: Long =>
        assert(res == 1)
      }
      whenReady(p3) { res: Long =>
        assert(res == 2)
      }
      whenReady(p4) { res: Long =>
        assert(res == 3)
      }

      assert(consumeFirstStringMessageFrom("topic4") == "content3")
      assert(consumeFirstStringMessageFrom("topic4") == "content4")

  }

  "producer kafka service" should "sendRaw event messages with any type" in {
      val kafkaService = new KafkaProducerService()
      kafkaService.initialize()
      Thread.sleep(2000)

      val p: Future[Long] = kafkaService.sendRaw[MyCaseClass]("topic6", MyCaseClass("maroon", 5))
      assert(consumeFirstStringMessageFrom("topic6") == """{"stringy":"maroon","numbery":5}""")

      // test future
      whenReady(p) { res: Long =>
        assert(res == 0)
      }
  }

  "consumer kafka service" should "simply listen" in {

      publishStringMessageToKafka("topic7", "message3327")
      publishStringMessageToKafka("topic8", "message3328")
      publishStringMessageToKafka("topic9", "message3329")
      Thread.sleep(2000)
      val kafkaService = new KafkaConsumerService()
      Thread.sleep(2000)

      val settings = kafkaService.createBasicConsumerSettings()
      val source = kafkaService.listenSince(Seq("topic7", "topic8", "topic9"), settings, Platform.currentTime)
      val f1 = source.take(2).runWith(Sink.ignore)

      publishStringMessageToKafka("topic7", "message3327111")
      publishStringMessageToKafka("topic8", "message3328")
      publishStringMessageToKafka("topic7", "message3327222")

     whenReady(f1) { res =>
        res shouldBe Done
      }
  }

  "consumer kafka service" should "listen to messages" in {

      publishStringMessageToKafka("topic3", "message332")
      publishStringMessageToKafka("topic32", "message332")
      val kafkaService = new KafkaConsumerService()
      Thread.sleep(2000)

      val settings = kafkaService.createBasicConsumerSettings()
      val source = kafkaService.listenSince(Seq("topic3"), settings, 1000)
      publishStringMessageToKafka("topic3", "message332")

      val f5 = source.take(5).runWith(Sink.ignore)
      val f3 = source.take(3).runWith(Sink.ignore)

      publishStringMessageToKafka("topic3", "message331")
      publishStringMessageToKafka("topic3", "message333")

      Thread.sleep(1500)

      // test future
      assert(!f5.isCompleted)
      assert(f3.isCompleted)
  }

  "consumer kafka service" should "handle the waitTime parameter" in {
      val kafkaService = new KafkaConsumerService()
      Thread.sleep(2000)

      publishStringMessageToKafka("abc1", "message332")
      val settings = kafkaService.createBasicConsumerSettings()
      val source: Source[ConsumerMessageType, _] = kafkaService.listenSince(Seq("abc1"), settings, 100, 1000)
      val f: Future[Seq[ConsumerMessageType]] = source.runWith(Sink.seq)
      val source2: Source[ConsumerMessageType, _] = kafkaService.listenSince(Seq("abc1"), settings, 100, 4000)
      val f2: Future[Seq[ConsumerMessageType]] = source2.runWith(Sink.seq)

      Thread.sleep(2000)
      assert(f.isCompleted)
      assert(!f2.isCompleted)


  }

  "consumer kafka service" should "handle messages" in {

      publishToKafka("abc", KafkaService.toBytes[String]("message_abc11"))
      publishToKafka("abc", KafkaService.toBytes[String]("message_abc22"))
      publishToKafka("abc", KafkaService.toBytes[String]("message_abc33"))

      Thread.sleep(2000)
      val kafkaService = new KafkaConsumerService()
      Thread.sleep(2000)

      val settings = kafkaService.createBasicConsumerSettings()
      val source: Source[ConsumerMessageType, _] = kafkaService.listenSince(Seq("abc"), settings, 100, 1000)
      val f: Future[Seq[ConsumerMessageType]] = source.take(2).runWith(Sink.seq)

      whenReady(f) { res: Seq[ConsumerMessageType] =>
        res.size shouldEqual 2
        res(0).topic() shouldEqual "abc"
        res(0).value() shouldEqual  KafkaService.toBytes[String]("message_abc11")
        res(1).topic() shouldEqual "abc"
        res(1).value() shouldEqual KafkaService.toBytes[String]("message_abc22")
      }

  }


}

