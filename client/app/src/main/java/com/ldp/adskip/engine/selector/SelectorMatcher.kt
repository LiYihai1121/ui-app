package com.ldp.adskip.engine.selector

import com.ldp.adskip.engine.AdNode
import java.util.Locale

/**
 * 选择器求值：右到左递归（CSS 标准方向），最右 compound 先测，逐级向左验证组合关系。
 *
 * fail-safe：任一环节解析不到（parent / previousSibling 为 null）即匹配失败（不点击），
 * 绝不抛异常。递归深度 ≤ [SelectorParser.MAX_COMPOUNDS] 个 compound，天然有界。
 */
object SelectorMatcher {

    /** [node] 为 null（根节点无父、兄弟越界等）时返回 false。 */
    fun matches(selector: SelectorAst, node: AdNode?): Boolean {
        if (node == null) return false
        return matchAt(selector, selector.compounds.lastIndex, node)
    }

    private fun matchAt(sel: SelectorAst, i: Int, node: AdNode?): Boolean {
        if (node == null) return false
        if (!matchCompound(sel.compounds[i], node)) return false
        if (i == 0) return true
        return when (sel.combinators[i - 1]) {
            Combinator.DESCENDANT -> {
                var ancestor = node.parent
                while (ancestor != null) {
                    if (matchAt(sel, i - 1, ancestor)) return true
                    ancestor = ancestor.parent
                }
                false
            }
            Combinator.CHILD -> matchAt(sel, i - 1, node.parent)
            Combinator.PREV_SIBLING -> matchAt(sel, i - 1, node.previousSibling())
        }
    }

    private fun matchCompound(compound: CompoundSelector, node: AdNode): Boolean {
        // evalOrder 构造期已按成本排序，逐个短路（click/vid 先于 text*=，DESIGN §5.2）
        for (simple in compound.evalOrder) {
            if (!matchAttr(simple.attr, node)) return false
        }
        return true
    }

    private fun matchAttr(attr: AttrMatcher, node: AdNode): Boolean {
        val raw: String? = when (attr.key) {
            AttrKey.CLICK -> if (node.isClickable) "true" else "false"
            AttrKey.TEXT -> node.text
            AttrKey.DESC -> node.desc
            AttrKey.VID -> node.viewId
        }

        // 存在性简写：click ≡ isClickable；text/desc ≡ 非空白；vid ≡ 非空
        if (attr.op == MatchOp.EXISTS) {
            return when (attr.key) {
                AttrKey.CLICK -> node.isClickable
                AttrKey.TEXT, AttrKey.DESC -> !raw.isNullOrBlank()
                AttrKey.VID -> !raw.isNullOrEmpty()
            }
        }

        val effective = raw?.trim() ?: return false // 属性缺席：值运算永不匹配（fail-safe）
        // text/desc 超长直接不匹配，防扫大段文本（DESIGN §3.3）
        if ((attr.key == AttrKey.TEXT || attr.key == AttrKey.DESC) &&
            effective.length > SelectorParser.MAX_VALUE_LENGTH
        ) {
            return false
        }
        val expected = attr.valueLower ?: return false // 解析期保证非 null；防御不抛
        val actual = effective.lowercase(Locale.ROOT)
        return when (attr.op) {
            MatchOp.EQ -> actual == expected
            MatchOp.CONTAINS -> actual.contains(expected)
            MatchOp.PREFIX -> actual.startsWith(expected)
            MatchOp.SUFFIX -> actual.endsWith(expected)
            MatchOp.EXISTS -> false // 不可达：EXISTS 已在上方分支返回
        }
    }
}
