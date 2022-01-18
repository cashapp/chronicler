package app.cash.chronicler.player.ext

import java.time.Duration
import java.time.Instant

fun Instant.max(other: Instant) = this.takeIf { it > other } ?: other
val Duration.isPositive get() = this > Duration.ZERO
operator fun Duration.div(num: Int): Duration = this.dividedBy(num.toLong())
