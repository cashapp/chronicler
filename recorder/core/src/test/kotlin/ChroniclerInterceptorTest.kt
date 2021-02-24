package com.squareup.cash.chronicler

import com.mysql.cj.jdbc.ConnectionImpl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import squareup.chronicler.Statement
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset.UTC
import kotlin.test.assertEquals
import kotlin.text.RegexOption.IGNORE_CASE

private fun <T> connect(proc: (conn: Connection) -> T): T =
  DriverManager.getConnection(
    "jdbc:mysql://localhost:3306/mysql?queryInterceptors=com.squareup.cash.chronicler.ChroniclerInterceptor&chroniclerConfigName=main",
    "root", ""
  )
    .use(proc)

private fun Connection.executeSimple(sql: String) {
  createStatement().use { stmt ->
    stmt.executeQuery(sql).close()
  }
}

private fun Connection.executePrepared(sql: String, setter: PreparedStatement.() -> Unit = { }) {
  prepareStatement(sql).use { stmt ->
    stmt.setter()
    stmt.executeQuery()
      .close()
  }
}

val frozenInstant = Instant.ofEpochMilli(100)
val statementCapture = mutableListOf<Statement>()

fun assertCapturedSql(vararg expectedSql: String) {
  assertEquals(
    expectedSql.toList(),
    statementCapture.map { it.sql.lowercase() }
  )
}

private object Config : ChroniclerInterceptor.Config {
  override val clock: Clock = Clock.fixed(frozenInstant, UTC)

  override fun buildCallContext() = ChroniclerInterceptor.CallContext(
    "trace-id",
    "thread-id"
  )

  override val samplingRule = SamplingRules.sqlDenylist(
    deny = "^SET ".toRegex(IGNORE_CASE),
    allow = "^SET autocommit=".toRegex(IGNORE_CASE)
  )

  override val statementSink = object : StatementSink {
    override fun accept(stmt: Statement) {
      statementCapture.add(stmt)
    }
  }
}

class ChroniclerInterceptorTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun setupClass() {
      ChroniclerConfigRepository["main"] = Config
    }
  }

  @BeforeEach
  fun setupTest() {
    statementCapture.clear()
  }

  @Test
  fun `fields properly propagated for prepared statement`() {
    var expectedConnectionId: Long? = null
    connect { c ->
      require(c is ConnectionImpl)
      expectedConnectionId = c.session.threadId

      c.executePrepared("SELECT ?") {
        setInt(1, 1)
      }
    }

    assertCapturedSql(
      "set autocommit=1",
      "select 1"
    )

    assertEquals(
      Statement.Builder().apply {
        trace_id = "trace-id"
        thread_id = "thread-id"
        connection_id = expectedConnectionId!!.toString(32)
        success = true
        rows_affected = -1
        client_start = frozenInstant
        client_end = frozenInstant
        sql = "SELECT 1"
      }.build(),
      statementCapture[1]
    )
  }

  @Test
  fun `fields properly propagated for regular statement`() {
    var expectedConnectionId: Long? = null
    connect { c ->
      require(c is ConnectionImpl)
      expectedConnectionId = c.session.threadId
      c.executeSimple("SELECT 1")
    }

    assertCapturedSql(
      "set autocommit=1",
      "select 1"
    )

    assertEquals(
      Statement.Builder().apply {
        trace_id = "trace-id"
        thread_id = "thread-id"
        connection_id = expectedConnectionId!!.toString(32)
        success = true
        rows_affected = -1
        client_start = frozenInstant
        client_end = frozenInstant
        sql = "SELECT 1"
      }.build(),
      statementCapture[1]
    )
  }

  @Test
  fun `transaction boundaries captured`() {
    connect { c ->
      c.autoCommit = false
      c.executeSimple("BEGIN")
      c.executeSimple("SELECT 1")
      c.commit()
    }

    assertCapturedSql(
      "set autocommit=1",
      "set autocommit=0",
      "begin",
      "select 1",
      "commit",
      "rollback"
    )
  }
}
