package com.squareup.cash.chronicler.player

import com.codahale.metrics.MetricRegistry
import com.squareup.cash.chronicler.player.ext.installJvmShutdownHook
import com.squareup.cash.chronicler.player.ext.mount
import com.squareup.cash.chronicler.player.reader.KinesisReaderConfig
import com.squareup.cash.chronicler.player.reader.kinesisReader
import com.squareup.cash.chronicler.player.tracereplayer.TraceMultiplierConfig
import com.squareup.cash.chronicler.player.tracereplayer.delayTraces
import com.squareup.cash.chronicler.player.tracereplayer.mixStatements
import com.squareup.cash.chronicler.player.tracereplayer.multiplyStatements
import com.squareup.cash.chronicler.player.tracereplayer.replayTraces
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.client.hotspot.DefaultExports
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.mysqlclient.MySQLPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.net.InetSocketAddress
import io.prometheus.client.exporter.HTTPServer as PrometheusHttpServer

data class ReplayerConfig(
  val kinesisReaderConfig: KinesisReaderConfig,
  val dbOptions: MySQLConnectOptions,
  val playbackSpeed: Int,
  val traceBufferCapacity: Int,
  val workerCount: Int,
  val fetchRows: Boolean,
  val traceMultiplierConfig: TraceMultiplierConfig,
  val blacklistedQueryPatterns: Set<Regex>
)

fun CoroutineScope.launchReplayer(cfg: ReplayerConfig) = launch {
  val logger = LoggerFactory.getLogger("replayer")
  val metrics = launchMetricsReporter()

  val poolOptions = PoolOptions().also { po ->
    po.maxSize = cfg.workerCount * 3 // Some workers need more than one connection
  }

  val pool: Pool = MySQLPool.pool(cfg.dbOptions, poolOptions)

  kinesisReader(
    config = cfg.kinesisReaderConfig
  )
    .filterNot { q -> cfg.blacklistedQueryPatterns.any { p -> p.matches(q.sql) } }
    .mixStatements(
      metrics = metrics.mount("mixer")
    )
    .multiplyStatements(cfg.traceMultiplierConfig)
    .delayTraces(
      timeFactor = cfg.playbackSpeed,
      metrics = metrics.mount("delayer")
    )
    .replayTraces(
      cp = pool,
      count = cfg.workerCount,
      fetchRows = cfg.fetchRows
    )
    .collectMetrics(
      metrics = metrics,
      cfg = cfg,
      logger = logger
    )
}.installJvmShutdownHook()

private fun CoroutineScope.launchMetricsReporter() = MetricRegistry().also { metrics ->
  PrometheusHttpServer(
    InetSocketAddress(getenv("PROMETHEUS_PORT")?.toInt() ?: 9102),
    CollectorRegistry()
      .also { DefaultExports.register(it) }
      .also { it.register(DropwizardExports(metrics)) }
  ).also { server ->
    launch { awaitCancellation() }.invokeOnCompletion { server.stop() }
  }
}
