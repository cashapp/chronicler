package com.squareup.cash.chronicler

import squareup.chronicler.Statement

interface StatementSink {
  fun accept(stmt: Statement)
}

inline fun StatementSink(crossinline proc: (Statement) -> Unit) = object : StatementSink {
  override fun accept(stmt: Statement) = proc(stmt)
}

object StatementSinks {
  fun debugStringBridge(delegate: (String) -> Unit) = StatementSink { statement ->
    delegate(statement.toString())
  }

  fun debugStdout() = debugStringBridge(::println)

  fun noop() = StatementSink {}
}
