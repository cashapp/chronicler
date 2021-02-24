package com.squareup.cash.chronicler.kpl.canonical

import com.amazonaws.auth.AWSCredentialsProvider
import com.codahale.metrics.Metric
import com.codahale.metrics.MetricRegistry
import com.squareup.cash.chronicler.ChroniclerInterceptor
import com.squareup.cash.chronicler.SamplingRule
import com.squareup.cash.chronicler.SamplingRules
import com.squareup.cash.chronicler.SamplingRules.traceRatio
import com.squareup.cash.chronicler.StatementSink
import com.squareup.cash.chronicler.StatementSinks.noop
import com.squareup.cash.chronicler.kpl.ChroniclerKplSink
import org.apache.log4j.Logger
import java.time.Clock
import java.util.function.Supplier
import java.util.regex.Pattern

/**
 * Class which acts as an instance of ChroniclerInterceptor.Config (used by ChroniclerInterceptor)
 * which is built from Franklin's configuration.
 *
 * Kinesis stream is used as a sink.
 */
class CanonicalChroniclerInterceptorConfig(
  private val kinesisConfig: ChroniclerKinesisConfig,
  private val awsCredentialsProvider: AWSCredentialsProvider,
  val metrics: MetricRegistry,
  override val clock: Clock,
  private val traceIdProducer: Supplier<String?>
) : ChroniclerInterceptor.Config, AutoCloseable {
  override lateinit var statementSink: StatementSink
  override lateinit var samplingRule: SamplingRule

  class Factory(
    private val awsCredentialsProvider: AWSCredentialsProvider,
    private val metricRegistry: MetricRegistry,
    private val clock: Clock
  ) {
    fun build(
      kinesisConfig: ChroniclerKinesisConfig,
      traceIdProducer: Supplier<String?>
    ): CanonicalChroniclerInterceptorConfig {
      return CanonicalChroniclerInterceptorConfig(
        kinesisConfig,
        awsCredentialsProvider,
        metricRegistry,
        clock,
        traceIdProducer
      )
    }
  }

  override fun buildCallContext(): ChroniclerInterceptor.CallContext {
    val threadId = Integer.toString(System.identityHashCode(Thread.currentThread()), 32)
    // In case of trace missing, we will use buckets of 1 second duration per thread as traceIds.
    val fallbackTraceId = "thread-" + threadId + "-" + System.currentTimeMillis() / 1000
    val providedTraceId = traceIdProducer.get()
    return ChroniclerInterceptor.CallContext(
      providedTraceId ?: fallbackTraceId,
      threadId
    )
  }

  override fun close() {
    logger.info("Shutting down chronicler interceptor config")
    // Clean up old sink if present
    if (::statementSink.isInitialized && statementSink is ChroniclerKplSink) {
      val sink = statementSink as ChroniclerKplSink?
      sink!!.close()
      metrics.removeMatching { name: String, _: Metric? -> name.startsWith("chronicler.sink") }
    }
    statementSink = noop()
    samplingRule = traceRatio(0.0)
  }

  fun init(config: DynamicChroniclerConfig?) {
    logger.info("Resetting chronicler interceptor config")
    close()
    if (config != null && config.sampleRate > 0) {
      logger.info(
        String.format(
          "Initializing FranklinChroniclerInterceptorConfig with following configuration: %s",
          config
        )
      )
      val kplSink = ChroniclerKplSink(
        ChroniclerKplSink.Config(
          true,
          kinesisConfig.endpoint,
          kinesisConfig.region,
          awsCredentialsProvider,
          kinesisConfig.stream,
          config.kplAggregateMessages,
          config.kplMaxOutstandingRecords
        )
      )
      statementSink = kplSink
      metrics.register("chronicler.sink", kplSink.metrics)
      val rules = SamplingRules
      samplingRule = rules.and(
        rules.traceRatio(config.sampleRate),
        rules.sqlDenylist(
          Pattern.compile(
            config.denylistedQueryRegex,
            Pattern.CASE_INSENSITIVE
          )
        )
      )
    }
  }

  companion object {
    private val logger = Logger.getLogger(CanonicalChroniclerInterceptorConfig::class.java)
  }

  init {
    init(null)
  }
}
