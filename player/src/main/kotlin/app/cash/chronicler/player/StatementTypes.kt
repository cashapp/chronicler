package app.cash.chronicler.player

import app.cash.chronicler.proto.Statement
import org.slf4j.LoggerFactory

enum class StatementType {
  QUERY, INSERT, UPDATE, DELETE, SET, TX, UNKNOWN
}

private val logger = LoggerFactory.getLogger("statementType")

val Statement.type: StatementType
  get() {
    val operation = sql.replace("^/\\*.*?\\*/".toRegex(RegexOption.DOT_MATCHES_ALL), "")
      .trimStart()
      .substringBefore(" ")
      .lowercase()

    return when (operation) {
      "select" -> StatementType.QUERY
      "insert" -> StatementType.INSERT
      "delete", "truncate" -> StatementType.DELETE
      "update" -> StatementType.UPDATE
      "set" -> StatementType.SET
      "commit", "rollback" -> StatementType.TX
      else ->
        StatementType.UNKNOWN
          .also { logger.error("Failed to parse type of query: $sql") }
    }
  }
