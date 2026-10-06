package com.ldp.adskip.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [LanguageMode] 的映射真值表。
 *
 * 重点是「跟随系统」那条：它必须映射为**空** LocaleList 而不是某个具体 locale。
 * 写错的话用户点了「跟随系统」也不会回到系统语言——这是本功能最容易做错的一处，
 * 且症状要等用户切回去才发现。
 */
class LanguageModeTest {

    @Test
    fun `system mode carries no tag so callers can clear the override`() {
        assertNull(LanguageMode.languageTagOrNull(LanguageMode.SYSTEM))
        assertNull(LanguageMode.SYSTEM.tag)
    }

    @Test
    fun `concrete modes carry their own tags`() {
        assertEquals("zh-CN", LanguageMode.languageTagOrNull(LanguageMode.CHINESE))
        assertEquals("zh-CN", LanguageMode.CHINESE.tag)
        assertEquals("en", LanguageMode.languageTagOrNull(LanguageMode.ENGLISH))
        assertEquals("en", LanguageMode.ENGLISH.tag)
    }

    @Test
    fun `fromTag round trips and falls back to system for unknown values`() {
        LanguageMode.entries.forEach { mode ->
            assertEquals(mode, LanguageMode.fromTag(mode.tag))
        }
        // 旧值 / 损坏值不应崩溃，也不该被误判成某种语言。
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTag(null))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTag(""))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTag("fr-FR"))
    }

    @Test
    fun `default is follow system`() {
        assertEquals(LanguageMode.SYSTEM, LanguageMode.DEFAULT)
    }
}
