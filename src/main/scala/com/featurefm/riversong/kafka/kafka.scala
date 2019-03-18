package com.featurefm.riversong

import akka.kafka.{ConsumerMessage, ProducerMessage}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords}

import scala.concurrent.Promise
import scala.util.Try

package object kafka {

  type KeyType = String
  type ValueType = Array[Byte]

  type ConsumerMessageType = ConsumerRecord[KeyType, ValueType]
  type ConsumerMessageAnyType = ConsumerRecord[KeyType, Try[AnyRef]]
  type ConsumerComittableMessageType = ConsumerMessage.CommittableMessage[KeyType, ValueType]

  type ProducerMsgType = ProducerMessage.Message[KeyType, ValueType, Promise[Long]]
  type ProducerResultType = ProducerMessage.Result[KeyType, ValueType, Promise[Long]]

}
