package com.squareup.cash.chronicler.player.ext

import java.util.Queue

// NOT thread safe.
inline fun <T> Queue<T>.pollIf(predicate: (T) -> Boolean) =
  peek()?.takeIf(predicate)?.let { poll() }

// NOT thread safe.
inline fun <T> Queue<T>.pollWhile(crossinline predicate: (T) -> Boolean) =
  generateSequence {
    pollIf(predicate)
  }
