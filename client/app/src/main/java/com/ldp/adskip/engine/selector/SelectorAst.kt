package com.ldp.adskip.engine.selector

import com.ldp.adskip.engine.AdNode
import java.util.Locale

/**
 * 选择器 AST（纯数据结构；文法见 docs/DESIGN-PHASE1-SELECTOR.md §3）。
 *
 * ```text
 * selector := compound (WS combinator WS? compound)*
 * compound := simple+                 ; 相邻 simple 为逻辑与（AND）
 * simple   := '[' attr ']'            ; 单属性断言
 * ```
 */
enum class Combinator { DESCENDANT, CHILD, PREV_SIBLING }

/** 选择器属性键：text（文本）/ desc（contentDescription）/ vid（viewIdResourceName）/ click（是否可点击）。 */
enum class AttrKey { TEXT, DESC, VID, CLICK }

/** 匹配运算符；[EXISTS] 对应简写 `[key]`（存在性判断）。 */
enum class MatchOp { EQ, CONTAINS, PREFIX, SUFFIX, EXISTS }

/**
 * 单属性断言。
 *
 * 所有字符串比较忽略大小写（与现有关键词通道行为一致）。
 *
 * @param value [MatchOp.EXISTS] 时为 null；其余为引号内原值
 */
data class AttrMatcher(
    val key: AttrKey,
    val op: MatchOp,
    val value: String? = null
) {
    /** 预小写值：匹配热路径零次转换 */
    val valueLower: String? = value?.lowercase(Locale.ROOT)
}

/** 一个 `[…]` 断言。 */
data class SimpleSelector(val attr: AttrMatcher)

/**
 * 一个 compound：内部多个断言为逻辑与（AND）。
 *
 * [evalOrder] 在构造期按求值成本排序（click → vid → text → desc，等值先于包含类），
 * 供匹配器短路求值，热路径零排序（DESIGN-PHASE1 §5.2）。
 */
data class CompoundSelector(val simples: List<SimpleSelector>) {
    val evalOrder: List<SimpleSelector> = simples.sortedWith(
        compareBy(
            { costOf(it.attr.key) },
            { if (it.attr.op == MatchOp.EQ || it.attr.op == MatchOp.EXISTS) 0 else 1 }
        )
    )

    private fun costOf(key: AttrKey): Int = when (key) {
        AttrKey.CLICK -> 0
        AttrKey.VID -> 1
        AttrKey.TEXT -> 2
        AttrKey.DESC -> 3
    }
}

/**
 * 完整选择器：[compounds] 从左到右，[combinators] 连接相邻 compound，
 * 即 `combinators[i]` 介于 `compounds[i]` 与 `compounds[i + 1]` 之间
 * （故 `combinators.size == compounds.size - 1`）。
 *
 * 只能经 [SelectorParser.parse] 构造（解析失败返回 null，绝不抛异常）。
 */
data class SelectorAst(
    val compounds: List<CompoundSelector>,
    val combinators: List<Combinator>
) {
    init {
        require(compounds.isNotEmpty()) { "selector must contain at least one compound" }
        require(combinators.size == compounds.size - 1) { "combinators must join adjacent compounds" }
    }
}
