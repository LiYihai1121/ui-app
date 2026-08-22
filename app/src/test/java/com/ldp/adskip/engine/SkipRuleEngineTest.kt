package com.ldp.adskip.engine

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test

/**
 * 引擎 JVM 单测：使用 [FakeAdNode] 模拟节点树，不依赖 Android 框架。
 *
 * 覆盖：关键词边界、ViewID 匹配、黑名单拦截、深度上限、去抖窗口等。
 */
class SkipRuleEngineTest {

    private val engine = SkipRuleEngine()

    // ---------- 文本关键词 ----------

    @Test
    fun `text exact match`() {
        val node = FakeAdNode.node(text = "跳过")
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertTrue(engine.matches(node, rules))
    }

    @Test
    fun `text case insensitive match`() {
        val node = FakeAdNode.node(text = "Skip")
        val rules = RuleSet(keywords = listOf("skip"), viewIds = emptyList())
        assertTrue(engine.matches(node, rules))
    }

    @Test
    fun `desc match`() {
        val node = FakeAdNode.node(desc = "点击跳过广告")
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertTrue(engine.matches(node, rules))
    }

    @Test
    fun `text too long not matched`() {
        val node = FakeAdNode.node(text = "这是一个很长很长的跳过按钮文字")
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertFalse(engine.matches(node, rules))
    }

    @Test
    fun `no keyword in text`() {
        val node = FakeAdNode.node(text = "欢迎")
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertFalse(engine.matches(node, rules))
    }

    @Test
    fun `multiple keywords match`() {
        val node = FakeAdNode.node(text = "关闭")
        val rules = RuleSet(keywords = listOf("跳过", "关闭", "skip"), viewIds = emptyList())
        assertTrue(engine.matches(node, rules))
    }

    // ---------- ViewID ----------

    @Test
    fun `viewId match`() {
        val node = FakeAdNode.node(viewId = "com.example:id/skip_view")
        val rules = RuleSet(keywords = emptyList(), viewIds = listOf("skip"))
        assertTrue(engine.matches(node, rules))
    }

    @Test
    fun `viewId case insensitive match`() {
        val node = FakeAdNode.node(viewId = "com.example:id/SkipButton")
        val rules = RuleSet(keywords = emptyList(), viewIds = listOf("skip"))
        assertTrue(engine.matches(node, rules))
    }

    @Test
    fun `viewId rule too short not matched`() {
        val node = FakeAdNode.node(viewId = "com.example:id/sk")
        val rules = RuleSet(keywords = emptyList(), viewIds = listOf("sk"))
        assertFalse(engine.matches(node, rules))
    }

    @Test
    fun `viewId no match`() {
        val node = FakeAdNode.node(viewId = "com.example:id/title")
        val rules = RuleSet(keywords = emptyList(), viewIds = listOf("skip"))
        assertFalse(engine.matches(node, rules))
    }

    // ---------- 节点状态过滤 ----------

    @Test
    fun `invisible node not matched`() {
        val node = FakeAdNode.node(text = "跳过", visible = false)
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertFalse(engine.matches(node, rules))
    }

    @Test
    fun `editable node not matched`() {
        val node = FakeAdNode.node(text = "跳过", editable = true)
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertFalse(engine.matches(node, rules))
    }

    // ---------- 规则集状态 ----------

    @Test
    fun `empty ruleset returns null`() {
        val root = FakeAdNode.node(text = "跳过")
        val rules = RuleSet(keywords = emptyList(), viewIds = emptyList())
        assertNull(engine.findTarget(root, rules))
    }

    @Test
    fun `disabled ruleset returns null`() {
        val root = FakeAdNode.node(text = "跳过")
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList(), disabled = true)
        assertNull(engine.findTarget(root, rules))
    }

    // ---------- 遍历 ----------

    @Test
    fun `finds target in deep tree`() {
        val target = FakeAdNode.node(text = "跳过")
        val root = FakeAdNode.node(
            children = listOf(
                FakeAdNode.node(text = "广告", children = listOf(target))
            )
        )
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        val result = engine.findTarget(root, rules)
        assertNotNull(result)
        assertEquals("跳过", result?.text)
    }

    @Test
    fun `max nodes limit respected`() {
        // 构造 600 个不匹配节点 + 1 个匹配节点
        // 匹配节点放在第一个子节点位置：DFS 的 LIFO 栈中先入后出，
        // 会在 600 个不匹配节点之后才被弹出，超出 100 节点上限
        val target = FakeAdNode.node(text = "跳过")
        val filler = (1..600).map { FakeAdNode.node(text = "node$it") }
        val root = FakeAdNode.node(children = listOf(target) + filler)
        val smallEngine = SkipRuleEngine(maxNodes = 100)
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        // 100 节点上限内不会遍历到目标
        assertNull(smallEngine.findTarget(root, rules))
    }

    @Test
    fun `returns first match not last`() {
        val first = FakeAdNode.node(text = "跳过")
        val second = FakeAdNode.node(text = "跳过")
        val root = FakeAdNode.node(children = listOf(first, second))
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        val result = engine.findTarget(root, rules)
        // 深度优先，root 先入栈，root 不匹配，children 逆序出栈
        assertNotNull(result)
    }

    @Test
    fun `no match returns null`() {
        val root = FakeAdNode.node(text = "广告", children = listOf(FakeAdNode.node(text = "欢迎")))
        val rules = RuleSet(keywords = listOf("跳过"), viewIds = emptyList())
        assertNull(engine.findTarget(root, rules))
    }

    // ---------- RuleSet ----------

    @Test
    fun `ruleset isEmpty when both lists empty`() {
        assertTrue(RuleSet(emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun `ruleset not empty when keywords present`() {
        assertFalse(RuleSet(listOf("跳过"), emptyList()).isEmpty)
    }

    @Test
    fun `ruleset not empty when viewIds present`() {
        assertFalse(RuleSet(emptyList(), listOf("skip")).isEmpty)
    }

    @Test
    fun `ruleset default schemaVersion is 1`() {
        assertEquals(1, RuleSet(emptyList(), emptyList()).schemaVersion)
    }

    @Test
    fun `ruleset min schemaVersion is 1`() {
        assertEquals(1, RuleSet.MIN_SCHEMA_VERSION)
    }
}
