package com.featurefm.riversong.kafka

import java.time.Duration

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer, OffsetAndTimestamp}
import org.apache.kafka.common.{PartitionInfo, TopicPartition}
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.FlatSpecLike
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future


class KafkaSpec extends TestKit(ActorSystem("KafkaSpec")) with FlatSpecLike with EmbeddedKafka  with ScalaFutures {

  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

  implicit val mat: ActorMaterializer = ActorMaterializer()

  "running with embedded kafka" should "work" in {

    withRunningKafka {
      // embedded kafka sanity test
      publishStringMessageToKafka("topic", "message")
      consumeFirstStringMessageFrom("topic") equals "message"
    }
  }

  "producer kafka service" should "convert to bytes" in {
    withRunningKafka {

      val kafkaService = spy(new KafkaProducerService())
      kafkaService.initialize()

      val p: Future[Long] = kafkaService.sendRaw[Seq[String]]("topic2", Seq("123", "456"))
      val b = """["123","456"]""".getBytes
      Mockito.verify(kafkaService, times(1)).send("topic2", b, KafkaService.hashKey(b))
    }
  }

  "producer kafka service" should "send event messages" in {
    withRunningKafka {

      Thread.sleep(3000)
      val kafkaService = new KafkaProducerService()
      kafkaService.initialize()
      Thread.sleep(3000)

      val p: Future[Long] = kafkaService.send("topic2", "content1".getBytes())
      assert(consumeFirstStringMessageFrom("topic2") == "content1")
      // test future
      whenReady(p) { res: Long =>
        assert(res == 0)
      }

      // few more messages
      val p2: Future[Long] = kafkaService.send("topic2", "content2".getBytes(), "message2")
      val p3: Future[Long] = kafkaService.send("topic2", "content3".getBytes(), "message1")
      val p4: Future[Long] = kafkaService.send("topic2", "content4".getBytes(), "message2")
      assert(consumeFirstStringMessageFrom("topic2") == "content2")
      whenReady(p2) { res: Long =>
        assert(res == 1)
      }
      whenReady(p3) { res: Long =>
        assert(res == 2)
      }
      whenReady(p4) { res: Long =>
        assert(res == 3)
      }

      assert(consumeFirstStringMessageFrom("topic2") == "content3")
      assert(consumeFirstStringMessageFrom("topic2") == "content4")
    }

  }

  "consumer kafka service" should "listen to messages" in {
    withRunningKafka {

      Thread.sleep(3000)
      val kafkaService = new KafkaConsumerService()
      Thread.sleep(3000)

      val source = kafkaService.listenSince("topic3", 1000)
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
  }

  import scala.collection.JavaConversions._
  val partitionsList = new java.util.ArrayList[PartitionInfo]()
  partitionsList.add(new PartitionInfo("abc", 1, null, Array.empty, Array.empty))
  partitionsList.add(new PartitionInfo("abc", 2, null, Array.empty, Array.empty))
  partitionsList.add(new PartitionInfo("abc", 3, null, Array.empty, Array.empty))
  val offsetsForTimesMap:java.util.Map[TopicPartition, java.lang.Long] = Map[TopicPartition, java.lang.Long](new TopicPartition("abc", 1) -> 100L,
    new TopicPartition("abc", 2) -> 100L,
    new TopicPartition("abc", 3) -> 100L)
  val offsetsPerPartition:java.util.Map[TopicPartition, OffsetAndTimestamp] = Map[TopicPartition, OffsetAndTimestamp](new TopicPartition("abc", 1) -> new OffsetAndTimestamp(345, 123),
    new TopicPartition("abc", 2) -> new OffsetAndTimestamp(7777, 103),
    new TopicPartition("abc", 3) -> new OffsetAndTimestamp(11, 102))

  val tp = new TopicPartition("abc", 1)
  val tp2 = new TopicPartition("abc", 2)
  val tp3 = new TopicPartition("abc", 3)

//  "consumer kafka service" should "handle messages" in {
//    withRunningKafka {
//
//      Thread.sleep(3000)
//      val kafkaService = Mockito.spy(new KafkaConsumerService())
//      Thread.sleep(3000)
//
//      val mockConsumer = Mockito.mock(classOf[KafkaConsumer[KeyType, ValueType]])
//      Mockito.when(kafkaService.createConsumer("abc")).thenReturn(mockConsumer)
//      Mockito.when(mockConsumer.partitionsFor("abc")).thenReturn(partitionsList)
//
//      val source: Source[ConsumerMessageType, _] = kafkaService.listenSince("abc", 100, 1000)
//      val f: Future[Seq[ConsumerMessageType]] = source.take(2).runWith(Sink.seq)
//
//      publishStringMessageToKafka("abc", "message_abc1")
//      publishStringMessageToKafka("abc", "message_abc2")
//
//      whenReady(f) { res: Seq[ConsumerMessageType] =>
//        assert(res.size == 2)
//        assert(res(0).topic() == "abc")
//        assert(res(0).partition() == 1)
//        assert(res(0).offset() == 346)
//        assert(res(1).topic() == "abc")
//        assert(res(1).partition() == 2)
//        assert(res(1).offset() == 7778)
//      }
//    }
//
//  }

  "consumer kafka service" should "handle the waitTime parameter" in {
    withRunningKafka {
      Thread.sleep(3000)
      val kafkaService = Mockito.spy(new KafkaConsumerService())
      Thread.sleep(3000)

      val mockConsumer = Mockito.mock(classOf[KafkaConsumer[KeyType, ValueType]])
      Mockito.when(kafkaService.createConsumer("abc")).thenReturn(mockConsumer)
      Mockito.when(mockConsumer.partitionsFor("abc")).thenReturn(partitionsList)
      Mockito.when(mockConsumer.offsetsForTimes(offsetsForTimesMap)).thenReturn(offsetsPerPartition)

      val source: Source[ConsumerMessageType, _] = kafkaService.listenSince("abc", 100, 1000)
      val f: Future[Seq[ConsumerMessageType]] = source.runWith(Sink.seq)
      val source2: Source[ConsumerMessageType, _] = kafkaService.listenSince("abc", 100, 4000)
      val f2: Future[Seq[ConsumerMessageType]] = source2.runWith(Sink.seq)

      Thread.sleep(2000)
      assert(f.isCompleted)
      assert(!f2.isCompleted)

    }

  }


}

