package com.squareup.cash.chronicler

import com.mysql.cj.MysqlConnection
import com.mysql.cj.Query
import com.mysql.cj.interceptors.QueryInterceptor
import com.mysql.cj.log.Log
import com.mysql.cj.protocol.Resultset
import com.mysql.cj.protocol.ServerSession
import squareup.chronicler.Statement
import java.time.Clock
import java.time.Instant
import java.util.Properties
import java.util.function.Supplier

const val configNameProperty = "chroniclerConfigName"

class ChroniclerInterceptor : QueryInterceptor {
  private lateinit var log: Log

  private var inFlightQueryDetails: InFlightQueryDetails? = null
  private var config: Config? = null

  override fun init(
    conn: MysqlConnection,
    props: Properties,
    log: Log
  ) = this.also {
    this.log = log

    val configName = (
      props.getProperty(configNameProperty)
        ?: error(
          "Chronicler config name property not set on jdbc connection: $configNameProperty !"
        )
      )

    this.log.logInfo("Subscribing to configuration '$configName'")
    ChroniclerConfigRepository.subsribe(configName) {
      this.log.logInfo("Configuration set for configName '$configName'")
      config = it
    }
  }

  override fun <T : Resultset> preProcess(
    sqlSupplier: Supplier<String?>,
    query: Query?
  ): T? = null.also {
    try {
      sample(sqlSupplier) { ctx, cfg ->
        inFlightQueryDetails = InFlightQueryDetails(
          samplingContext = ctx,
          clientStart = cfg.clock.instant()
        )
      }
    } catch (e: Exception) {
      log.logError("Failed to preProcess query", e)
    }
  }

  override fun <T : Resultset> postProcess(
    sqlSupplier: Supplier<String?>,
    query: Query?,
    originalResultSet: T?,
    serverSession: ServerSession
  ): T? = originalResultSet.also {
    try {
      sample(sqlSupplier) { ctx, cfg ->
        inFlightQueryDetails
          ?.takeIf { it.samplingContext == ctx }
          ?.let { inFlightQuery ->
            Statement.Builder()
              .apply {
                trace_id = ctx.traceId
                connection_id = serverSession.capabilities.threadId.toString(32)
                thread_id = ctx.threadId

                client_start = inFlightQuery.clientStart
                client_end = cfg.clock.instant()
                sql = ctx.sql
                success = originalResultSet != null // Weird way Connector/J does this.
                rows_affected = originalResultSet?.updateCount ?: 0
              }
              .build()
              .let(cfg.statementSink::accept)
          }
        inFlightQueryDetails = null
      }
    } catch (e: Exception) {
      log.logError("Failed to postProcess query", e)
    }
  }

  private fun sample(
    sqlSupplier: Supplier<String?>,
    proc: (ctx: SamplingContext, cfg: Config) -> Unit
  ) = config?.let { cfg ->
    cfg.buildCallContext()
      .takeIf { cfg.samplingRule.shouldSample(it) }
      ?.let { ctx ->
        sqlSupplier.get()
          ?.takeIf { sql -> cfg.samplingRule.shouldSample(sql) }
          ?.let { sql ->
            SamplingContext(
              traceId = ctx.traceId,
              threadId = ctx.threadId,
              sql = sql
            )
          }
      }
      ?.let { samplingContext ->
        proc(samplingContext, cfg)
      }
  }

  override fun executeTopLevelOnly() = false
  override fun destroy() = Unit

  private data class SamplingContext(
    val traceId: String,
    val threadId: String,
    val sql: String
  )

  private data class InFlightQueryDetails(
    val samplingContext: SamplingContext,
    val clientStart: Instant
  )

  interface Config {
    /**
     * Builds contextual information from the current thread or static context.
     * Can be used to extract traceId from MDC context, or, alternatively, generate a random identifier to
     * disable SQL request grouping into "application requests".
     */
    fun buildCallContext(): CallContext

    /**
     * A rule determining which requests are sampled. This includes both filtering the requests (ignoring
     * un-interesting queries) and actual sampling percentages.
     *
     * This can be implemented from scratch or use builder methods inside `SamplingRules` class.
     */
    val samplingRule: SamplingRule

    /**
     * Sink that will receive all sampled requests.
     */
    val statementSink: StatementSink

    /**
     * Clock implementation used to calculate duration of queries and timing.
     */
    val clock: Clock
  }

  /**
   * Class identifying a trace. TraceId should correlate to an application layer request (or job execution, etc),
   * while threadId is a sub-thread in said application layer request. Usually traceId is extracted from MDC context,
   * while threadId can be either a simple Thread.name or an id extracted from a coroutine.
   */
  data class CallContext(
    val traceId: String,
    val threadId: String
  )
}
