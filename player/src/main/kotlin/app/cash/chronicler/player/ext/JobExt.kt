package app.cash.chronicler.player.ext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

fun Job.installJvmShutdownHook() {
  Runtime.getRuntime().addShutdownHook(
    thread(start = false) {
      runBlocking(Dispatchers.Unconfined) {
        LoggerFactory.getLogger("jvm-shutdown-hook").info("Shutting down application...")
        this@installJvmShutdownHook.cancelAndJoin()
      }
    }
  )
}
