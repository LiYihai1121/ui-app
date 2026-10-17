package com.qingqi.adskip.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SkeletonList 微光渐变的 dp/px 契约守护。
 *
 * 背景：`Brush.linearGradient` 的 Offset 处于 DrawScope 的**像素**空间，而
 * SkeletonList 的行高是 Dp。旧实现直接把 `rowHeight.value`（dp 数值）当像素用，
 * 渐变只覆盖行顶部那一小段，微光扫过时行底部「不亮」，像渲染残留——纯视觉缺陷，
 * 不崩不报错，只有真机看得见，故用源码扫描锁住。
 */
class SkeletonShimmerContractTest {

    @Test
    fun `shimmer gradient converts row height to pixels`() {
        val source = feedbackSource()
        assertTrue(
            "微光渐变终点必须用 with(LocalDensity.current) { rowHeight.toPx() } 转换后的像素值，" +
                "不得把 dp 数值直接当 Offset 用",
            source.contains("rowHeight.toPx()"),
        )
        assertFalse(
            "不得再把 rowHeight.value（dp 数值）直接用作 Offset 分量",
            Regex("""Offset\([^)]*rowHeight\.value""").containsMatchIn(source),
        )
    }

    private fun feedbackSource(): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "client/app/src/main/java/com/qingqi/adskip/ui/components/Feedback.kt")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("未定位到 Feedback.kt（从 user.dir 向上查找失败）")
    }
}
