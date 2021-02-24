package com.squareup.cash.chronicler

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ChroniclerConfigRepositoryTest {
  @Test
  fun subscribe() {
    val cfg = mockk<ChroniclerInterceptor.Config>()

    var receivedCfg: ChroniclerInterceptor.Config? = null
    ChroniclerConfigRepository.subsribe("test") { receivedCfg = it }
    ChroniclerConfigRepository["test"] = cfg
    assertEquals(cfg, receivedCfg)

    var receivedCfg2: ChroniclerInterceptor.Config? = null
    ChroniclerConfigRepository["test2"] = cfg
    ChroniclerConfigRepository.subsribe("test2") { receivedCfg2 = it }
    assertEquals(cfg, receivedCfg2)
  }
}
