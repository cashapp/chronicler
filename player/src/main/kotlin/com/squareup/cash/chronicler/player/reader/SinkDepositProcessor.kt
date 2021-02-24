package com.squareup.cash.chronicler.player.reader

import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.squareup.cash.chronicler.player.ext.decode
import com.squareup.cash.chronicler.player.ext.inc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.kinesis.lifecycle.events.InitializationInput
import software.amazon.kinesis.lifecycle.events.LeaseLostInput
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput
import software.amazon.kinesis.lifecycle.events.ShardEndedInput
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput
import software.amazon.kinesis.processor.ShardRecordProcessor
import squareup.chronicler.Statement

internal class SinkDepositProcessor(
  private val sink: SendChannel<Statement>,
  private val checkpointRule: CheckpointRule
) : ShardRecordProcessor {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(SinkDepositProcessor::class.java)
  }

  override fun initialize(input: InitializationInput) {
    logger.info("Initializing @ Sequence: ${input.extendedSequenceNumber()} @ shard: ${MDCVars.shardId}")
  }

  override fun leaseLost(input: LeaseLostInput) {
    logger.info("Lease lost for shard: ${MDCVars.shardId}")
  }

  override fun shardEnded(input: ShardEndedInput) {
    logger.info("Reached end of shard: ${MDCVars.shardId}")
    input.checkpointer().checkpoint()
  }

  override fun shutdownRequested(input: ShutdownRequestedInput) {
    logger.info("Shutdown requested")
    input.checkpointer().checkpoint()
  }

  override fun processRecords(input: ProcessRecordsInput) = runBlocking(Dispatchers.Unconfined) {
    input.records()
      .map {
        Statement.ADAPTER.decode(it.data())
      }
      .also { metrics.recordsRead.inc(it.count()) }
      .forEach { sink.send(it) }

    checkpointRule.onRecordsProcessed(input)
  }

  inner class Metrics : MetricRegistry() {
    val recordsRead: Counter = counter("records-read")
  }

  val metrics = Metrics()
}
