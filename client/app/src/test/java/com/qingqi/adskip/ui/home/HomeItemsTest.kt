package com.qingqi.adskip.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [HomeItems] 守护：滚动锚点不再依赖魔法下标。
 *
 * 背景：`animateScrollToItem` 只接受下标，旧实现把 `index = 4` 硬编码在闭包里。
 * LazyColumn 上方增删任何 item 都会让「添加关键词」入口静默滚错位置——不报错、
 * 不崩溃，只是滚到别的东西。现在 item key 与滚动目标同源于 [HomeItems.order]。
 *
 * 本测试两部分：
 * 1. 纯映射断言（下标与旧硬编码值一致、key 互异、未知 key 返回 -1）；
 * 2. 源码扫描契约——HomeScreen.kt 中 `item(key = HomeItems.X)` 的出现顺序
 *    必须等于 [HomeItems.order] 声明序，且全部 item 都必须带 key（漏 key 的
 *    item 不参与 order 映射，正是本次修复要消灭的静默失效路径）。
 */
class HomeItemsTest {

    @Test
    fun `keywords index matches the historical hardcoded anchor`() {
        // 4 = 旧实现 animateScrollToItem(index = 4) 的值；改这里说明首页结构变了，
        // 需要人工确认滚动的仍是关键词卡
        assertEquals(4, HomeItems.indexOf(HomeItems.KEYWORDS))
    }

    @Test
    fun `all item keys are distinct`() {
        assertEquals(HomeItems.order.size, HomeItems.order.toSet().size)
    }

    @Test
    fun `unknown key resolves to minus one`() {
        assertEquals(-1, HomeItems.indexOf("home-does-not-exist"))
    }

    @Test
    fun `home screen items appear in declared order and all carry keys`() {
        val source = homeScreenSource()
        // 源码里写的是字段名（HomeItems.KEYWORDS），反射取出其值（"home-keywords"）
        val declared = Regex("""item\(key = HomeItems\.(\w+)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()
        val actualKeys = declared.map { fieldName ->
            HomeItems::class.java.getDeclaredField(fieldName).get(null)
        }
        assertEquals(
            "HomeScreen.kt 中 item(key = HomeItems.X) 的顺序必须等于 HomeItems.order 声明序",
            HomeItems.order,
            actualKeys,
        )

        // 未带 key 的 item 不参与 order 映射：插到关键词卡之前就会让滚动锚点静默失效
        val keylessItems = Regex("""\bitem\s*\{""").findAll(source).count()
        assertTrue(
            "首页 LazyColumn 还有 $keylessItems 个未带 key 的 item（形如 `item {`），" +
                "必须改用 item(key = HomeItems.X) 并在 HomeItems.order 登记",
            keylessItems == 0,
        )
    }

    private fun homeScreenSource(): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "client/app/src/main/java/com/qingqi/adskip/ui/home/HomeScreen.kt")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("未定位到 HomeScreen.kt（从 user.dir 向上查找失败）")
    }
}
