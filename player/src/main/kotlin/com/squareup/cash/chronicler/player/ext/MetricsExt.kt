package com.squareup.cash.chronicler.player.ext

import com.codahale.metrics.Counter
import com.codahale.metrics.Meter
import com.codahale.metrics.MetricRegistry
import kotlin.reflect.full.createInstance

fun Counter.inc(n: Int) = inc(n.toLong())
fun Counter.dec(n: Int) = dec(n.toLong())
fun Meter.mark(n: Int) = mark(n.toLong())

inline fun <reified T : MetricRegistry> MetricRegistry.mount(namespace: String): T =
  T::class.createInstance().also { register(namespace, it) }
