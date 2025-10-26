package com.featurefm.riversong.kafka

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.util
import java.util.{Optional, UUID}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.health.{HealthCheck, HealthInfo, HealthState}
import com.featurefm.riversong.metrics.Instrumented
import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import org.apache.kafka.common.{PartitionInfo, TopicPartition}

import java.util.Properties
import scala.collection.JavaConverters._
import scala.compat.Platform
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.Try


class KafkaConsumerService()(implicit val system: ActorSystem) extends Instrumented with Configurable with HealthCheck {

  protected lazy val log = Logging(system, getClass)

  implicit val mat: Materializer = Materializer.matFromSystem

  val brokers: KeyType = config.getString("kafka.hosts")

  val defaultGroupId = config.getString("kafka.receive.group-id")
  val defaultClientId = config.getString("kafka.receive.client-id")
  val defaultAutoOffsetReset = config.getString("kafka.receive.auto-offset")

  private val timeout = 5.seconds

  import system.dispatcher

  private def toScalaFuture[T](completionStage: java.util.concurrent.CompletionStage[T]): Future[T] = {
    val promise = Promise[T]()
    completionStage.whenComplete { (result, exception) =>
      if (exception != null) {
        promise.failure(exception)
      } else {
        promise.success(result)
      }
    }
    promise.future
  }

  // AdminClient for metadata operations (replaces KafkaConsumerActor)
  private lazy val adminClient: AdminClient = {
    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeout.toMillis.toString)
    AdminClient.create(props)
  }

  // Clean up AdminClient on system termination
  system.registerOnTermination {
    adminClient.close()
  }

  def topicsFuture: Future[Set[String]] = {
    toScalaFuture(adminClient.listTopics().names().toCompletionStage)
      .map(_.asScala.toSet)
  }


  /**
    * Getting a commitable source to start listening to Kafka. kafka handles offsets, commit should be called. (used by: Subscription)
    *
    * @param topics           - topics to listen and poll messages
    * @param consumerSettings - consumer's settings
    * @return - commitable source
    */
  def committableSource(topics: Seq[String], consumerSettings: ConsumerSettings[KeyType, ValueType]): Source[ConsumerComittableMessageType, _] = {

    log.info(s"Start listening to topics ${topics.toSet}")
    Consumer.committableSource(consumerSettings, Subscriptions.topics(topics.toSet))
  }

  /**
    * Getting a source to start listening to Kafka, from a certain timestamp.
    * Two options:
    * 1. Keep on listening (used by: CampaignServer)
    * 2. Read all messages and stop. finish after 'waitTimeInMs'. (used by: EventsManager for debugger)
    *
    * @param topics           - topics to listen and poll messages
    * @param consumerSettings - consumer's settings
    * @param timestamp        - listen to messages with  offset from this timestamp
    * @param waitTimeInMs     - by default -1, which means Option 1. Positive value to define when to stop reading, in Option 2
    * @return - source
    */
  def listenSince(topics: Seq[String],
                  consumerSettings: ConsumerSettings[KeyType, ValueType],
                  timestamp: Long = Platform.currentTime,
                  waitTimeInMs: Long = -1): Source[ConsumerMessageType, _] = {

    Source.fromFuture(getPartitionsPerTopic(topics))
      .flatMapMerge(1, { partitions =>
        log.info(s"Start listening to topics: $topics")
        log.info(s"Partitions per topic: $partitions")
        val partitionToTimeMap = Map(partitions.map({ a => new TopicPartition(a.topic(), a.partition()) -> long2Long(timestamp) }): _*)

        val baseSource = Consumer.plainSource(consumerSettings, Subscriptions.assignmentOffsetsForTimes(partitionToTimeMap.asJava))

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
      })
  }

  /**
    * when service initialize it gets from kafka all exiting topics with their partitions.
    * this method returns the partitions for specific topics
    *
    * @param topicsSeq - topics to get their partitions
    * @return - sequence of topic-partition pairs
    */
  def getPartitionsPerTopic(topicsSeq: Seq[String]): Future[Seq[PartitionInfo]] = {
    toScalaFuture(adminClient.describeTopics(topicsSeq.asJava).allTopicNames().toCompletionStage)
      .map { descriptions =>
        descriptions.asScala.values.flatMap { desc =>
          desc.partitions().asScala.map { partition =>
            new PartitionInfo(
              desc.name(),
              partition.partition(),
              partition.leader(),
              partition.replicas().toArray(new Array[org.apache.kafka.common.Node](0)),
              partition.isr().toArray(new Array[org.apache.kafka.common.Node](0))
            )
          }
        }.toSeq
      }
  }

  /**
    * create a basic cunsumerSettings with GroupId, ClientId and AutoOffsetReset values read from config
    *
    * @param continueFromGroupLastOffset - whether to give unique id to group, or use existing one
    * @return cunsumer's Settings
    */
  def createBasicConsumerSettings(continueFromGroupLastOffset: Boolean = true): ConsumerSettings[KeyType, ValueType] = {
    val processId = if (continueFromGroupLastOffset) "" else s"-${UUID.randomUUID()}"
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokers)
      .withGroupId(s"$defaultGroupId$processId")
      .withClientId(s"$defaultClientId$processId")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, defaultAutoOffsetReset)
  }

  private def createConsumerSettings(groupId: String, clientId: String, useUniqueId: Boolean = true): ConsumerSettings[KeyType, ValueType] = {
    val processId = if (useUniqueId) s"-${UUID.randomUUID()}" else ""
    ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokers)
      .withGroupId(s"$groupId$processId")
      .withClientId(s"$clientId$processId")
  }

  /**
    * Fetch the health for this registered checker.
    *
    * @return returns a future to the health information
    */
  override def getHealth: Future[HealthInfo] = {
    topicsFuture map { topics =>
      if (topics.isEmpty)
        HealthInfo(HealthState.CRITICAL, details = s"no topics")
      else
        HealthInfo(HealthState.OK, details = s"topics=$topics")
    } recover { case e =>
      HealthInfo(HealthState.CRITICAL, details = e.toString)
    }
  }
}

object KafkaConsumerService {

  import com.featurefm.riversong.Json4sProtocol._

  def fromBytes[T](bytes: Array[Byte])(implicit m: Manifest[T]): Try[T] =
    Try(serialization.read[T](new InputStreamReader(new ByteArrayInputStream(bytes))))


}
