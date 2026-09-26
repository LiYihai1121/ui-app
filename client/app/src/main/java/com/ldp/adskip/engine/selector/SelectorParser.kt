package com.ldp.adskip.engine.selector

/**
 * 选择器解析器：字符串 → [SelectorAst]；任何非法输入返回 null，绝不抛异常（fail-safe）。
 *
 * 文法权威：docs/planning/DESIGN-PHASE1-SELECTOR.md §3。两处已记录的处理偏差（见实现 PR）：
 *  - 组合符两侧空白可选（接受文法 `WS combinator WS?` 的超集，`[a]>[b]` 亦合法）；
 *  - [AttrKey.CLICK] 仅允许 `=` 与存在性两种形态（值域仅 `true`/`false`），其余运算符解析期拒绝。
 */
object SelectorParser {

    /** 单条表达式长度上限（trim 后；与服务端 MAX_SELECTOR_LEN 对齐） */
    const val MAX_LENGTH = 256

    /** compound 数上限（组合符链深度；DESIGN-PHASE1 §3.3） */
    const val MAX_COMPOUNDS = 4

    /** value 长度上限（DESIGN-PHASE1 §3.1/§3.3） */
    const val MAX_VALUE_LENGTH = 64

    /** 解析入口。输入非法（含 null/空/超限/语法错误）一律返回 null。 */
    fun parse(input: String?): SelectorAst? {
        val s = input?.trim() ?: return null
        if (s.isEmpty() || s.length > MAX_LENGTH) return null
        val scanner = Scanner(s)
        val compounds = ArrayList<CompoundSelector>(4)
        val combinators = ArrayList<Combinator>(3)

        compounds.add(scanner.parseCompound() ?: return null)
        while (true) {
            if (scanner.atEnd()) break
            val hadWs = scanner.skipWs()
            if (scanner.atEnd()) break
            val combinator = when {
                scanner.peek() == '>' -> { scanner.skipOne(); scanner.skipWs(); Combinator.CHILD }
                scanner.peek() == '+' -> { scanner.skipOne(); scanner.skipWs(); Combinator.PREV_SIBLING }
                hadWs -> Combinator.DESCENDANT
                else -> return null // 紧邻垃圾字符（如 `[text]x`）
            }
            if (compounds.size >= MAX_COMPOUNDS) return null
            combinators.add(combinator)
            compounds.add(scanner.parseCompound() ?: return null)
        }
        return SelectorAst(compounds, combinators)
    }

    /** 手写扫描器：全程边界检查，无任何抛出路径。 */
    private class Scanner(private val s: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= s.length
        fun peek(): Char = s[pos]
        fun skipOne() { pos++ }

        /** 跳过空白；返回是否消耗过空白 */
        fun skipWs(): Boolean {
            var any = false
            while (!atEnd() && s[pos].isWhitespace()) {
                pos++
                any = true
            }
            return any
        }

        /** 解析 compound：一个以上连续的 `[...]`；无起始 `[` 或内部非法返回 null。 */
        fun parseCompound(): CompoundSelector? {
            val simples = ArrayList<SimpleSelector>(4)
            while (!atEnd() && s[pos] == '[') {
                simples.add(parseSimple() ?: return null)
            }
            if (simples.isEmpty()) return null
            return CompoundSelector(simples)
        }

        private fun parseSimple(): SimpleSelector? {
            skipOne() // 吃掉 '['
            val key = parseKey() ?: return null
            if (atEnd()) return null
            if (s[pos] == ']') {
                skipOne()
                return SimpleSelector(AttrMatcher(key, MatchOp.EXISTS))
            }
            val op = parseOp() ?: return null
            // click 值域收紧：仅允许 `=`（DESIGN §3.1：值仅 "true"/"false"）
            if (key == AttrKey.CLICK && op != MatchOp.EQ) return null
            if (atEnd() || s[pos] != '"') return null
            skipOne()
            val start = pos
            while (!atEnd() && s[pos] != '"') skipOne()
            if (atEnd()) return null // 引号未闭合
            val value = s.substring(start, pos)
            skipOne() // 吃掉收尾引号
            if (value.length > MAX_VALUE_LENGTH) return null
            if (atEnd() || s[pos] != ']') return null
            skipOne()
            if (key == AttrKey.CLICK &&
                !value.equals("true", ignoreCase = true) &&
                !value.equals("false", ignoreCase = true)
            ) return null
            return SimpleSelector(AttrMatcher(key, op, value))
        }

        private fun parseKey(): AttrKey? = when {
            s.startsWith("text", pos) -> { pos += 4; AttrKey.TEXT }
            s.startsWith("desc", pos) -> { pos += 4; AttrKey.DESC }
            s.startsWith("vid", pos) -> { pos += 3; AttrKey.VID }
            s.startsWith("click", pos) -> { pos += 5; AttrKey.CLICK }
            else -> null
        }

        private fun parseOp(): MatchOp? {
            if (atEnd()) return null
            return when (val c = s[pos]) {
                '*', '^', '$' -> {
                    if (pos + 1 >= s.length || s[pos + 1] != '=') return null
                    pos += 2
                    when (c) {
                        '*' -> MatchOp.CONTAINS
                        '^' -> MatchOp.PREFIX
                        else -> MatchOp.SUFFIX
                    }
                }
                '=' -> { skipOne(); MatchOp.EQ }
                else -> null
            }
        }
    }
}
