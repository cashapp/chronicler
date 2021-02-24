package com.squareup.cash.chronicler

import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Determines if the given query should be sampled.
 * Function should be deterministic and stable.
 *
 * For request to be sampled - both `shouldSample(CallContext)` and `shouldSample(sql)` have to return true.
 */
interface SamplingRule {
  fun shouldSample(ctx: ChroniclerInterceptor.CallContext): Boolean
  fun shouldSample(sql: String): Boolean
}

@JvmName("ctxSamplingRule")
inline fun SamplingRule(crossinline proc: (ctx: ChroniclerInterceptor.CallContext) -> Boolean) = object : SamplingRule {
  override fun shouldSample(ctx: ChroniclerInterceptor.CallContext) = proc(ctx)
  override fun shouldSample(sql: String) = true
}

@JvmName("sqlSamplingRule")
inline fun SamplingRule(crossinline proc: (sql: String) -> Boolean) = object : SamplingRule {
  override fun shouldSample(ctx: ChroniclerInterceptor.CallContext) = true
  override fun shouldSample(sql: String) = proc(sql)
}

object SamplingRules {
  fun traceRatio(ratio: Double) = SamplingRule { ctx: ChroniclerInterceptor.CallContext ->
    ratio > 0.0 && Random(ctx.traceId.hashCode()).nextDouble() < ratio
  }

  @JvmOverloads
  fun sqlDenylist(deny: Regex, allow: Regex? = null) = sqlDenylist(deny.toPattern(), allow?.toPattern())

  @JvmOverloads
  fun sqlDenylist(deny: Pattern, allow: Pattern? = null) = sqlRule {
    (allow?.matcher(it)?.find() == true) ||
      !deny.matcher(it).find()
  }

  fun sqlRule(predicate: (String) -> Boolean) = SamplingRule { sql: String -> predicate(sql) }

  fun all() = object : SamplingRule {
    override fun shouldSample(ctx: ChroniclerInterceptor.CallContext) = true
    override fun shouldSample(sql: String) = true
  }

  fun and(vararg rules: SamplingRule) = object : SamplingRule {
    override fun shouldSample(ctx: ChroniclerInterceptor.CallContext) =
      rules.all { it.shouldSample(ctx) }

    override fun shouldSample(sql: String) =
      rules.all { it.shouldSample(sql) }
  }
}
