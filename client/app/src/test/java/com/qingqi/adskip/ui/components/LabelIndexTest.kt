package com.qingqi.adskip.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [LabelIndex] 行为守护（JVM 纯函数测试，不碰 PackageManager）。
 *
 * 背景：旧 resolveAppLabel 对每个未命中缓存的包名都跑一次 queryIntentActivities
 * 全量枚举（真机 100+ 应用），且发生在主线程（rememberAppLabel 在组合期调用）。
 * 新实现全程只查一次、按包名懒加载标签；本测试锁住「索引查找」这半边的行为，
 * Android 查询那半边由 buildLauncherLabelIndex 的降级路径保证（查询失败 → 空索引）。
 */
class LabelIndexTest {

    @Test
    fun `hit returns loaded label`() {
        val index = LabelIndex(mapOf("com.tencent.mm" to { "微信" }))
        assertEquals("微信", index["com.tencent.mm"])
    }

    @Test
    fun `miss returns null so caller falls back`() {
        val index = LabelIndex(mapOf("com.tencent.mm" to { "微信" }))
        assertNull(index["com.unknown.app"])
    }

    @Test
    fun `throwing loader is swallowed and treated as null`() {
        // loadLabel 在真实设备上可能抛资源相关异常；索引不得把异常带给调用方
        val index = LabelIndex(
            mapOf(
                "com.broken.app" to {
                    error("resource not found")
                },
            ),
        )
        assertNull(index["com.broken.app"])
    }

    @Test
    fun `empty index misses everything without crashing`() {
        // 对应查询失败降级路径（getOrElse { emptyMap() }）
        val index = LabelIndex(emptyMap())
        assertNull(index["com.tencent.mm"])
    }

    @Test
    fun `loader runs lazily only on lookup`() {
        var calls = 0
        val index = LabelIndex(
            mapOf(
                "com.example" to {
                    calls++
                    "示例"
                },
            ),
        )
        assertEquals("建索引阶段不得触发任何标签加载", 0, calls)
        index["com.example"]
        assertEquals("取用时才加载", 1, calls)
    }
}
