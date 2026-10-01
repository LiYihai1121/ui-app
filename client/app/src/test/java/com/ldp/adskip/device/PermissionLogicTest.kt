package com.ldp.adskip.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权限状态呈现逻辑的纯 JVM 守护测试。
 *
 * 本测试守护的是**产品契约**，不是实现细节：
 * 「查不到状态」必须与「明确没开启」严格区分。若哪天有人为了简化把
 * [PermissionState.UNKNOWN] 并入 DENIED，下列两条断言会立刻失败——
 * 那正是最容易被改坏、且改坏后果最直接（用户被谎报并反复跳转）的地方。
 */
class PermissionLogicTest {

    // ---------- 三态语义：UNKNOWN 绝不降级 ----------

    @Test
    fun `unknown state is presented as unknown not denied`() {
        val p = PermissionLogic.present(
            key = PermissionKeys.VENDOR_KEEPALIVE,
            state = PermissionState.UNKNOWN,
            supported = true,
            readyText = READY,
            deniedText = DENIED,
            unknownText = UNKNOWN,
            unsupportedText = UNSUPPORTED,
        )
        assertEquals("UNKNOWN 必须显示「无法检测」文案，不得显示「未开启」", UNKNOWN, p.statusText)
        assertFalse("UNKNOWN 不得被当作已就绪", p.ready)
        // 但仍需保留跳转入口：用户可以手动去系统页确认
        assertTrue("UNKNOWN 仍应提供跳转入口以便手动确认", p.actionable)
    }

    @Test
    fun `denied and unknown produce different status text`() {
        val denied = present(PermissionState.DENIED)
        val unknown = present(PermissionState.UNKNOWN)
        assertFalse(
            "DENIED 与 UNKNOWN 的文案必须不同，否则用户被引导去重复操作已完成的设置",
            denied.statusText == unknown.statusText,
        )
    }

    @Test
    fun `granted is ready and actionable`() {
        val p = present(PermissionState.GRANTED)
        assertTrue(p.ready)
        assertEquals(READY, p.statusText)
        assertTrue("已就绪也保留入口，便于用户反查", p.actionable)
    }

    @Test
    fun `denied is not ready but actionable`() {
        val p = present(PermissionState.DENIED)
        assertFalse(p.ready)
        assertEquals(DENIED, p.statusText)
        assertTrue(p.actionable)
    }

    // ---------- 不适用项：不再引导，但不算「未就绪的故障」 ----------

    @Test
    fun `unsupported item is treated as ready and not actionable`() {
        val p = PermissionLogic.present(
            key = PermissionKeys.VENDOR_KEEPALIVE,
            state = PermissionState.UNKNOWN, // 即使状态未知
            supported = false, // 但当前 ROM 无此开关
            readyText = READY,
            deniedText = DENIED,
            unknownText = UNKNOWN,
            unsupportedText = UNSUPPORTED,
        )
        assertEquals(UNSUPPORTED, p.statusText)
        assertFalse("不适用项不该把用户引向一个不存在的设置页", p.actionable)
        // 呈现上视为「无需处理」，故 ready=true，避免汇总文案把它算成待办
        assertTrue("不适用项不应计入「待处理」，否则汇总永远显示有事项未完成", p.ready)
    }

    @Test
    fun `unsupported overrides denied state`() {
        // supported=false 时，无论探测到什么状态都走「不适用」分支
        val p = PermissionLogic.present(
            key = PermissionKeys.VENDOR_KEEPALIVE,
            state = PermissionState.DENIED,
            supported = false,
            readyText = READY,
            deniedText = DENIED,
            unknownText = UNKNOWN,
            unsupportedText = UNSUPPORTED,
        )
        assertEquals(UNSUPPORTED, p.statusText)
    }

    // ---------- 汇总口径 ----------

    @Test
    fun `ready count includes only granted`() {
        val items = listOf(
            PermissionItem("a", PermissionState.GRANTED),
            PermissionItem("b", PermissionState.GRANTED),
            PermissionItem("c", PermissionState.DENIED),
            PermissionItem("d", PermissionState.UNKNOWN),
        )
        assertEquals(2, PermissionLogic.readyCount(items))
    }

    @Test
    fun `unknown counts against ready but still raises attention`() {
        val items = listOf(
            PermissionItem("a", PermissionState.GRANTED),
            PermissionItem("b", PermissionState.UNKNOWN),
        )
        assertEquals(
            "UNKNOWN 不得计入已就绪：查不到状态时不能向用户保证「已受保护」",
            1,
            PermissionLogic.readyCount(items),
        )
        assertTrue("UNKNOWN 应提示用户仍有事项待确认", PermissionLogic.needsAttention(items))
    }

    @Test
    fun `all granted needs no attention`() {
        val items = listOf(
            PermissionItem("a", PermissionState.GRANTED),
            PermissionItem("b", PermissionState.GRANTED),
        )
        assertFalse(PermissionLogic.needsAttention(items))
        assertEquals(2, PermissionLogic.readyCount(items))
    }

    @Test
    fun `empty list is handled without crash`() {
        assertEquals(0, PermissionLogic.readyCount(emptyList()))
        assertFalse(PermissionLogic.needsAttention(emptyList()))
    }

    private fun present(state: PermissionState) = PermissionLogic.present(
        key = PermissionKeys.ACCESSIBILITY,
        state = state,
        supported = true,
        readyText = READY,
        deniedText = DENIED,
        unknownText = UNKNOWN,
        unsupportedText = UNSUPPORTED,
    )

    private companion object {
        const val READY = "已开启"
        const val DENIED = "未开启"
        const val UNKNOWN = "无法检测，请手动确认"
        const val UNSUPPORTED = "当前系统无需设置"
    }
}
