package app.cash.chronicler.recorder.kpl.canonical

/**
 * Dynamic (update-able) configuration object de/serialized from/to JSON.
 */
data class DynamicChroniclerConfig(
  /**
   * Sampling rate (from 0.0 to 1.0) of requests.
   */
  val sampleRate: Double,
  /**
   * When true - KPL will aggregate multiple user-messages into a custom KPL envelope.
   *
   * *Warning:* Can not be dynamically updated - requires a Franklin restart.
   */
  val kplAggregateMessages: Boolean,
  /**
   * Max records allowed to be outstanding in the KPL buffer. Any new records above this threshold
   * will be dropped.
   */
  val kplMaxOutstandingRecords: Int,
  /**
   * Regex pattern to use to filter *out* queries. Any query that *matches* this pattern will *not*
   * be recorded.
   */
  val denylistedQueryRegex: String
)
