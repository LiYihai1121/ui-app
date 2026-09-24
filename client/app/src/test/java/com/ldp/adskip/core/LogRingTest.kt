package com.ldp.adskip.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LogRing JVM 单测：环形容量、导出格式与清空。
 */
class LogRingTest {

    @Before
    fun reset() {
        LogRing.clear()
    }

    @Test
    fun `export contains level tag and message`() {
        LogRing.d("Tag", "hello world")
        LogRing.w("Tag", "warn msg")
        LogRing.e("Tag", "err msg")
        val out = LogRing.export()
        assertTrue(out.contains("D/Tag: hello world"))
        assertTrue(out.contains("W/Tag: warn msg"))
        assertTrue(out.contains("E/Tag: err msg"))
        assertEquals(3, out.trim().lines().size)
    }

    @Test
    fun `capacity capped at 500 dropping oldest`() {
        for (i in 1..600) LogRing.d("T", "e" + i.toString().padStart(4, '0'))
        val lines = LogRing.export().trim().lines()
        assertEquals(500, lines.size)
        assertFalse(lines.first().contains("e0001")) // 最旧的被挤出
        assertTrue(lines.first().contains("e0101")) // 保留 101..600
        assertTrue(lines.last().contains("e0600"))
    }

    @Test
    fun `clear empties ring`() {
        LogRing.d("T", "x")
        LogRing.clear()
        assertEquals("", LogRing.export())
    }

    @Test
    fun `export line format is timestamp level tag message`() {
        LogRing.d("T", "m")
        val first = LogRing.export().lineSequence().first()
        assertTrue(
            Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} D/T: m$""").matches(first)
        )
    }
}