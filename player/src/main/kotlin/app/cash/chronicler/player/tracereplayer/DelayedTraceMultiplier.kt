package app.cash.chronicler.player.tracereplayer

import app.cash.chronicler.player.ReconstructedTrace
import app.cash.chronicler.player.StatementType
import app.cash.chronicler.player.delayedCopy
import app.cash.chronicler.player.ext.max
import app.cash.chronicler.player.ext.pollWhile
import app.cash.chronicler.player.type
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.time.Duration
import java.time.Instant
import java.util.PriorityQueue

data class TraceMultiplierConfig(
  val traceMultiplier: Int,
  val traceMultiplierDelay: Duration
)

data class TraceToBeMultiplied(
  val nextTrace: ReconstructedTrace,
  val copiesPending: Int
)

fun Flow<ReconstructedTrace>.multiplyStatements(
  cfg: TraceMultiplierConfig,
  queueCapacity: Int = 100000
): Flow<ReconstructedTrace> {
  val queue = PriorityQueue<TraceToBeMultiplied>(queueCapacity, Comparator.comparing { it.nextTrace.traceStart })
  var eventTime: Instant = Instant.MIN

  return transform { trace ->
    eventTime = eventTime.max(trace.traceStart)

    queue
      .pollWhile { it.nextTrace.traceStart <= eventTime }
      .forEach { queueTrace ->
        if (queueTrace.copiesPending > 1) {
          queue.add(
            TraceToBeMultiplied(
              nextTrace = queueTrace.nextTrace.delayedCopy(cfg.traceMultiplierDelay),
              copiesPending = queueTrace.copiesPending - 1
            )
          )
        }
        emit(queueTrace.nextTrace)
      }

    emit(trace)

    if (cfg.traceMultiplier > 1 && trace.statements.none { it.type == StatementType.INSERT }) {
      queue.add(
        TraceToBeMultiplied(
          nextTrace = trace.delayedCopy(cfg.traceMultiplierDelay),
          copiesPending = cfg.traceMultiplier - 1
        )
      )
    }
  }
}
