package app.cash.chronicler.player.tracereplayer

import app.cash.chronicler.player.ReconstructedTrace
import app.cash.chronicler.player.RelativisticClock
import app.cash.chronicler.player.StatementType
import app.cash.chronicler.player.ext.duration
import app.cash.chronicler.player.type
import app.cash.chronicler.proto.Statement
import com.squareup.wire.durationOfSeconds
import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.SqlConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import kotlin.time.measureTimedValue
import kotlin.time.toJavaDuration

private val maxStatementTimeout = durationOfSeconds(45, 0)
private val minStatementTimeout = durationOfSeconds(15, 0)

fun Flow<ReconstructedTrace>.replayTraces(
  cp: Pool,
  count: Int,
  fetchRows: Boolean // TODO: Extract into config data class?){}
) = channelFlow {
  val replayer = TraceReplayer(cp, fetchRows)
  val semaphore = Semaphore(permits = count)
  collect { trace ->
    semaphore.acquire()
    launch {
      send(replayer.replay(trace))
    }.invokeOnCompletion {
      semaphore.release()
    }
  }
}

private class TraceReplayer(
  private val cp: Pool,
  private val fetchRows: Boolean // TODO: Extract into config data class?
) {
  suspend fun replay(trace: ReconstructedTrace): TraceReplayStats = coroutineScope {
    // SpeedFactor is 1 to replicate all delays within the trace
    val clock = RelativisticClock(trace.traceStart, 1)

    trace.statements.groupBy { it.thread_id }
      .values.map { statements ->
        async { replay(clock, statements) }
      }
      .awaitAll()
      .flatten()
      .let(::TraceReplayStats)
  }

  private suspend fun replay(clock: RelativisticClock, statements: Iterable<Statement>): List<StatementStats> {
    val connCache = CountingConnectionCache(
      cp = cp,
      limits = statements.groupBy { it.connection_id }.mapValues { (_, v) -> v.count() }
    )

    return try {
      statements
        .withDelays(clock.instant)
        .map { (delay, statement) ->
          clock.delayDuration(delay)

          connCache.execute(statement.connection_id) { conn -> conn.replay(statement) }
        }
    } finally {
      withContext(NonCancellable) {
        connCache.cleanup()
      }
    }
  }

  /**
   * Returns a list of pairs (Duration, Statement), signifying how much relative time to wait between executions
   * of Statements.
   *
   * For example, (1, s1), (2, s2) means -> wait 1, execute s1, wait 2, execute s2
   */
  private fun Iterable<Statement>.withDelays(startTime: Instant) = this@withDelays.let { statements ->
    listOf(
      Pair(
        first = Duration.between(startTime, statements.first().client_start!!),
        second = statements.first()
      )
    ) + statements.zipWithNext { a, b ->
      Pair(
        first = Duration.between(a.client_start, b.client_start),
        second = b
      )
    }
  }

  val Statement.maxExpectedDuration get() = this.duration.multipliedBy(20L).coerceIn(minStatementTimeout, maxStatementTimeout)

  /**
   * Replays the statement in given Connection.
   * If any error happens during execution - it is wrapped up into StatementStats.FailureStats
   */
  private suspend fun SqlConnection.replay(statement: Statement): StatementStats {
    measureTimedValue {
      try {
        return withTimeout(statement.maxExpectedDuration.toMillis()) {
          replayUnsafe(statement)
        }
      } catch (e: TimeoutCancellationException) {
        e
      } catch (e: CancellationException) {
        throw e // We want to propagate the real cancellation
      } catch (e: Exception) {
        e
      }
    }.let { (error, duration) ->
      return StatementStats.FailureStats(
        original = statement,
        execDuration = duration.toJavaDuration(),
        error = error
      )
    }
  }

  /**
   * Replays the statement in given Connection.
   * If any error happens during execution - it bubbles up.
   */
  private suspend fun SqlConnection.replayUnsafe(statement: Statement): StatementStats {
    val (result, executionDuration) = measureTimedValue {
      this@replayUnsafe.query(statement.sql).execute().await()
    }
    return when (statement.type) {
      StatementType.UPDATE,
      StatementType.DELETE,
      StatementType.INSERT,
      StatementType.TX,
      StatementType.SET,
      StatementType.UNKNOWN ->
        StatementStats.UpdateStats(
          original = statement,
          execDuration = executionDuration.toJavaDuration(),
          rowsAffected = result.rowCount()
        )
      StatementType.QUERY -> {
        if (fetchRows) {
          val (rowsFetched, fetchDuration) = measureTimedValue { result.asFlow().count() }
          StatementStats.QueryStats(
            original = statement,
            execDuration = executionDuration.toJavaDuration(),
            rowsFetched = rowsFetched,
            fetchDuration = fetchDuration.toJavaDuration()
          )
        } else {
          StatementStats.QueryStats(
            original = statement,
            execDuration = executionDuration.toJavaDuration(),
            rowsFetched = -1,
            fetchDuration = Duration.ZERO
          )
        }
      }
    }
  }
}
