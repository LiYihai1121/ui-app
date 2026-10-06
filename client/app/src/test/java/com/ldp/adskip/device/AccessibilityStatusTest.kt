package com.ldp.adskip.device

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AccessibilityStatus.decide] 的真值表。
 *
 * 这个函数是整个「服务到底开没开」的唯一判定点，因此把每种输入组合都钉死。
 * 重点是两条容易被写错的边界：
 * 1. **系统真值优先于进程信号**——反过来会让「用户在系统设置里关掉服务，UI 仍显示运行中」
 *    的缺陷原样复现；
 * 2. **查询失败不得谎报为 OFF**——已经开启服务的用户会被反复引导去设置页而状态永不变。
 */
class AccessibilityStatusTest {

    @Test
    fun `system truth wins over a stale process signal`() {
        assertEquals(AccessibilityStatus.OFF, AccessibilityStatus.decide(systemEnabled = false, processSignal = true))
        assertEquals(AccessibilityStatus.ON, AccessibilityStatus.decide(systemEnabled = true, processSignal = false))
    }

    @Test
    fun `process signal is used only when the system query is unavailable`() {
        assertEquals(AccessibilityStatus.ON, AccessibilityStatus.decide(systemEnabled = null, processSignal = true))
        assertEquals(AccessibilityStatus.OFF, AccessibilityStatus.decide(systemEnabled = null, processSignal = false))
    }

    @Test
    fun `no evidence at all is UNKNOWN rather than OFF`() {
        assertEquals(
            AccessibilityStatus.UNKNOWN,
            AccessibilityStatus.decide(systemEnabled = null, processSignal = null),
        )
    }

    @Test
    fun `agreement between both sources keeps the same answer`() {
        assertEquals(AccessibilityStatus.ON, AccessibilityStatus.decide(systemEnabled = true, processSignal = true))
        assertEquals(AccessibilityStatus.OFF, AccessibilityStatus.decide(systemEnabled = false, processSignal = false))
    }
}
