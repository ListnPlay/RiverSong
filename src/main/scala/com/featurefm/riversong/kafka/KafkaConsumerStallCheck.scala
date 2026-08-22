package com.featurefm.riversong.kafka

import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.kafka.{TopicPartitionsAssigned, TopicPartitionsRevoked}
import com.featurefm.riversong.Configurable
import com.featurefm.riversong.health.{HealthCheck, HealthInfo, HealthState}
import com.featurefm.riversong.metrics.Instrumented
import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, OffsetSpec}
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Generic liveness watchdog for a RiverSong Kafka consumer.
 *
 * It detects a partition whose committed offset is frozen while a backlog grows — a wedged
 * consumer partition. That failure keeps the JVM alive and the consumer's other partitions
 * flowing, so it is NOT caught by a process-level liveness probe. This check reports
 * `HealthState.CRITICAL` for such a partition, which makes the aggregated `/health` endpoint
 * return HTTP 503 (any CRITICAL check ⇒ 503). Point the k8s livenessProbe at `/health` so
 * Kubernetes restarts the pod, which clears the stall.
 *
 * Explicit, per requested topic-set. Typical wiring:
 * {{{
 *   // 1. create one check for the topics you want guarded, and register it as a HealthCheck
 *   val stall = new KafkaConsumerStallCheck(Set("my-topic"), config.getString("kafka.receive.group-id"))
 *   // 2. attach its rebalance listener to that consumer's source so it learns this pod's assignment
 *   val src = kafkaConsumerService.committableSource(Seq("my-topic"), settings, Some(stall.rebalanceListenerRef))
 *   // 3. start the background poll (after the stream is materialising)
 *   stall.start()
 * }}}
 *
 * Detection is per-replica: the AdminClient reads committed offsets group-wide, but only the
 * partitions currently ASSIGNED to THIS consumer (tracked via the rebalance listener) are
 * evaluated, so only the replica that owns a wedged partition reports CRITICAL and restarts.
 *
 * A partition is flagged only when it has a backlog (`lag >= min-lag`) AND its committed offset
 * has not advanced for at least `stall-timeout`. That ignores idle partitions (lag ~0) and
 * healthy backlog drains (offsets keep advancing during a drain). Fail-open: if the check's own
 * AdminClient calls fail, it never escalates to CRITICAL — it keeps the last known state — so a
 * broker hiccup cannot itself cause restarts.
 *
 * Defaults live under `kafka.consumer.stall-detector` in reference.conf and may be overridden in
 * the service's application.conf.
 */
class KafkaConsumerStallCheck(topics: Set[String], groupId: String)(implicit val system: ActorSystem)
    extends HealthCheck with Configurable with Instrumented {

  import KafkaConsumerStallCheck.classify

  private val log = Logging(system, getClass)

  private val topicsId: String = topics.toSeq.sorted.mkString("_")
  override lazy val healthCheckName: String = s"kafka-consumer-stall-$topicsId"

  // Status-aware: this is a liveness signal, not just an informational health line. When the
  // service sets `health-check.use-in-status = true`, RiverSong's HealthMonitorActor polls the
  // status-aware checks and drives the StatusActor that `/status` reads — so a stall makes the
  // existing `/status` probe return 503 (no need to repoint the probe to `/health`). Because
  // typical dependency checks (mongo/http/etc.) are NOT status-aware, `/status` then reflects
  // ONLY real stalls, not dependency blips. Independent of `/health`, which always includes it.
  override lazy val isStatusAware: Boolean = true

  private val brokers: String        = config.getString("kafka.hosts")
  private val checkInterval: FiniteDuration =
    config.getDuration("kafka.consumer.stall-detector.check-interval").toMillis.millis
  private val stallTimeoutMs: Long   = config.getDuration("kafka.consumer.stall-detector.stall-timeout").toMillis
  private val minLag: Long           = config.getLong("kafka.consumer.stall-detector.min-lag")
  private val adminTimeoutMs: Long   = config.getDuration("kafka.consumer.stall-detector.admin-timeout").toMillis
  private val assignmentWarnAfter: FiniteDuration =
    config.getDuration("kafka.consumer.stall-detector.assignment-warn-after").toMillis.millis

  // Partitions currently assigned to THIS consumer, maintained by the rebalance listener.
  private val assigned: java.util.Set[TopicPartition] = ConcurrentHashMap.newKeySet[TopicPartition]()
  // Set true the first time ANY rebalance callback arrives — proof the listener is actually wired
  // to the consumer. Used to warn about a misconfiguration (listener never attached).
  private val assignmentObserved = new AtomicBoolean(false)

  /** Attach this to the consumer subscription (`committableSource(.., Some(ref))`) so the check
    * learns which partitions this replica owns. Safe to share across streams of the same group. */
  val rebalanceListenerRef: ActorRef =
    system.actorOf(Props(new KafkaConsumerStallCheck.RebalanceListenerActor(assigned, assignmentObserved)),
      s"kafka-stall-rebalance-listener-$topicsId")

  // Per-partition (lastCommittedOffset, lastAdvancedAtMs) for owned partitions only.
  private val progress = new ConcurrentHashMap[TopicPartition, (Long, Long)]()

  @volatile private var stalled: List[String] = Nil
  @volatile private var lastMaxLag: Long = 0L
  private val lastPollErrorLogMs = new AtomicLong(0L)
  private val startedFlag = new AtomicBoolean(false)

  gauge("stalled_partitions", topicsId) { stalled.size }
  gauge("max_lag", topicsId) { lastMaxLag }

  private lazy val pollEc: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor { r =>
      val t = new Thread(r, s"kafka-stall-check-$topicsId"); t.setDaemon(true); t
    })

  private var admin: AdminClient = _ // only touched from the single poll thread

  private def adminClient(): AdminClient = {
    if (admin == null) {
      val props = new java.util.Properties()
      props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
      props.put(AdminClientConfig.CLIENT_ID_CONFIG, s"kafka-stall-check-$topicsId")
      props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, adminTimeoutMs.toInt.toString)
      props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (adminTimeoutMs * 2).toInt.toString)
      admin = AdminClient.create(props)
    }
    admin
  }

  /** Begin the background poll. Idempotent. */
  def start(): Unit = if (startedFlag.compareAndSet(false, true)) {
    log.info(s"Kafka consumer stall check active: group=$groupId topics=${topics.mkString(",")} " +
      s"interval=$checkInterval stallTimeout=${stallTimeoutMs}ms minLag=$minLag")
    system.scheduler.scheduleWithFixedDelay(checkInterval, checkInterval)(
      new Runnable { override def run(): Unit = poll() })(pollEc)

    // Misconfiguration guard: if no rebalance callback has arrived within `assignmentWarnAfter`,
    // the rebalanceListenerRef was almost certainly never attached to the consumer, so `assigned`
    // stays empty and stall detection silently never fires. Warn loudly so it gets noticed.
    system.scheduler.scheduleOnce(assignmentWarnAfter, new Runnable {
      override def run(): Unit = if (!assignmentObserved.get())
        log.warning(s"Kafka consumer stall check for topics [${topics.mkString(",")}] (group=$groupId) has " +
          s"observed NO partition assignment ${assignmentWarnAfter} after start — its rebalance listener is " +
          s"probably not attached. Pass this check's rebalanceListenerRef to " +
          s"KafkaConsumerService.committableSource(topics, settings, Some(ref)). Stall detection is INACTIVE " +
          s"until an assignment is observed.")
    })(pollEc)
  }

  /** Stop and release the AdminClient (mainly for tests / clean shutdown). */
  def close(): Unit = if (admin != null) try admin.close() catch { case _: Throwable => () }

  private def poll(): Unit = {
    try {
      val a = adminClient()

      val committed: Map[TopicPartition, Long] = a.listConsumerGroupOffsets(groupId)
        .partitionsToOffsetAndMetadata().get(adminTimeoutMs, TimeUnit.MILLISECONDS)
        .asScala.toMap
        .collect { case (tp, om) if topics.contains(tp.topic) => tp -> om.offset() }

      if (committed.isEmpty) {
        stalled = Nil; lastMaxLag = 0L
        return
      }

      val specs = committed.keys.map(tp => tp -> OffsetSpec.latest()).toMap.asJava
      val endOffsets: Map[TopicPartition, Long] = a.listOffsets(specs).all()
        .get(adminTimeoutMs, TimeUnit.MILLISECONDS)
        .asScala.map { case (tp, info) => tp -> info.offset() }.toMap

      val (stalledNow, maxLag) =
        classify(progress, committed, endOffsets, assigned.asScala.toSet,
          System.currentTimeMillis(), minLag, stallTimeoutMs)

      stalled = stalledNow
      lastMaxLag = maxLag

      if (stalledNow.nonEmpty)
        log.error(s"Kafka consumer STALL detected on ${stalledNow.size} partition(s): " +
          s"${stalledNow.mkString(", ")}. /health will report CRITICAL so k8s can restart this pod.")
    } catch {
      // Fail-open: a failure of our own probe must never escalate to CRITICAL. Keep last state.
      case ex: Throwable =>
        val nowMs = System.currentTimeMillis()
        val last = lastPollErrorLogMs.get()
        if (nowMs - last >= 60000 && lastPollErrorLogMs.compareAndSet(last, nowMs))
          log.warning(s"Kafka stall check poll failed (will retry; health unaffected): " +
            Option(ex.getMessage).getOrElse(ex.toString))
    }
  }

  override def getHealth: Future[HealthInfo] = stalled match {
    case Nil => Future.successful(HealthInfo(HealthState.OK, s"no stalled partitions (maxLag=$lastMaxLag)"))
    case xs  => Future.successful(HealthInfo(HealthState.CRITICAL, s"stalled consumer partitions: ${xs.mkString(", ")}"))
  }
}

object KafkaConsumerStallCheck {

  private def render(tps: Set[TopicPartition]): String =
    tps.toSeq.map(t => s"${t.topic}-${t.partition}").sorted.mkString(",")

  private class RebalanceListenerActor(assigned: java.util.Set[TopicPartition],
                                       assignmentObserved: AtomicBoolean) extends Actor {
    private val log: LoggingAdapter = Logging(context.system, getClass)
    override def receive: Receive = {
      case TopicPartitionsAssigned(_, tps) =>
        assignmentObserved.set(true) // any callback proves the listener is wired
        assigned.addAll(tps.asJava)
        log.info(s"Kafka consumer assigned ${tps.size} partition(s): ${render(tps)}")
      case TopicPartitionsRevoked(_, tps) =>
        assignmentObserved.set(true)
        assigned.removeAll(tps.asJava)
        log.info(s"Kafka consumer revoked ${tps.size} partition(s): ${render(tps)}")
    }
  }

  /**
   * Pure stall decision, shared with unit tests (no ActorSystem / Kafka needed).
   *
   * Only partitions in `owned` (this replica's current assignment) are considered. Mutates
   * `progress` in place: for each owned partition it records the last committed offset and the
   * wall-clock time that offset last ADVANCED. A partition is reported stalled when it has a
   * backlog (`lag >= minLag`) and its committed offset has not advanced for at least
   * `stallTimeoutMs`. Partitions no longer owned (revoked in a rebalance) are pruned so a
   * partition that moved to another replica can't be flagged here forever.
   *
   * @return (stalled partition descriptions, max lag across owned partitions this round)
   */
  def classify(progress: ConcurrentHashMap[TopicPartition, (Long, Long)],
               committed: Map[TopicPartition, Long],
               endOffsets: Map[TopicPartition, Long],
               owned: Set[TopicPartition],
               now: Long, minLag: Long, stallTimeoutMs: Long): (List[String], Long) = {
    val mine = committed.filter { case (tp, _) => owned.contains(tp) }

    var maxLag = 0L
    val stalledNow = List.newBuilder[String]

    mine.foreach { case (tp, committedOffset) =>
      val endOffset = endOffsets.getOrElse(tp, committedOffset)
      val lag = math.max(0L, endOffset - committedOffset)
      if (lag > maxLag) maxLag = lag

      val prev = Option(progress.get(tp))
      // No prior sample => first observation (grace period). Otherwise "advanced" only if the
      // committed offset strictly increased; a frozen offset keeps its earlier timestamp so the
      // stall age accrues.
      val advanced = prev.forall { case (prevOffset, _) => committedOffset > prevOffset }
      if (advanced) progress.put(tp, (committedOffset, now))

      val frozenSinceMs = Option(progress.get(tp)).map(_._2).getOrElse(now)
      val frozenForMs = now - frozenSinceMs
      if (lag >= minLag && frozenForMs >= stallTimeoutMs)
        stalledNow += s"${tp.topic}-${tp.partition}(lag=$lag,frozen=${frozenForMs / 1000}s)"
    }

    // Drop progress for partitions we no longer own (revoked) or that vanished from the group.
    progress.keySet().asScala.toList.filterNot(mine.contains).foreach(progress.remove)
    (stalledNow.result(), maxLag)
  }
}
