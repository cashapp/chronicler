package app.cash.chronicler.player.reader

import app.cash.chronicler.proto.Statement
import com.codahale.metrics.MetricRegistry
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.kinesis.checkpoint.CheckpointConfig
import software.amazon.kinesis.common.InitialPositionInStreamExtended
import software.amazon.kinesis.common.KinesisClientUtil
import software.amazon.kinesis.coordinator.CoordinatorConfig
import software.amazon.kinesis.coordinator.Scheduler
import software.amazon.kinesis.leases.LeaseManagementConfig
import software.amazon.kinesis.lifecycle.LifecycleConfig
import software.amazon.kinesis.metrics.MetricsConfig
import software.amazon.kinesis.metrics.MetricsLevel
import software.amazon.kinesis.processor.ProcessorConfig
import software.amazon.kinesis.retrieval.DataFetchingStrategy
import software.amazon.kinesis.retrieval.RetrievalConfig
import software.amazon.kinesis.retrieval.polling.PollingConfig
import java.util.concurrent.Future

private val logger = LoggerFactory.getLogger("kinesis-reader")

fun kinesisReader(
  config: KinesisReaderConfig
): Flow<Statement> = channelFlow {
  val reader = KinesisReader(config, this)
  reader.start()
  try {
    awaitCancellation()
  } finally {
    if (!this.channel.isClosedForSend) {
      this.channel.close()
    }
    reader.shutdown()
  }
}

data class KinesisReaderConfig(
  val kinesisConfig: KinesisConfig,
  val dynamoConfig: DynamoDbConfig,
  val cloudwatchConfig: CloudwatchConfig,
  val applicationName: String,
  val workerIdentifier: String,
  val checkpointEveryRecords: Long,
  val initialPositionInStream: InitialPositionInStreamExtended
)

data class KinesisConfig(
  val awsCredentialsProvider: AwsCredentialsProvider = DefaultCredentialsProvider.create(),
  val region: Region,
  val streamName: String
)

data class DynamoDbConfig(
  val awsCredentialsProvider: AwsCredentialsProvider = DefaultCredentialsProvider.create(),
  val region: Region,
  val tableName: String
)

data class CloudwatchConfig(
  val awsCredentialsProvider: AwsCredentialsProvider = DefaultCredentialsProvider.create(),
  val region: Region
)

private class KinesisReader(
  private val config: KinesisReaderConfig,
  private val sink: SendChannel<Statement>
) {
  private val scheduler: Scheduler

  private var thread: Thread? = null

  val metrics = MetricRegistry()

  init {
    val kinesisClient = KinesisClientUtil.createKinesisAsyncClient(
      KinesisAsyncClient.builder()
        .region(config.kinesisConfig.region)
        .credentialsProvider(
          config.kinesisConfig.awsCredentialsProvider
        )
    )

    val dynamoClient = DynamoDbAsyncClient.builder()
      .region(config.dynamoConfig.region)
      .credentialsProvider(
        config.dynamoConfig.awsCredentialsProvider
      )
      .build()

    val cloudwatchClient = CloudWatchAsyncClient.builder()
      .region(config.cloudwatchConfig.region)
      .credentialsProvider(
        config.cloudwatchConfig.awsCredentialsProvider
      )
      .build()

    this.scheduler = Scheduler(
      CheckpointConfig(),
      CoordinatorConfig(config.applicationName),
      LeaseManagementConfig(
        config.dynamoConfig.tableName,
        dynamoClient,
        kinesisClient, config.kinesisConfig.streamName, config.workerIdentifier
      ),
      LifecycleConfig().apply {
        taskExecutionListener(ShardIdMdcEnricher())
      },
      MetricsConfig(cloudwatchClient, config.applicationName)
        .apply { metricsLevel(MetricsLevel.NONE) }, // Effectively disable CW metrics
      ProcessorConfig {
        SinkDepositProcessor(
          sink = sink,
          checkpointRule = CountingCheckpointRule(config.checkpointEveryRecords)
        ).also {
          metrics.register("processors.${it.hashCode()}", it.metrics)
        }
      },
      RetrievalConfig(kinesisClient, config.kinesisConfig.streamName, config.applicationName)
        .retrievalSpecificConfig(
          PollingConfig(config.kinesisConfig.streamName, kinesisClient).apply {
            recordsFetcherFactory().dataFetchingStrategy(DataFetchingStrategy.PREFETCH_CACHED)
            idleTimeBetweenReadsInMillis(200L) // Max is 5 per sec + buffer
          }
        ).apply {
          initialPositionInStreamExtended(config.initialPositionInStream)
        }
    )
  }

  fun start() {
    require(thread == null) { "Attempted to start KinesisReader twice!" }

    thread = Thread(scheduler).also { it.start() }
  }

  fun shutdown(): Future<Boolean>? {
    logger.info("Kinesis Reader shutdown initiated")
    return require(thread != null) { "Attempted to stop KinesisReader that has not been started yet!" }
      .let {
        scheduler.startGracefulShutdown()
      }
  }
}
