package com.squareup.cash.chronicler.player

import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.Meter
import com.codahale.metrics.MetricRegistry
import com.squareup.cash.chronicler.player.ext.duration
import com.squareup.cash.chronicler.player.ext.inc
import com.squareup.cash.chronicler.player.tracereplayer.StatementStats
import com.squareup.cash.chronicler.player.tracereplayer.TraceReplayStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import org.slf4j.Logger
import java.time.Duration
import kotlin.math.max

suspend fun Flow<TraceReplayStats>.collectMetrics(
  metrics: MetricRegistry,
  cfg: ReplayerConfig,
  logger: Logger
) {
  val traceStats = metrics.durationStats("replayer.traces")
  val statementStats = metrics.durationStats("replayer.statements")
  val errorCounter = metrics.counter("replayer.statements.errors")
  metrics.gauge("replayer.timefactor") { Gauge { cfg.playbackSpeed } }

  collect { stats ->
    traceStats.update(
      original = Duration.between(
        stats.statementStats.first().original.client_start,
        stats.statementStats.last().original.client_end
      ),
      replayed = stats.statementStats
        .sumOf { it.totalDuration.toMillis() }
        .let(Duration::ofMillis)
    )

    stats.statementStats
      .filter { it !is StatementStats.FailureStats }
      .forEach { stat ->
        statementStats.update(stat.original.duration, stat.totalDuration)
      }

    val errorStatements = stats.statementStats.count { it is StatementStats.FailureStats }
    if (errorStatements > 0) {
      logger.warn("Trace contains errors: $stats")
      errorCounter.inc(errorStatements)
    }
  }
}

private fun MetricRegistry.durationStats(namespace: String) = DurationStats(
  replayedFasterBy = histogram("$namespace.replayedFasterBy"),
  replayedSlowerBy = histogram("$namespace.replayedSlowerBy"),
  replayedFasterPercent = histogram("$namespace.replayedFasterPercent"),
  replayedSlowerPercent = histogram("$namespace.replayedSlowerPercent"),
  originalDurationMsec = histogram("$namespace.originalDurationMsec"),
  replayedDurationMsec = histogram("$namespace.replayedDurationMsec"),
  totalMeter = meter("$namespace.totalMeter")
)

private data class DurationStats(
  val replayedFasterBy: Histogram,
  val replayedSlowerBy: Histogram,
  val replayedFasterPercent: Histogram,
  val replayedSlowerPercent: Histogram,
  val originalDurationMsec: Histogram,
  val replayedDurationMsec: Histogram,
  val totalMeter: Meter
)

private fun DurationStats.update(original: Duration, replayed: Duration) {
  replayedFasterBy.update(original.toMillis() - replayed.toMillis())
  replayedSlowerBy.update(replayed.toMillis() - original.toMillis())
  replayedFasterPercent.update(original.toMillis() * 100 / max(replayed.toMillis(), 1))
  replayedSlowerPercent.update(replayed.toMillis() * 100 / max(original.toMillis(), 1))
  originalDurationMsec.update(original.toMillis())
  replayedDurationMsec.update(replayed.toMillis())
  totalMeter.mark()
}
