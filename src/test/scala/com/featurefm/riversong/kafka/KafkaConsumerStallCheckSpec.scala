package com.featurefm.riversong.kafka

import java.util.concurrent.ConcurrentHashMap

import org.apache.kafka.common.TopicPartition
import org.scalatest.{FlatSpec, Matchers}

/**
 * Pure-logic tests for KafkaConsumerStallCheck.classify — no ActorSystem / Kafka / AdminClient.
 */
class KafkaConsumerStallCheckSpec extends FlatSpec with Matchers {

  private val topic = "my-topic"
  private def tp(p: Int) = new TopicPartition(topic, p)

  private val Timeout = 600000L // 10 minutes
  private val MinLag  = 1L

  private def newProgress = new ConcurrentHashMap[TopicPartition, (Long, Long)]()

  // Default: this replica owns whatever partitions the test passes in `committed`.
  private def classify(progress: ConcurrentHashMap[TopicPartition, (Long, Long)],
                       committed: Map[TopicPartition, Long],
                       endOffsets: Map[TopicPartition, Long],
                       now: Long,
                       owned: Set[TopicPartition] = null) = {
    val ownedSet = Option(owned).getOrElse(committed.keySet)
    KafkaConsumerStallCheck.classify(progress, committed, endOffsets, ownedSet, now, MinLag, Timeout)
  }

  behavior of "KafkaConsumerStallCheck.classify"

  it should "never flag a partition whose committed offset keeps advancing (healthy drain)" in {
    val prog = newProgress
    var offset = 100L
    for (t <- 0L to (Timeout * 3) by 60000L) {
      offset += 500
      val (stalled, maxLag) = classify(prog, Map(tp(10) -> offset), Map(tp(10) -> (offset + 100000)), t)
      stalled shouldBe empty
      maxLag shouldBe 100000L
    }
  }

  it should "flag a partition only after its frozen offset exceeds the stall timeout, with a backlog" in {
    val prog = newProgress
    classify(prog, Map(tp(10) -> 100L), Map(tp(10) -> 200L), 0L)._1 shouldBe empty
    classify(prog, Map(tp(10) -> 100L), Map(tp(10) -> 250L), Timeout - 1)._1 shouldBe empty
    val (stalled, _) = classify(prog, Map(tp(10) -> 100L), Map(tp(10) -> 300L), Timeout)
    stalled should have size 1
    stalled.head should include(s"$topic-10")
  }

  it should "not flag an idle/caught-up partition even if its offset never moves (lag below min-lag)" in {
    val prog = newProgress
    classify(prog, Map(tp(3) -> 500L), Map(tp(3) -> 500L), 0L)._1 shouldBe empty
    classify(prog, Map(tp(3) -> 500L), Map(tp(3) -> 500L), Timeout * 5)._1 shouldBe empty
  }

  it should "clear the stall once the committed offset advances again" in {
    val prog = newProgress
    classify(prog, Map(tp(10) -> 100L), Map(tp(10) -> 300L), 0L)
    classify(prog, Map(tp(10) -> 100L), Map(tp(10) -> 300L), Timeout)._1 should have size 1
    classify(prog, Map(tp(10) -> 300L), Map(tp(10) -> 300L), Timeout + 1000)._1 shouldBe empty
  }

  it should "only flag the wedged partition, not its healthy neighbours on the same consumer" in {
    val prog = newProgress
    def round(now: Long, p10Committed: Long, p11Committed: Long) =
      classify(prog,
        Map(tp(10) -> p10Committed, tp(11) -> p11Committed),
        Map(tp(10) -> (p10Committed + 5000), tp(11) -> (p11Committed + 5000)),
        now)

    round(0L, 100L, 100L)._1 shouldBe empty
    val (stalled, _) = round(Timeout, 100L, 900L)
    stalled should have size 1
    stalled.head should include(s"$topic-10")
  }

  it should "never flag a partition this replica does not own, even if it is wedged group-wide" in {
    val prog = newProgress
    val owned = Set(tp(11))
    classify(prog, Map(tp(10) -> 100L, tp(11) -> 100L), Map(tp(10) -> 300L, tp(11) -> 100L), 0L, owned)
    val (stalled, _) =
      classify(prog, Map(tp(10) -> 100L, tp(11) -> 100L), Map(tp(10) -> 500L, tp(11) -> 100L), Timeout, owned)
    stalled shouldBe empty
    prog.containsKey(tp(10)) shouldBe false
  }

  it should "prune partitions that are no longer assigned/reported by the group" in {
    val prog = newProgress
    classify(prog, Map(tp(10) -> 100L), Map(tp(10) -> 200L), 0L)
    prog.containsKey(tp(10)) shouldBe true
    classify(prog, Map(tp(11) -> 50L), Map(tp(11) -> 50L), 1000L)
    prog.containsKey(tp(10)) shouldBe false
  }
}
