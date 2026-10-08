package com.ldp.adskip.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 取点规则坐标换算（纯函数）单测：
 * 取点（悬浮层触摸）与点击（手势派发）两条链路共用同一换算，
 * 存算不一致会「点偏」，故钉死。
 */
class PointRulesTest {

    @Test
    fun `scale maps screen ratios to pixels`() {
        val (x, y) = PointRules.scale(0.5f, 0.25f, 1080, 2400)
        assertEquals(540f, x, 0.01f)
        assertEquals(600f, y, 0.01f)
    }

    @Test
    fun `scale clamps out-of-range ratios`() {
        val (x, y) = PointRules.scale(1.5f, -0.2f, 100, 100)
        assertEquals(100f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `scale keeps corners at screen corners`() {
        val (tlx, tly) = PointRules.scale(0f, 0f, 720, 1280)
        val (brx, bry) = PointRules.scale(1f, 1f, 720, 1280)
        assertEquals(0f, tlx, 0.01f)
        assertEquals(0f, tly, 0.01f)
        assertEquals(720f, brx, 0.01f)
        assertEquals(1280f, bry, 0.01f)
    }
}
