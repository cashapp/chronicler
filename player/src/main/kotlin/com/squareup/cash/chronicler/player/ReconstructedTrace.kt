package com.squareup.cash.chronicler.player

import squareup.chronicler.Statement
import java.time.Instant
import java.time.Duration as JavaDuration

class ReconstructedTraceBuilder(firstStatement: Statement) {
  val statements = mutableListOf(firstStatement)

  fun add(statement: Statement) = statements.add(statement)

  val traceStart: Instant get() = statements.first().client_start!!
  val traceId: String get() = statements.first().trace_id

  override fun toString(): String {
    return "ReconstructedTrace(statements=$statements, traceStart=$traceStart, traceId='$traceId')"
  }

  fun build() = ReconstructedTrace(
    statements = statements,
    traceStart = traceStart,
    traceId = traceId
  )
}

data class ReconstructedTrace(
  val statements: List<Statement>,
  val traceStart: Instant,
  val traceId: String
)

fun ReconstructedTrace.delayedCopy(relativeDuration: JavaDuration) = copy(
  statements = statements.map {
    it.copy(
      client_start = it.client_start!!.plus(relativeDuration),
      client_end = it.client_end!!.plus(relativeDuration)
    )
  },
  traceStart = traceStart.plus(relativeDuration)
)
