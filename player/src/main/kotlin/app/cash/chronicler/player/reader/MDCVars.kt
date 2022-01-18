package app.cash.chronicler.player.reader

import org.slf4j.MDC

object MDCVars {
  var shardId: String?
    get() = MDC.get("ShardId")
    set(value) = value?.let { MDC.put("ShardId", value) } ?: MDC.remove("ShardId")
}
