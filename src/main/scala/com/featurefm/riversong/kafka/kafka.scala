package com.featurefm.riversong

import akka.kafka.ProducerMessage
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords}

import scala.concurrent.Promise

package object kafka {

  type KeyType = String
  type ValueType = Array[Byte]

  type ConsumerMessageType = ConsumerRecord[KeyType,ValueType]
  type ConsumerMessagesType = ConsumerRecords[KeyType,ValueType]

  type ProducerMsgType = ProducerMessage.Message[KeyType, ValueType, Promise[Long]]
  type ProducerResultType = ProducerMessage.Result[KeyType, ValueType, Promise[Long]]

}
