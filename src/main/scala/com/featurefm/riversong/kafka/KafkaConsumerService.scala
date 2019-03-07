package com.featurefm.riversong.kafka

import java.time.Duration
import java.util.{Properties, UUID}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.metrics.Instrumented
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer, OffsetAndTimestamp}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConversions._


class KafkaConsumerService ()(implicit val system: ActorSystem) extends Instrumented with Configurable {

  protected lazy val log = Logging(system, getClass)

  implicit val mat = ActorMaterializer()

  implicit val kafkaDispatcher = system.dispatchers.lookup("kafka.receive.kafka-dispatcher")

  val brokers: KeyType = config.getString("kafka.hosts")

  val groupId = config.getString("kafka.receive.group-id")
  val clientId = config.getString("kafka.receive.client-id")

  /**
    * Start listening to Kafka, simple case. (used by: CampaignServer)
    * @param topics - topics to listen and poll messages
    * @return - source
    */
  def listen(topics: String*): Source[ConsumerMessageType, _] = {
    val consumerSettings = createConsumerSettings()

    Consumer.plainSource(consumerSettings, Subscriptions.topics(topics.toSet))
  }

  /**
    * Start listening to Kafka, from a certain timestamp.
    * Two options:
    * 1. Keep on listening (used by: CampaignServer)
    * 2. Read all messages and stop. finish after 'waitTimeInMs'. (used by: EventsManager for debugger)
    * @param topics - topics to listen and poll messages
    * @param timestamp - listen from this timestamp
    * @param waitTimeInMs - by default -1, which means Option 1. Positive value to define when to stop reading, in Option 2
    * @return - source
    */
  def listenSince(topic: String, timestamp: Long, waitTimeInMs: Long = -1): Source[ConsumerMessageType, _] = {

    val consumer = createConsumer(topic)
    val partitions = consumer.partitionsFor(topic)
    val partitionToTimeMap: java.util.Map[TopicPartition, java.lang.Long] = Map(partitions.map({ a => new TopicPartition(a.topic(), a.partition()) -> long2Long(timestamp) }): _*)

    val consumerSettings = createConsumerSettings()

    val baseSource = Consumer.plainSource(consumerSettings, Subscriptions.assignmentOffsetsForTimes(partitionToTimeMap))

    val source: Source[ConsumerMessageType, _] =
    if (waitTimeInMs > 0) {
      // this implementation try to read the 10-minutes-data for $waitTime and stops.
      // if the $waitTime is too short, it might miss the LAST messages (which are the most relevant ones)
      // if waitTime is too long and the debugger's user need to wait, we should rewrite this
      // and read until we get to currentTime or to last offset
      baseSource
        .takeWithin(waitTimeInMs.millis)

    }
    else {
      baseSource
    }

    source
  }

  private def createConsumerSettings() = {
    val processId = UUID.randomUUID()
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokers)
      .withGroupId(s"${config.getString("kafka.receive.group-id")}-$processId")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      .withClientId(s"${config.getString("kafka.receive.client-id")}-$processId")
  }

  protected[kafka] def createConsumer(topic: String): KafkaConsumer[KeyType, ValueType] = {

    val processId = UUID.randomUUID()

    val props = new Properties
    props.setProperty("bootstrap.servers", brokers)
    props.setProperty("group.id", s"$groupId-$processId")
    props.setProperty("client.id", s"$clientId-$processId")
    props.setProperty("enable.auto.commit", "true")
    props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")

    val consumer = new KafkaConsumer[KeyType, ValueType](props)

    consumer
  }


}
