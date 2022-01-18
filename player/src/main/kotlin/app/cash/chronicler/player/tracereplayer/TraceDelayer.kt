package app.cash.chronicler.player.tracereplayer

import app.cash.chronicler.player.ReconstructedTrace
import app.cash.chronicler.player.RelativisticClock
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.time.Instant

fun Flow<ReconstructedTrace>.delayTraces(
  timeFactor: Int,
  metrics: TraceDelayerMetrics
): Flow<ReconstructedTrace> {
  var relativisticClock: RelativisticClock? = null

  return transform { trace ->
    relativisticClock = relativisticClock ?: RelativisticClock(trace.traceStart, timeFactor)

    metrics.queuedMessageTime = trace.traceStart
    val (relativeDelay, realDelay) = relativisticClock!!.delayUntil(trace.traceStart)

    metrics.traceDelayRelativeMillis.update(relativeDelay.toMillis())
    metrics.traceDelayRealNanos.update(realDelay.toNanos())

    emit(trace)
    metrics.lastDispatchedTime = trace.traceStart
  }
}

class TraceDelayerMetrics : MetricRegistry() {
  val traceDelayRealNanos: Histogram = histogram("trace-delay-real-nsec")
  val traceDelayRelativeMillis: Histogram = histogram("trace-delay-relative-msec")

  var queuedMessageTime: Instant? = null
  var lastDispatchedTime: Instant? = null

  init {
    gauge("queued-message-time") { Gauge { queuedMessageTime?.epochSecond ?: "" } }
    gauge("last-dispatched-time") { Gauge { lastDispatchedTime?.epochSecond ?: "" } }
  }
}
