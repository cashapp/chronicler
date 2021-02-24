package com.squareup.cash.chronicler.player

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.squareup.cash.chronicler.player.ext.parseFancyRegex
import com.squareup.cash.chronicler.player.miskds.TargetMiskDatasourceOptions
import com.squareup.cash.chronicler.player.reader.CloudwatchConfig
import com.squareup.cash.chronicler.player.reader.DynamoDbConfig
import com.squareup.cash.chronicler.player.reader.KinesisConfig
import com.squareup.cash.chronicler.player.reader.KinesisReaderConfig
import com.squareup.cash.chronicler.player.tracereplayer.TraceMultiplierConfig
import io.netty.handler.ssl.OpenSsl
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.kinesis.common.InitialPositionInStream
import software.amazon.kinesis.common.InitialPositionInStreamExtended
import software.amazon.kinesis.common.InitialPositionInStreamExtended.newInitialPosition
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.function.Supplier

private val logger = LoggerFactory.getLogger("main")
private val uncaughtExceptionHandler = CoroutineExceptionHandler { _, e -> logger.error("Uncaught exception", e) }

fun main(args: Array<String>) = Player.main(args)

fun RawOption.awsRegion() =
  choice("us-west-1", "us-west-2", "us-east-1", "us-east-2")
    .convert { Region.of(it) }

object Player : CliktCommand() {
  private val streamRegion: Region
    by option("--stream-region", help = "Stream AWS region id.")
    .awsRegion()
    .required()

  private val streamName
    by option(help = "Name of kinesis data stream to replay messages from.")
    .required()

  private val applicationName
    by option(
    help = "Application name is used to coordinate a single " +
      "cursor between multiple instances of an application."
  )
    .required()

  private val workerIdentifier
    by option(
    help = "Unique worker identifier used mostly for debugging" +
      ". Defaults to a random UUID."
  )
    .defaultLazy { UUID.randomUUID().toString() }

  private val dynamoRegion: Region
    by option("--dynamo-region", help = "DynamoDB AWS region id.")
    .awsRegion()
    .defaultLazy("Same as --stream-region") { streamRegion }

  private val tableName
    by option(help = "Dynamo table to use for lease coordination.")
    .required()

  private val cloudwatchRegion: Region
    by option("--cloudwatch-region", help = "Cloudwatch AWS region id.")
    .awsRegion()
    .defaultLazy("Same as --stream-region") { streamRegion }

  private val checkpointEveryRecords: Long
    by option(
    "--checkpoint-every-records",
    help = "How many user records to process between checkpointing the Kinesis stream. Defaults to 10 000."
  )
    .long()
    .default(10000L)

  private val initialPositionInStream: InitialPositionInStreamExtended
    by option(
    "--initial-position",
    help = "Initial position in the stream. Valid values include 'TRIM_HORIZON', 'LATEST' or a unix timestamp. " +
      "Defaults to 'TRIM_HORIZON'"
  )
    .convert {
      when (it) {
        "TRIM_HORIZON", "LATEST" -> newInitialPosition(InitialPositionInStream.valueOf(it))
        else -> InitialPositionInStreamExtended.newInitialPositionAtTimestamp(Date.from(Instant.parse(it)))
      }
    }
    .default(newInitialPosition(InitialPositionInStream.TRIM_HORIZON))

  private val miskDatasourceOptions: TargetMiskDatasourceOptions
    by TargetMiskDatasourceOptions()

  private val dbOptions get() = miskDatasourceOptions.mySQLConnectOptions

  private val playbackSpeed: Int
    by option("--playback-speed", help = "Factor by which to speed up time during replay.")
    .int().required()

  private val traceBufferCapacity: Int
    by option("--trace-buffer-capacity", help = "Capacity of trace replay buffer.")
    .int().default(10000)

  private val workerCount: Int
    by option(
    "--worker-count",
    help = "Number of parallel replayers to launch. This directly impacts connection pool size."
  )
    .int().default(100)

  private val fetchRows: Boolean
    by option("--fetch-rows", help = "Should replayer fetch all rows when executing queries.")
    .flag("--no-fetch-rows", default = true)

  private val traceMultiplier: Int
    by option("--trace-multiplier", help = "Replay each trace specified number of times.")
    .int()
    .default(1)

  private val traceMultiplierDelay: Duration
    by option(
    "--trace-multiplier-delay",
    help = """
      Delay each copy of the trace by specified duration. Higher delays will result in more memory usage 
      to re-order the statements in-memory. Syntax examples: 5s, 10m, 3h. Default: 30s
    """.trimIndent()
  )
    .convert { Duration.parse("PT$it") }
    .default(Duration.ofSeconds(30))

  private val blacklistedQueryPatterns: Set<Regex>
    by option(
    "--blacklisted-patterns", "-b",
    help = """
      File containing a list of blacklisted regex patterns (one pattern per line). 
      SQL statements matching any of those patterns will not be replayed.
      Patterns have to start with a `/` (ignored) and end with `/` followed by regex modifiers.
    """.trimIndent()
  )
    .convert { fileName ->
      File(fileName).readLines()
        .filterNot(String::isBlank)
        .map { it.trim().parseFancyRegex() }
        .toSet()
    }
    .default(setOf())

  private val kinesisStsAssumeRole by AssumeRoleOptions("kinesis").cooccurring()
  private val dynamoStsAssumeRole by AssumeRoleOptions("dynamo").cooccurring()
  private val cloudwatchStsAssumeRole by AssumeRoleOptions("cloudwatch").cooccurring()

  // Wiring all components
  override fun run() = runBlocking(Dispatchers.Default + uncaughtExceptionHandler) {
    logger.info("Processors: ${Runtime.getRuntime().availableProcessors()}")
    logger.info("OpenSSL Available: ${OpenSsl.isAvailable()}")

    val defaultCredentialsProvider = DefaultCredentialsProvider.create()

    launchReplayer(
      ReplayerConfig(
        kinesisReaderConfig = KinesisReaderConfig(
          kinesisConfig = KinesisConfig(
            region = streamRegion,
            streamName = streamName,
            awsCredentialsProvider = kinesisStsAssumeRole?.toCredentialsProvider(defaultCredentialsProvider)
              ?: defaultCredentialsProvider
          ),
          dynamoConfig = DynamoDbConfig(
            region = dynamoRegion,
            tableName = tableName,
            awsCredentialsProvider = dynamoStsAssumeRole?.toCredentialsProvider(defaultCredentialsProvider)
              ?: defaultCredentialsProvider
          ),
          cloudwatchConfig = CloudwatchConfig(
            region = cloudwatchRegion,
            awsCredentialsProvider = cloudwatchStsAssumeRole?.toCredentialsProvider(defaultCredentialsProvider)
              ?: defaultCredentialsProvider
          ),
          applicationName = applicationName,
          workerIdentifier = workerIdentifier,
          checkpointEveryRecords = checkpointEveryRecords,
          initialPositionInStream = initialPositionInStream
        ),
        dbOptions = dbOptions,
        playbackSpeed = playbackSpeed,
        blacklistedQueryPatterns = blacklistedQueryPatterns,
        traceBufferCapacity = traceBufferCapacity,
        workerCount = workerCount,
        fetchRows = fetchRows,
        traceMultiplierConfig = TraceMultiplierConfig(
          traceMultiplier = traceMultiplier,
          traceMultiplierDelay = traceMultiplierDelay
        )
      )
    )
  }
}

class AssumeRoleOptions(resourceName: String, prefix: String = "$resourceName-") : OptionGroup() {
  val roleArn: String by option("--${prefix}role-arn", help = "Role-arn for access to $resourceName")
    .required()
  val externalId: String by option("--${prefix}external-id", help = "External-id for access to $resourceName")
    .required()
  val roleSessionNamePrefix: String by option("--${prefix}role-session-prefix", help = "Role-session name prefix for access to $resourceName")
    .default("chronicler-player-")
}

private fun AssumeRoleOptions.toCredentialsProvider(parentCredentialsProvider: AwsCredentialsProvider) =
  StsAssumeRoleCredentialsProvider.builder()
    .stsClient(
      StsClient.builder()
        .credentialsProvider(parentCredentialsProvider)
        .build()
    )
    .refreshRequest(
      Supplier {
        AssumeRoleRequest.builder()
          .roleArn(this.roleArn)
          .externalId(this.externalId)
          .roleSessionName(this.roleSessionNamePrefix + System.currentTimeMillis())
          .build()
      }
    )
    .build()
