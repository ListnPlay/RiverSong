package com.featurefm.riversong.kafka

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.{Timer, TimerTask}
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.scaladsl.Producer
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source, SourceQueue}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.featurefm.riversong.health.{HealthCheckWithCritical, HealthInfo, HealthState}
import com.featurefm.riversong.metrics.Instrumented
import com.featurefm.riversong.{Configurable, InitBeforeUse}
import io.prometheus.client.Counter
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.hashing.MurmurHash3

object FutureUtil {

  // All Future's that use futureWithTimeout will use the same Timer object
  // it is thread safe and scales to thousands of active timers
  // The true parameter ensures that timeout timers are daemon threads and do not stop
  // the program from shutting down

  val timer: Timer = new Timer(true)

  /**
   * Returns the result of the provided future within the given time or a timeout exception, whichever is first
   * This uses Java Timer which runs a single thread to handle all futureWithTimeouts and does not block like a
   * Thread.sleep would
   * @param future Caller passes a future to execute
   * @param timeout Time before we return a Timeout exception instead of future's outcome
   * @return Future[T]
   */
  def futureWithTimeout[T]( future : Future[T],
                            timeout : FiniteDuration,
                            onTimeOut: () => Throwable = () => {new TimeoutException()})
                          (implicit ec: ExecutionContext): Future[T] = {

    // Promise will be fulfilled with either the callers Future or the timer task if it times out
    val p = Promise[T]

    // and a Timer task to handle timing out
    val timerTask = new TimerTask() {
      def run() : Unit = {
        p.tryFailure(onTimeOut())
      }
    }

    // Set the timeout to check in the future
    timer.schedule(timerTask, timeout.toMillis)

    future.map {
      a =>
        if(p.trySuccess(a)) {
          timerTask.cancel()
        }
    }
      .recover {
        case e: Exception =>
          if(p.tryFailure(e)) {
            timerTask.cancel()
          }
      }

    p.future
  }

}

class KafkaProducerService(implicit val system: ActorSystem) extends Instrumented with Configurable with InitBeforeUse with HealthCheckWithCritical {

  override val serviceName = "kafka-producer"

  implicit val executor = system.dispatcher
  implicit val mat = ActorMaterializer()

  lazy val healthTopic = "health-check"
  override def isServiceCritical: Boolean = config.getBoolean("kafka.is-critical")

  protected val log = Logging(system, getClass)

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

    // count events where timeout occurred (dropped out of queue?)
    FutureUtil.futureWithTimeout(p.future, sendTimeout, () => {
      KafkaService.msgMetric.labels(s"$topic-timeout").inc()
      new TimeoutException(s"Sending to kafka got timeout after $sendTimeout ms message with topic:$topic key:$key")
    })
  }

  override def getHealth: Future[HealthInfo] = {
   send(healthTopic, KafkaService.toBytes(system.name), system.name) map { ret =>
     if (ret >= 0) {
       HealthInfo(HealthState.OK, details = s"'$healthTopic' topic on brokers ${brokers}, offset $ret")
     }
     else {
       HealthInfo(HealthState.CRITICAL, details = s"'$healthTopic' topic on brokers ${brokers}, offset $ret")
     }
   }
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

