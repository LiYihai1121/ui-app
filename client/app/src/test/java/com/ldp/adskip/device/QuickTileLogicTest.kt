package com.ldp.adskip.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快捷磁贴交互契约的 JVM 单测。
 *
 * `TileService` 本身要靠系统绑定才能跑，无法在 JVM 中执行；
 * 但「运行中点一下 = 关闭，未运行点一下 = 去开启」是产品承诺，必须可回归。
 */
class QuickTileLogicTest {

    @Test
    fun `tap while running turns the service off`() {
        assertEquals(QuickTileAction.DISABLE_SERVICE, QuickTileLogic.decide(true))
    }

    @Test
    fun `tap while stopped guides the user to accessibility settings`() {
        assertEquals(
            QuickTileAction.OPEN_ACCESSIBILITY_SETTINGS,
            QuickTileLogic.decide(false),
        )
    }

    @Test
    fun `model mirrors state and action from the same decision`() {
        val running = QuickTileLogic.model(true)
        assertTrue(running.enabled)
        assertEquals(QuickTileAction.DISABLE_SERVICE, running.action)

        val stopped = QuickTileLogic.model(false)
        assertFalse(stopped.enabled)
        assertEquals(QuickTileAction.OPEN_ACCESSIBILITY_SETTINGS, stopped.action)
    }

    @Test
    fun `every state has exactly one action and every action is covered`() {
        val actions = listOf(true, false).map { QuickTileLogic.decide(it) }.toSet()
        assertEquals(QuickTileAction.entries.toSet(), actions)
    }

    @Test
    fun `tile add results are exhaustive`() {
        // UI 层按枚举穷举映射文案，缺少分支会在编译期暴露；这里固化「不得新增未映射状态」
        assertEquals(4, TileAddResult.entries.size)
    }
}
