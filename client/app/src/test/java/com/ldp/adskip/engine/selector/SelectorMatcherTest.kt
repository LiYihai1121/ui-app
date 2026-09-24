package com.ldp.adskip.engine.selector

import com.ldp.adskip.engine.FakeAdNode
import org.junit.Assert.*
import org.junit.Test

/**
 * 匹配器 JVM 单测：运算符 × 组合符正反例 + 边界（parent 为 null、兄弟越界、超长 text）。
 *
 * 前置守卫（isVisible / !isEditable）属引擎通道职责，不在本类覆盖——见 SkipRuleEngineTest。
 */
class SelectorMatcherTest {

    private fun sel(expr: String) =
        requireNotNull(SelectorParser.parse(expr)) { "invalid test expr: $expr" }

    // ---------- 运算符：text / desc / vid / click ----------

    @Test
    fun `eq matches ignoring case`() {
        val node = FakeAdNode.node(text = "Skip")
        assertTrue(SelectorMatcher.matches(sel("[text=\"skip\"]"), node))
    }

    @Test
    fun `eq rejects different value`() {
        val node = FakeAdNode.node(text = "关闭")
        assertFalse(SelectorMatcher.matches(sel("[text=\"跳过\"]"), node))
    }

    @Test
    fun `contains matches substring`() {
        val node = FakeAdNode.node(text = "点击跳过广告")
        assertTrue(SelectorMatcher.matches(sel("[text*=\"跳过\"]"), node))
    }

    @Test
    fun `contains rejects absence`() {
        val node = FakeAdNode.node(text = "欢迎")
        assertFalse(SelectorMatcher.matches(sel("[text*=\"跳过\"]"), node))
    }

    @Test
    fun `prefix matches`() {
        val node = FakeAdNode.node(text = "跳过广告")
        assertTrue(SelectorMatcher.matches(sel("[text^=\"跳过\"]"), node))
    }

    @Test
    fun `prefix rejects when value is suffix only`() {
        val node = FakeAdNode.node(text = "广告跳过")
        assertFalse(SelectorMatcher.matches(sel("[text^=\"跳过\"]"), node))
    }

    @Test
    fun `suffix matches`() {
        val node = FakeAdNode.node(text = "广告跳过")
        assertTrue(SelectorMatcher.matches(sel("[text$=\"跳过\"]"), node))
    }

    @Test
    fun `desc contains case insensitive`() {
        val node = FakeAdNode.node(desc = "Tap to Skip")
        assertTrue(SelectorMatcher.matches(sel("[desc*=\"tap to\"]"), node))
    }

    @Test
    fun `vid suffix matches`() {
        val node = FakeAdNode.node(viewId = "com.example:id/skip_view")
        assertTrue(SelectorMatcher.matches(sel("[vid$=\":id/skip_view\"]"), node))
    }

    @Test
    fun `vid null rejects value op`() {
        val node = FakeAdNode.node()
        assertFalse(SelectorMatcher.matches(sel("[vid$=\":id/skip_view\"]"), node))
    }

    @Test
    fun `click true matches`() {
        val node = FakeAdNode.node(clickable = true)
        assertTrue(SelectorMatcher.matches(sel("[click=\"true\"]"), node))
    }

    @Test
    fun `click false matches on non clickable`() {
        val node = FakeAdNode.node(clickable = false)
        assertTrue(SelectorMatcher.matches(sel("[click=\"false\"]"), node))
    }

    @Test
    fun `click existence rejects non clickable`() {
        val node = FakeAdNode.node(clickable = false)
        assertFalse(SelectorMatcher.matches(sel("[click]"), node))
    }

    @Test
    fun `compound and requires both halves`() {
        val node = FakeAdNode.node(text = "跳过", clickable = true)
        assertTrue(SelectorMatcher.matches(sel("[text*=\"跳过\"][click=\"true\"]"), node))
    }

    @Test
    fun `compound and rejects missing half`() {
        val node = FakeAdNode.node(text = "跳过", clickable = false)
        assertFalse(SelectorMatcher.matches(sel("[text*=\"跳过\"][click=\"true\"]"), node))
    }

    // ---------- 长度边界 ----------

    @Test
    fun `text over limit not matched`() {
        val text = "a".repeat(70) + "跳过"
        val node = FakeAdNode.node(text = text)
        assertFalse(SelectorMatcher.matches(sel("[text*=\"跳过\"]"), node))
    }

    @Test
    fun `text at limit matched`() {
        val text = "a".repeat(SelectorParser.MAX_VALUE_LENGTH - 2) + "跳过"
        assertEquals(SelectorParser.MAX_VALUE_LENGTH, text.length)
        val node = FakeAdNode.node(text = text)
        assertTrue(SelectorMatcher.matches(sel("[text*=\"跳过\"]"), node))
    }

    @Test
    fun `existence rejects null and blank text`() {
        val selector = sel("[text]")
        assertFalse(SelectorMatcher.matches(selector, FakeAdNode.node()))
        assertFalse(SelectorMatcher.matches(selector, FakeAdNode.node(text = "   ")))
    }

    @Test
    fun `existence accepts non blank text`() {
        assertTrue(SelectorMatcher.matches(sel("[text]"), FakeAdNode.node(text = "跳过")))
    }

    // ---------- 组合符：> 直接子节点 ----------

    @Test
    fun `child combinator matches direct child`() {
        val child = FakeAdNode.node(clickable = true)
        val root = FakeAdNode.node(text = "跳过", children = listOf(child))
        assertTrue(SelectorMatcher.matches(sel("[text*=\"跳过\"] > [click]"), child))
        assertEquals(root, child.parent)
    }

    @Test
    fun `child combinator rejects grandchild`() {
        val leaf = FakeAdNode.node(clickable = true)
        val mid = FakeAdNode.node(children = listOf(leaf))
        FakeAdNode.node(text = "跳过", children = listOf(mid))
        assertFalse(SelectorMatcher.matches(sel("[text*=\"跳过\"] > [click]"), leaf))
    }

    @Test
    fun `child combinator fails without parent`() {
        val root = FakeAdNode.node(text = "跳过", clickable = true)
        assertNull(root.parent)
        assertFalse(SelectorMatcher.matches(sel("[text*=\"跳过\"] > [click]"), root))
    }

    // ---------- 组合符：空格 后代 ----------

    @Test
    fun `descendant matches grandchild`() {
        val leaf = FakeAdNode.node(clickable = true)
        val mid = FakeAdNode.node(children = listOf(leaf))
        FakeAdNode.node(text = "跳过", children = listOf(mid))
        assertTrue(SelectorMatcher.matches(sel("[text*=\"跳过\"] [click]"), leaf))
    }

    @Test
    fun `descendant rejects unrelated branch`() {
        val target = FakeAdNode.node(clickable = true)
        val anchor = FakeAdNode.node(text = "跳过")
        FakeAdNode.node(children = listOf(anchor, target))
        assertFalse(SelectorMatcher.matches(sel("[text*=\"跳过\"] [click]"), target))
    }

    @Test
    fun `descendant fails on parentless root`() {
        val root = FakeAdNode.node(clickable = true)
        assertFalse(SelectorMatcher.matches(sel("[text] [click]"), root))
    }

    // ---------- 组合符：+ 相邻前一个兄弟 ----------

    @Test
    fun `prev sibling matches preceding node`() {
        val target = FakeAdNode.node(viewId = "com.example:id/iv_close")
        val anchor = FakeAdNode.node(desc = "跳过广告")
        FakeAdNode.node(children = listOf(anchor, target))
        assertTrue(
            SelectorMatcher.matches(sel("[desc^=\"跳过\"] + [vid$=\"id/iv_close\"]"), target)
        )
    }

    @Test
    fun `prev sibling rejects first child`() {
        val only = FakeAdNode.node(clickable = true)
        FakeAdNode.node(children = listOf(only))
        assertFalse(SelectorMatcher.matches(sel("[desc^=\"跳过\"] + [click]"), only))
    }

    // ---------- 链式与文档示例 ----------

    @Test
    fun `four compound chain matches`() {
        val d = FakeAdNode.node(clickable = true)
        val c = FakeAdNode.node(viewId = "com.example:id/c")
        val b = FakeAdNode.node(desc = "bbbb", children = listOf(c, d))
        FakeAdNode.node(text = "aaaa", children = listOf(b))
        assertTrue(
            SelectorMatcher.matches(sel("[text*=\"a\"] [desc*=\"b\"] > [vid$=\"c\"] + [click]"), d)
        )
    }

    @Test
    fun `design example skip container fallback`() {
        // DESIGN §3.2 主力示例：跳过容器内任意可点击节点
        val target = FakeAdNode.node(clickable = true)
        val mid = FakeAdNode.node(children = listOf(target))
        val container = FakeAdNode.node(desc = "跳过广告", children = listOf(mid))
        val selector = sel("[desc*=\"跳过\"] [click=\"true\"]")
        assertTrue(SelectorMatcher.matches(selector, target))
        assertFalse(SelectorMatcher.matches(selector, mid))
        assertFalse(SelectorMatcher.matches(selector, container))
    }

    // ---------- fail-safe 与职责边界 ----------

    @Test
    fun `null node returns false`() {
        assertFalse(SelectorMatcher.matches(sel("[click]"), null))
    }

    @Test
    fun `matcher does not apply visibility guard`() {
        // 可见性/可编辑守卫是引擎通道前置职责（DESIGN §4.2），匹配器只做语义匹配
        val node = FakeAdNode.node(text = "跳过", visible = false)
        assertTrue(SelectorMatcher.matches(sel("[text*=\"跳过\"]"), node))
    }
}