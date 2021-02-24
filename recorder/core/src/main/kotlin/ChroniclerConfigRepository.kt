package com.squareup.cash.chronicler

typealias ConfigSubscriber = (ChroniclerInterceptor.Config) -> Unit

object ChroniclerConfigRepository {
  private val configs = mutableMapOf<String, ChroniclerInterceptor.Config>()
  private val subscribers = mutableMapOf<String, MutableSet<ConfigSubscriber>>()

  @JvmStatic
  operator fun set(name: String, config: ChroniclerInterceptor.Config) = synchronized(this) {
    if (configs.containsKey(name))
      error("Attempted to set Chronicler Config '$name' twice for the same key. This is not supported.")
    configs[name] = config

    subscribers[name]?.forEach { it(config) }
    subscribers.remove(name)
  }

  fun subsribe(name: String, proc: (ChroniclerInterceptor.Config) -> Unit) = synchronized(this) {
    configs[name]
      ?.let(proc)
      ?: run {
        subscribers.computeIfAbsent(name) { mutableSetOf() }.add(proc)
      }
  }
}
