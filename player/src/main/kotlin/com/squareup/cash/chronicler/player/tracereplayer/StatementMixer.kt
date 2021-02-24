package com.squareup.cash.chronicler.player.tracereplayer

import com.codahale.metrics.Gauge
import com.codahale.metrics.Meter
import com.codahale.metrics.MetricRegistry
import com.squareup.cash.chronicler.player.ReconstructedTrace
import com.squareup.cash.chronicler.player.ReconstructedTraceBuilder
import com.squareup.cash.chronicler.player.ext.max
import com.squareup.cash.chronicler.player.ext.pollWhile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import squareup.chronicler.Statement
import java.time.Duration
import java.time.Instant
import java.util.PriorityQueue

fun Flow<Statement>.mixStatements(
  metrics: StatementMixerMetrics,
  traceTtl: Duration = Duration.ofSeconds(30),
  stageCapacity: Int = 100000
): Flow<ReconstructedTrace> {
  val traceIndex = mutableMapOf<String, ReconstructedTraceBuilder>()
  val stageQueue = PriorityQueue<ReconstructedTraceBuilder>(stageCapacity, Comparator.comparing { it.traceStart })
  var eventTime: Instant = Instant.MIN

  return transform { statement ->
    // Append or create.
    traceIndex.compute(statement.trace_id) { _, elem ->
      elem?.also { it.add(statement) }
        ?: ReconstructedTraceBuilder(statement).also { stageQueue.add(it) }
    }!!

    eventTime = eventTime.max(statement.client_end!!)

    // Statements that are waiting since this time should be flushed.
    val expiryThreshold = statement.client_end!!.minusMillis(traceTtl.toMillis())

    stageQueue
      .pollWhile { it.traceStart < expiryThreshold }
      .forEach {
        traceIndex.remove(it.traceId)
        emit(it.build())
      }

    metrics.totalStatementsReceived.mark()
    metrics.stageQueueSize = stageQueue.size
    metrics.eventTime = eventTime
  }
}

class StatementMixerMetrics : MetricRegistry() {
  init {
    gauge("stage-queue-size") { Gauge { stageQueueSize } }
    gauge("event-time-string") { Gauge { eventTime.toString() } }
    gauge("event-time-epoch-sec") { Gauge { eventTime?.epochSecond } }
  }

  var stageQueueSize: Int? = null
  var eventTime: Instant? = null
  val totalStatementsReceived: Meter = meter("total-statements-received")
}
