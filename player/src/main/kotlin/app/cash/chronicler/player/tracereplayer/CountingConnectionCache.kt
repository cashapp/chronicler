package app.cash.chronicler.player.tracereplayer

import io.vertx.kotlin.coroutines.await
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.SqlConnection
import kotlinx.coroutines.coroutineScope

/**
 * Lazy-initializes a connection from connection pool and caches it by it's id.
 * Also contains a map of expiration counters. Every query the counter is decremented, when counter reaches zero -
 * connection is closed. This is made for the TraceReplayer use-case, when the number of queries to run on each
 * connection is known in advance, we want to lazy-init them, and close as quickly as possible.
 */
class CountingConnectionCache(
  private val cp: Pool,
  limits: Map<String, Int>
) {
  private val cachedConnections = mutableMapOf<String, SqlConnection>()
  private val connectionQueriesRemaining = limits.toMutableMap()

  suspend fun <T> execute(connectionId: String, proc: suspend (SqlConnection) -> T): T {
    val conn = cachedConnections.getOrPut(connectionId) { cp.connection.await() }
    return proc(conn).also { maybeClose(connectionId) }
  }

  private suspend fun close(connectionId: String) {
    connectionQueriesRemaining.remove(connectionId)
    cachedConnections.remove(connectionId)?.close()?.await()
  }

  // Simple reference counting connection GC
  private suspend fun maybeClose(connectionId: String) {
    if (connectionQueriesRemaining.computeIfPresent(connectionId) { _, v -> v - 1 } == 0) {
      close(connectionId)
    }
  }

  suspend fun cleanup() = coroutineScope {
    connectionQueriesRemaining.replaceAll { _, _ -> 0 } // Reset all gc counters to 0
    cachedConnections.keys.forEach { connId ->
      maybeClose(connId)
    }
  }
}
