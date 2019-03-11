package com.featurefm.riversong.kafka

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.{lang, util}
import java.util.{Optional, Properties, UUID}

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, KafkaConsumerActor, Metadata, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.metrics.Instrumented
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.{PartitionInfo, TopicPartition}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer, StringDeserializer}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


class KafkaConsumerService ()(implicit val system: ActorSystem) extends Instrumented with Configurable {

  protected lazy val log = Logging(system, getClass)

  val brokers: KeyType = config.getString("kafka.hosts")

  val defaultGroupId = config.getString("kafka.receive.group-id")
  val defaultClientId = config.getString("kafka.receive.client-id")
  val defaultAutoOffsetReset = config.getString("kafka.receive.auto-offset")

  val partitionConsumerSettings = createConsumerSettings(defaultGroupId + "-partition", defaultClientId + "-partition", true)
  val timeout = 5.seconds
  val settings = partitionConsumerSettings.withMetadataRequestTimeout(timeout)
  implicit val askTimeout = Timeout(timeout)

  val consumer: ActorRef = system.actorOf(KafkaConsumerActor.props(settings))

  import akka.pattern.ask
  import system.dispatcher
  val topicsFuture: Future[Metadata.Topics] = (consumer ? Metadata.ListTopics).mapTo[Metadata.Topics]

  /**
    * Start listening to Kafka, simple case. (used by: CampaignServer)
    * @param topics - topics to listen and poll messages
    * @param groupId - consumer's group id. default is read from config kafka.receive.group-id
    * @param clientId - consumer's client id. default is read from config kafka.receive.client-id
    * @return - source
    */
  def listen(topics: Seq[String], groupId: String = defaultGroupId, clientId: String = defaultClientId): Source[ConsumerMessageType, _] = {
    val consumerSettings = createConsumerSettings(groupId, clientId)

    log.info(s"Start listening to topics ${topics.toSet}")
    Consumer.plainSource(consumerSettings, Subscriptions.topics(topics.toSet))
  }

  /**
    * Start listening to Kafka, kafka handles offsets. (used by: Subscription)
    * @param topics - topics to listen and poll messages
    * @param groupId - consumer's group id. default is read from config kafka.receive.group-id
    * @param clientId - consumer's client id. default is read from config kafka.receive.client-id
    * @return - source
    */
  def listenWithCommit(topics: Seq[String], groupId: String = defaultGroupId, clientId: String = defaultClientId): Source[ConsumerComittableMessageType, _] = {
    val consumerSettings = createConsumerSettings(groupId, clientId)

    log.info(s"Start listening to topics ${topics.toSet}")
    Consumer.committableSource(consumerSettings, Subscriptions.topics(topics.toSet))
  }

  /**
    * Start listening to Kafka, assigning the partitions.  (used by: BudgetManager)
    * @param topics - topics to listen and poll messages
    * @param decoder - Deserializer to parse the messages
    * @param groupId - consumer's group id. default is read from config kafka.receive.group-id
    * @param clientId - consumer's client id. default is read from config kafka.receive.client-id
    * @param useUniqueId - indicator whether to add unique identifier to group and client id
    * @return - PlainSource
    */
  def listenWithPartitions(topics: Seq[String], decoder: Deserializer[Try[AnyRef]], groupId: String = defaultGroupId, clientId: String = defaultClientId, useUniqueId: Boolean = true): Source[ConsumerMessageAnyType, _] = {
    listenWithPartitionsBasic(true, topics, decoder, groupId, clientId, useUniqueId)
  }

  /**
    * Start listening to Kafka, assigning the partitions.  (used by: BudgetManager)
    * @param topics - topics to listen and poll messages
    * @param decoder - Deserializer to parse the messages
    * @param groupId - consumer's group id. default is read from config kafka.receive.group-id
    * @param clientId - consumer's client id. default is read from config kafka.receive.client-id
    * @param useUniqueId - indicator whether to add unique identifier to group and client id
    * @return - AtMostOnceSource
    */
  def listenWithPartitionsAtMostOnce(topics: Seq[String], decoder: Deserializer[Try[AnyRef]], groupId: String = defaultGroupId, clientId: String = defaultClientId, useUniqueId: Boolean = true): Source[ConsumerMessageAnyType, _] = {
    listenWithPartitionsBasic(false, topics, decoder, groupId, clientId, useUniqueId)
  }

  private def listenWithPartitionsBasic(usePlain:Boolean, topics: Seq[String], decoder: Deserializer[Try[AnyRef]], groupId: String = defaultGroupId, clientId: String = defaultClientId, useUniqueId: Boolean = true): Source[ConsumerMessageAnyType, _] = {
    Source.fromFuture(getPartitionsPerTopic(topics))
      .flatMapConcat{ partitions =>

        val consumerSettings = createConsumerSettingsWithDecoder(decoder, groupId, clientId, useUniqueId)
        val topicPartitions = partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet
        val source = if (usePlain) Consumer.plainSource(consumerSettings, Subscriptions.assignment(topicPartitions)) else Consumer.atMostOnceSource(consumerSettings, Subscriptions.assignment(topicPartitions))
        source
      }
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
  def listenSince(topics: Seq[String],
                  timestamp: Long, 
                  waitTimeInMs: Long = -1,
                  groupId: String = defaultGroupId,
                  clientId: String = defaultClientId): Source[ConsumerMessageType, _] = {

    Source.fromFuture(getPartitionsPerTopic(topics))
      .flatMapConcat { partitions =>
        log.info(s"topics and partitions: $partitions")
        val partitionToTimeMap = Map(partitions.map({ a => new TopicPartition(a.topic(), a.partition()) -> long2Long(timestamp) }): _*)

        val consumerSettings = createConsumerSettings(groupId, clientId)

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
  }


  protected[kafka] def getPartitionsPerTopic(topicsSeq: Seq[String]): Future[Seq[PartitionInfo]] = {

    topicsFuture map { x =>
      val jTopicsMap: Optional[util.Map[String, util.List[PartitionInfo]]] = x.getResponse
      val topicsMap: Map[String, Seq[PartitionInfo]] = if (jTopicsMap.isPresent) jTopicsMap.get().toMap.mapValues(_.toSeq) else Map.empty

      topicsSeq.flatMap(topic => topicsMap.getOrElse(topic, Seq.empty))
    }
  }

  private def createConsumerSettings(groupId: String, clientId:String, useUniqueId: Boolean = true, autoOffsetReset: String = "latest"): ConsumerSettings[KeyType, ValueType] = {
    val processId = if (useUniqueId) s"-${UUID.randomUUID()}" else ""
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokers)
      .withGroupId(s"$groupId$processId")
      .withClientId(s"$clientId$processId")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset)
  }

  private def createConsumerSettingsWithDecoder(decoder: Deserializer[Try[AnyRef]], groupId: String, clientId:String, useUniqueId: Boolean = true, autoOffsetReset: String = "latest"): ConsumerSettings[KeyType, Try[AnyRef]] = {
    val processId = if (useUniqueId) s"-${UUID.randomUUID()}" else ""
    ConsumerSettings(system, new StringDeserializer, decoder)
      .withBootstrapServers(brokers)
      .withGroupId(s"$groupId$processId")
      .withClientId(s"$clientId$processId")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset)
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

  }

  protected[kafka] def createConsumer(groupId: String, clientId:String): KafkaConsumer[KeyType, ValueType] = {

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

object KafkaConsumerService {
  import com.featurefm.riversong.Json4sProtocol._
  def fromBytes[T](bytes: Array[Byte])(implicit m: Manifest[T]): Try[T] =
    Try(serialization.read[T](new InputStreamReader(new ByteArrayInputStream(bytes))))


}
