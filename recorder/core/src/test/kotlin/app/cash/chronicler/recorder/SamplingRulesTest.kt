package app.cash.chronicler.recorder

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SamplingRulesTest {
  @Test
  fun traceRatio() {
    val rule = SamplingRules.traceRatio(0.5)

    val traces = (1..1000)
      .map { traceNum -> traceNum.toString(32) }
      .map { traceId -> ChroniclerInterceptor.CallContext(traceId, threadId = "main") }

    val results1 = traces.associateWith { rule.shouldSample(it) }
    val results2 = traces.associateWith { rule.shouldSample(it) }

    assertEquals(results1, results2, "traceRatio rule is not stable!")

    val countTrue = results1.count { (_, v) -> v }
    val countTotal = results1.count()
    assertTrue("traceRatio rule distribution looks bad: $countTrue/$countTotal are true vs expected ~1/2") {
      countTrue.approximatelyEqual(countTotal / 2, 0.1f)
    }
  }

  @Test
  fun sqlBlacklist() {
    val rule = SamplingRules.sqlDenylist("^SET ".toRegex())

    assertFalse(rule.shouldSample("SET A=B"))
    assertTrue(rule.shouldSample("SELECT * FROM tbl"))
  }

  @Test
  fun sqlRule() {
    val rule = SamplingRule { sql: String -> sql.startsWith("SELECT ") }

    assertFalse(rule.shouldSample("SET A=B"))
    assertTrue(rule.shouldSample("SELECT * FROM tbl"))
  }

  @Test
  fun and() {
    val rule = with(SamplingRules) {
      and(
        SamplingRule { sql: String -> sql.startsWith("SELECT ") },
        SamplingRule { sql: String -> sql.endsWith("LIMIT 1000") }
      )
    }

    assertFalse(rule.shouldSample("SET A=B LIMIT 1000"))
    assertFalse(rule.shouldSample("SELECT * FROM tbl"))
    assertTrue(rule.shouldSample("SELECT * FROM tbl LIMIT 1000"))
  }

  private fun Int.approximatelyEqual(other: Int, tolerance: Float) =
    this.toFloat().approximatelyEqual(other.toFloat(), tolerance)

  private fun Float.approximatelyEqual(other: Float, tolerance: Float) =
    abs(this - other) < tolerance * abs(this + other)
}
