package com.ldp.adskip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本展示格式单测。
 *
 * 这条断言来自一次真实的线上可见缺陷：字符串模板里的 `$` 被转义后，
 * `"$`{info.versionName} ($code)"` 不会求值，**编译与测试全绿**，
 * 界面上却显示 `${info.versionName} (10)` 字面量。只有断言真实输出才能拦住它。
 */
class SettingsRepositoryVersionTest {

    @Test
    fun `version is rendered as name and code`() {
        assertEquals("3.1 (10)", SettingsRepository.formatVersion("3.1", 10L))
    }

    @Test
    fun `template placeholders are not left unexpanded`() {
        val out = SettingsRepository.formatVersion("3.1", 10L)
        assertFalse("输出残留未求值的模板：$out", out.contains("\${"))
        assertFalse("输出残留反引号：$out", out.contains("`"))
        assertTrue("输出应包含真实版本名：$out", out.contains("3.1"))
    }

    @Test
    fun `null version name falls back to placeholder`() {
        assertEquals("? (1)", SettingsRepository.formatVersion(null, 1L))
    }
}
