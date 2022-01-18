package app.cash.chronicler.player.tracereplayer

import app.cash.chronicler.proto.Statement
import java.time.Duration

data class TraceReplayStats(
  val statementStats: List<StatementStats>
)

sealed class StatementStats {
  abstract val original: Statement

  abstract val execDuration: Duration

  val totalDuration: Duration get() = when (this) {
    is QueryStats -> execDuration + fetchDuration
    else -> execDuration
  }

  data class FailureStats(
    override val original: Statement,
    override val execDuration: Duration,

    val error: Exception
  ) : StatementStats()

  data class QueryStats(
    override val original: Statement,
    override val execDuration: Duration,

    val rowsFetched: Int,
    val fetchDuration: Duration
  ) : StatementStats()

  data class UpdateStats(
    override val original: Statement,
    override val execDuration: Duration,

    val rowsAffected: Int
  ) : StatementStats()
}
