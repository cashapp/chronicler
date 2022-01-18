package app.cash.chronicler.player

import app.cash.chronicler.player.ext.div
import app.cash.chronicler.player.ext.isPositive
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.temporal.Temporal

/**
 * Immutable, thread-safe clock which supports specifying initial instant and timeFactor
 * (in relation to real-world clock)
 */
class RelativisticClock(
  private val startingInstant: Instant,
  private val timeFactor: Int
) {
  private val startingRealWorldInstant = Instant.now()

  val instant: Instant
    get() {
      val realTimePassed = Duration.between(startingRealWorldInstant, Instant.now())
      val fastTimePassed = realTimePassed.multipliedBy(timeFactor.toLong())
      return startingInstant.plus(fastTimePassed)
    }

  private fun RelativeDuration(relative: Duration) =
    RelativeDuration(
      relative = relative,
      real = relative / timeFactor
    )

  private fun RelativeDuration(startInclusive: Temporal, endExclusive: Temporal) =
    RelativeDuration(Duration.between(startInclusive, endExclusive))

  /**
   * Returns actual time that the function was suspended. For example, with negative durations result will be 0.
   */
  private suspend fun RelativeDuration.delay() =
    if (real.isPositive)
      this.also { delay(real.toMillis()) }
    else
      RelativeDuration(Duration.ZERO)

  /**
   * Returns actual time that the function was suspended. For example, with negative durations result will be 0.
   */
  suspend fun delayDuration(relativeDuration: Duration) = RelativeDuration(relativeDuration).delay()

  /**
   * Returns actual time that the function was suspended. For example, with negative durations result will be 0.
   */
  suspend fun delayUntil(relativeTarget: Instant) = RelativeDuration(instant, relativeTarget).delay()
}

data class RelativeDuration(
  val relative: Duration,
  val real: Duration
)
