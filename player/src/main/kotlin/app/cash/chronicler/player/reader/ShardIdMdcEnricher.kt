package app.cash.chronicler.player.reader

import software.amazon.kinesis.lifecycle.TaskExecutionListener
import software.amazon.kinesis.lifecycle.events.TaskExecutionListenerInput

/**
 * Listener for KCL adding ShardId into MDC context.
 */
class ShardIdMdcEnricher : TaskExecutionListener {
  override fun beforeTaskExecution(input: TaskExecutionListenerInput) {
    MDCVars.shardId = input.shardInfo().shardId()
  }

  override fun afterTaskExecution(input: TaskExecutionListenerInput) {
    MDCVars.shardId = null
  }
}
