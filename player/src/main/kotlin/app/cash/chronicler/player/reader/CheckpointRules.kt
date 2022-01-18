package app.cash.chronicler.player.reader

import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput

interface CheckpointRule {
  fun onRecordsProcessed(input: ProcessRecordsInput)
}

class CountingCheckpointRule(
  private val checkpointEvery: Long
) : CheckpointRule {
  private var processedRecords = 0L

  override fun onRecordsProcessed(input: ProcessRecordsInput) =
    onRecordsProcessed(input.records().size, input.checkpointer()::checkpoint)

  private fun onRecordsProcessed(count: Int, checkpoint: () -> Unit) {
    if (processedRecords / checkpointEvery != (processedRecords + count) / checkpointEvery)
      checkpoint()

    processedRecords += count
  }
}
