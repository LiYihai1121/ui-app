package com.ldp.adskip.engine

import java.util.Locale

/**
 * 跳过规则引擎（纯匹配逻辑）。
 *
 * v2.2 改造：依赖 [AdNode] 抽象而非 [android.view.accessibility.AccessibilityNodeInfo]，
 * 使引擎可跑 JVM 单测（注入 [FakeAdNode] 即可）。
 *
 * 职责单一：给定节点树与规则集，找出应点击的目标节点。
 * 与「如何点击」（AccessibilityService 动作/手势）完全解耦。
 *
 * 匹配通道：
 *  1. 文本 —— text / contentDescription 包含关键词，限短文本防误点
 *  2. ViewID —— 控件资源 ID 包含规则串，覆盖纯图片按钮
 */
class SkipRuleEngine(
    private val maxNodes: Int = 500,
    private val maxTextLen: Int = 12,
    private val minViewIdLen: Int = 3
) {

    /** 深度优先遍历，返回第一个命中规则的目标节点；未命中返回 null。 */
    fun findTarget(root: AdNode, rules: RuleSet): AdNode? {
        if (rules.isEmpty || rules.disabled) return null
        val stack = ArrayDeque<AdNode>()
        stack.addLast(root)
        var steps = 0
        while (stack.isNotEmpty() && steps < maxNodes) {
            val node = stack.removeLast()
            steps++
            if (matches(node, rules)) return node
            for (child in node.children()) {
                stack.addLast(child)
            }
        }
        return null
    }

    /** 判断单个节点是否命中规则。 */
    fun matches(node: AdNode, rules: RuleSet): Boolean {
        if (!node.isVisible) return false
        if (node.isEditable) return false

        // 通道一：文本 / 内容描述
        val text = node.text?.trim()
        val desc = node.desc?.trim()
        for (candidate in listOf(text, desc)) {
            if (candidate.isNullOrEmpty() || candidate.length > maxTextLen) continue
            val lower = candidate.lowercase(Locale.ROOT)
            for (kw in rules.keywords) {
                if (candidate.contains(kw) || lower.contains(kw.lowercase(Locale.ROOT))) return true
            }
        }

        // 通道二：ViewID（如 com.example:id/skip_view 命中 "skip"）
        val id = node.viewId
        if (id != null) {
            val lower = id.lowercase(Locale.ROOT)
            for (rule in rules.viewIds) {
                if (rule.length >= minViewIdLen && lower.contains(rule.lowercase(Locale.ROOT))) return true
            }
        }
        return false
    }
}
