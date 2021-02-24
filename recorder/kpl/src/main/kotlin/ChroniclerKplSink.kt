package com.squareup.cash.chronicler.kpl

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.kinesis.producer.KinesisProducer
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration
import com.amazonaws.services.kinesis.producer.UserRecord
import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.squareup.cash.chronicler.StatementSink
import squareup.chronicler.Statement
import java.io.Closeable

@Suppress("unused", "MemberVisibilityCanBePrivate")
class ChroniclerKplSink(
  private val config: Config
) : StatementSink, Closeable {

  /**
   * Configuration for chronicler kpl sink.
   */
  data class Config(
    /**
     * When `false` - the sink will be disabled, never starting (or shutting down) KPL sidecar.
     */
    val enabled: Boolean = false,

    /**
     * AWS Endpoint to use for KDS.
     */
    val endpoint: String,

    /**
     * AWS Region.
     */
    val region: String,

    /**
     * AWS Credential provider.
     */
    val credentialsProvider: AWSCredentialsProvider,

    /**
     * Stream to use for publishing recorded messages.
     */
    val streamName: String,

    /**
     * When true - KPL will aggregate multiple user-messages into a single custom KPL envelope.
     */
    val kplAggregateMessages: Boolean = false,

    /**
     * Max records allowed to be outstanding in the KPL buffer. Any new records above this threshold
     * will be dropped.
     */
    val kplMaxOutstandingRecords: Int = 10000
  )

  /**
   * Metrics that the chronicler kpl sink is exposing.
   * TODO: Bridge from internal KPL metrics and this class?
   */
  inner class Metrics : MetricRegistry() {
    val droppedRecords: Counter = counter("droppedRecords")
    val droppedRecordsSizeTooLarge: Counter = counter("droppedRecordsSizeTooLarge")

    init {
      gauge("outstandingRecords") { Gauge { producer.outstandingRecordsCount } }
    }
  }

  val metrics = Metrics()

  private val producer: KinesisProducer = KinesisProducer(
    KinesisProducerConfiguration().apply {
      kinesisEndpoint = config.endpoint
      region = config.region
      credentialsProvider = config.credentialsProvider
      isAggregationEnabled = config.kplAggregateMessages
      threadingModel = KinesisProducerConfiguration.ThreadingModel.POOLED
      metricsLevel = "none" // Disabling metrics as we do not have cloudwatch VPC endpoint
    }
  )

  private var closed: Boolean = false

  override fun accept(stmt: Statement) {
    if (closed || producer.outstandingRecordsCount > config.kplMaxOutstandingRecords) {
      metrics.droppedRecords.inc()
    } else {
      val byteBuffer = stmt.encodeByteString().asByteBuffer()
      if (byteBuffer.remaining() > 1048576) {
        metrics.droppedRecordsSizeTooLarge.inc()
      } else {
        producer.addUserRecord(
          UserRecord().apply {
            streamName = config.streamName
            partitionKey = stmt.trace_id
            data = byteBuffer
          }
        )
      }
    }
  }

  override fun close() = synchronized(this) {
    if (!closed) {
      closed = true
      producer.flushSync()
      producer.destroy()
    }
  }
}
