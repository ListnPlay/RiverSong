package com.featurefm.riversong.kafka

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.scaladsl.Producer
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source, SourceQueue}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.featurefm.riversong.metrics.Instrumented
import com.featurefm.riversong.{Configurable, InitBeforeUse}
import io.prometheus.client.Counter
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.util.hashing.MurmurHash3

class KafkaProducerService()(implicit val system: ActorSystem) extends Instrumented with Configurable with InitBeforeUse {

  protected lazy val log = Logging(system, getClass)

  implicit val mat = ActorMaterializer()

  val brokers: KeyType = config.getString("kafka.hosts")
  private val queueBuffer: Int = config.getInt("kafka.send.producer-queue-buffer")
  private val sendTimeout = config.getLong("kafka.send.call-timeout-ms").millis
  private val minBackoff = config.getLong("kafka.send.backoff.min-in-ms").millis
  private val maxBackoff = config.getLong("kafka.send.backoff.max-in-ms").millis
  private val randomFactor: Int = config.getInt("kafka.send.backoff.random-factor")
  private val maxRestarts: Int = config.getInt("kafka.send.backoff.max-restarts")

  private lazy val producerSettings = ProducerSettings[KeyType, ValueType](system, new StringSerializer, new ByteArraySerializer).withBootstrapServers(brokers)

  // reference to the queue. updated if queue is restarted
  val ref = new AtomicReference[SourceQueue[ProducerMessage.Message[KeyType, ValueType, Promise[Long]]]]()

  override def initialize(): Future[Done] = {
    log.info(s"Initializing producer to kafka server: $brokers with backoff params: minBackoff=$minBackoff, maxBackoff=$maxBackoff, randomFactor=$randomFactor, maxRestarts=$maxRestarts")
    RestartSource.onFailuresWithBackoff(minBackoff, maxBackoff, randomFactor, maxRestarts)(() =>
      Source
        .queue[ProducerMsgType](queueBuffer, OverflowStrategy.dropHead)
        .mapMaterializedValue(queue => {
          ref.set(queue)
          queue
        })
        .via(Producer.flexiFlow(producerSettings))
        .collect {
          case result@ProducerMessage.Result(metadata, _) =>
            result.passThrough.complete(Try(metadata.offset))
        }
    ).runWith(Sink.ignore)
  }

  def sendRaw[T <: AnyRef](topic: String, value: T): Future[Long] = {
    send(topic, KafkaService.toBytes[T](value))
  }

  def sendRaw [T <: AnyRef](topic: String, value: T, key: String): Future[Long] = {
    send(topic, KafkaService.toBytes[T](value), key)
  }

  def send(topic: String, value: ValueType): Future[Long] = {
    send(topic, value, KafkaService.hashKey(value))
  }

  def send(topic: String, value: ValueType, key: String): Future[Long] = {
    KafkaService.msgMetric.labels(topic).inc()

    if (ref.get == null)
      return Future failed new RuntimeException("Source queue for kafka producer wasn't created")

    val p = Promise[Long]

    ref.get.offer(ProducerMessage.Message[String, ValueType, Promise[Long]](new ProducerRecord(topic, key, value), p))

    import akka.pattern.after
    import system.dispatcher
    // count events where timeout occurred (dropped out of queue?)
    val monitorIfTimeout =  after(sendTimeout, using = system.scheduler)(Future {
      KafkaService.msgMetric.labels(s"$topic-timeout").inc()
      throw new TimeoutException(s"Sending to kafka got timeout after $sendTimeout ms message with topic:$topic key:$key")
    })

    Future.firstCompletedOf(Seq(
      p.future, monitorIfTimeout))
  }

}

object KafkaService {

  val msgMetric = Counter.build("producer_messages_count", "no. of messages produced")
    .labelNames("topic").register()

  def hashKey(payload: ValueType): KeyType = new String(int2bytes(MurmurHash3.bytesHash(payload)), "UTF-8")

  import com.featurefm.riversong.Json4sProtocol._
  def toBytes[T <: scala.AnyRef](e: T): Array[Byte] = {
    if (e == null) null
    else {
      val b = new ByteArrayOutputStream()
      serialization.write[T](e, b)
      b.toByteArray
    }
  }

  private def int2bytes(i: Int): Array[Byte] = ByteBuffer.allocate(4).putInt(i).array()
}

