package com.ldp.adskip.engine.selector

import org.junit.Assert.*
import org.junit.Test

/**
 * 解析器 JVM 单测：合法向量须得到结构正确的 AST，非法向量一律返回 null（不抛异常）。
 *
 * 文法权威：docs/planning/DESIGN-PHASE1-SELECTOR.md §3。
 */
class SelectorParserTest {

    private fun parse(input: String?) = SelectorParser.parse(input)

    // ---------- 合法 ----------

    @Test
    fun `vid suffix example parses`() {
        val ast = parse("[vid$=\":id/skip_view\"]")
        assertNotNull(ast)
        assertEquals(1, ast!!.compounds.size)
        assertTrue(ast.combinators.isEmpty())
        val attr = ast.compounds[0].simples[0].attr
        assertEquals(AttrKey.VID, attr.key)
        assertEquals(MatchOp.SUFFIX, attr.op)
        assertEquals(":id/skip_view", attr.value)
    }

    @Test
    fun `compound and parses`() {
        val ast = parse("[text*=\"跳过\"][click=\"true\"]")
        assertNotNull(ast)
        assertEquals(1, ast!!.compounds.size)
        assertEquals(2, ast.compounds[0].simples.size)
        assertTrue(ast.combinators.isEmpty())
    }

    @Test
    fun `child combinator parses`() {
        val ast = parse("[text*=\"跳过\"] > [vid$=\"id/tv_skip\"]")
        assertNotNull(ast)
        assertEquals(2, ast!!.compounds.size)
        assertEquals(listOf(Combinator.CHILD), ast.combinators)
    }

    @Test
    fun `prev sibling combinator parses`() {
        val ast = parse("[desc^=\"跳过\"] + [vid$=\"id/iv_close\"]")
        assertNotNull(ast)
        assertEquals(listOf(Combinator.PREV_SIBLING), ast!!.combinators)
    }

    @Test
    fun `descendant combinator parses`() {
        val ast = parse("[desc*=\"跳过\"] [click=\"true\"]")
        assertNotNull(ast)
        assertEquals(listOf(Combinator.DESCENDANT), ast!!.combinators)
    }

    @Test
    fun `four compound chain parses`() {
        val ast = parse("[text*=\"a\"] [desc*=\"b\"] > [vid$=\"c\"] + [click]")
        assertNotNull(ast)
        assertEquals(4, ast!!.compounds.size)
        assertEquals(
            listOf(Combinator.DESCENDANT, Combinator.CHILD, Combinator.PREV_SIBLING),
            ast.combinators
        )
    }

    @Test
    fun `exact equals parses`() {
        val ast = parse("[text=\"跳过\"]")
        assertEquals(MatchOp.EQ, ast!!.compounds[0].simples[0].attr.op)
    }

    @Test
    fun `contains prefix suffix ops parse`() {
        assertEquals(
            MatchOp.CONTAINS,
            parse("[text*=\"跳\"]")!!.compounds[0].simples[0].attr.op
        )
        assertEquals(
            MatchOp.PREFIX,
            parse("[text^=\"跳\"]")!!.compounds[0].simples[0].attr.op
        )
        assertEquals(
            MatchOp.SUFFIX,
            parse("[text$=\"跳\"]")!!.compounds[0].simples[0].attr.op
        )
    }

    @Test
    fun `quoted value with spaces parses`() {
        val ast = parse("[desc*=\"tap to skip\"]")
        assertEquals("tap to skip", ast!!.compounds[0].simples[0].attr.value)
    }

    @Test
    fun `existence shorthand parses`() {
        for ((expr, key) in listOf(
            "[click]" to AttrKey.CLICK,
            "[text]" to AttrKey.TEXT,
            "[desc]" to AttrKey.DESC,
            "[vid]" to AttrKey.VID
        )) {
            val ast = parse(expr)
            assertNotNull("should accept $expr", ast)
            val attr = ast!!.compounds[0].simples[0].attr
            assertEquals(key, attr.key)
            assertEquals(MatchOp.EXISTS, attr.op)
            assertNull(attr.value)
        }
    }

    @Test
    fun `click boolean value case insensitive accepted`() {
        assertNotNull(parse("[click=\"TRUE\"]"))
        assertNotNull(parse("[click=\"False\"]"))
    }

    @Test
    fun `whitespace around explicit combinator tolerated`() {
        assertNotNull(parse("[text*=\"跳过\"]   >   [click=\"true\"]"))
        assertNotNull(parse("[text*=\"跳过\"]  +  [click]"))
    }

    @Test
    fun `no whitespace around combinator tolerated`() {
        // 超集接受：组合符两侧空白可选（实现偏差已在解析器 KDoc 记录）
        assertNotNull(parse("[text*=\"跳过\"]>[click=\"true\"]"))
        assertNotNull(parse("[text*=\"跳过\"]+[click=\"true\"]"))
    }

    @Test
    fun `padded input trimmed`() {
        assertNotNull(parse("  \t[text*=\"跳过\"]  \n"))
    }

    @Test
    fun `value boundary 64 accepted`() {
        val value = "a".repeat(SelectorParser.MAX_VALUE_LENGTH)
        assertNotNull(parse("[text=\"$value\"]"))
    }

    @Test
    fun `expression boundary 256 accepted`() {
        val head = "[text]"
        val tail = "[click]"
        val pad = " ".repeat(SelectorParser.MAX_LENGTH - head.length - tail.length)
        val expr = head + pad + tail
        assertEquals(SelectorParser.MAX_LENGTH, expr.length)
        assertNotNull(parse(expr))
    }

    @Test
    fun `design example skip container parses`() {
        assertNotNull(parse("[desc*=\"跳过\"] [click=\"true\"]"))
    }

    // ---------- 非法（一律 null，不抛异常） ----------

    @Test
    fun `null input rejected`() {
        assertNull(parse(null))
    }

    @Test
    fun `empty input rejected`() {
        assertNull(parse(""))
    }

    @Test
    fun `whitespace only input rejected`() {
        assertNull(parse("   \t "))
    }

    @Test
    fun `bare token rejected`() {
        assertNull(parse("skip_view"))
    }

    @Test
    fun `unknown key rejected`() {
        assertNull(parse("[id=\"x\"]"))
    }

    @Test
    fun `uppercase key rejected`() {
        assertNull(parse("[TEXT=\"x\"]"))
    }

    @Test
    fun `unknown operator rejected`() {
        assertNull(parse("[text~=\"x\"]"))
    }

    @Test
    fun `missing closing quote rejected`() {
        assertNull(parse("[text=\"skip]"))
    }

    @Test
    fun `missing closing bracket rejected`() {
        assertNull(parse("[text=\"skip\""))
    }

    @Test
    fun `single quoted value rejected`() {
        assertNull(parse("[text='x']"))
    }

    @Test
    fun `empty brackets rejected`() {
        assertNull(parse("[]"))
    }

    @Test
    fun `five compounds rejected`() {
        assertNull(parse("[text=\"a\"] [text=\"b\"] [text=\"c\"] [text=\"d\"] [text=\"e\"]"))
    }

    @Test
    fun `value over limit rejected`() {
        val value = "a".repeat(SelectorParser.MAX_VALUE_LENGTH + 1)
        assertNull(parse("[text=\"$value\"]"))
    }

    @Test
    fun `expression over limit rejected`() {
        val head = "[text]"
        val tail = "[click]"
        val pad = " ".repeat(SelectorParser.MAX_LENGTH + 1 - head.length - tail.length)
        assertNull(parse(head + pad + tail))
    }

    @Test
    fun `click non boolean value rejected`() {
        assertNull(parse("[click=\"yes\"]"))
    }

    @Test
    fun `click non equality op rejected`() {
        assertNull(parse("[click*=\"rue\"]"))
    }

    @Test
    fun `trailing combinator rejected`() {
        assertNull(parse("[text] >"))
        assertNull(parse("[text] +"))
    }

    @Test
    fun `double combinator rejected`() {
        assertNull(parse("[text] > > [click]"))
    }

    @Test
    fun `adjacent garbage rejected`() {
        assertNull(parse("[text]x"))
    }

    @Test
    fun `garbage after space rejected`() {
        assertNull(parse("[text] x"))
    }

    @Test
    fun `unclosed attr rejected`() {
        assertNull(parse("[text"))
    }

    @Test
    fun `operator without value rejected`() {
        assertNull(parse("[text=]"))
    }
}