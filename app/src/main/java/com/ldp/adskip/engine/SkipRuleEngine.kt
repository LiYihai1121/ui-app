package com.ldp.adskip.engine

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 跳过规则引擎（纯匹配逻辑）。
 *
 * 职责单一：给定节点树与规则集，找出应点击的目标节点。
 * 与「如何点击」（AccessibilityService 动作/手势）完全解耦，
 * 便于单测与后续替换匹配策略（如截屏取点规则）。
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
    fun findTarget(root: AccessibilityNodeInfo, rules: RuleSet): AccessibilityNodeInfo? {
        if (rules.isEmpty || rules.disabled) return null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var steps = 0
        while (stack.isNotEmpty() && steps < maxNodes) {
            val node = stack.removeLast()
            steps++
            if (matches(node, rules)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }

    /** 判断单个节点是否命中规则。 */
    fun matches(node: AccessibilityNodeInfo, rules: RuleSet): Boolean {
        if (!node.isVisibleToUser) return false
        if (node.isEditable) return false

        // 通道一：文本 / 内容描述
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        for (candidate in listOf(text, desc)) {
            if (candidate.isNullOrEmpty() || candidate.length > maxTextLen) continue
            val lower = candidate.lowercase()
            for (kw in rules.keywords) {
                if (candidate.contains(kw) || lower.contains(kw.lowercase())) return true
            }
        }

        // 通道二：ViewID（如 com.example:id/skip_view 命中 "skip"）
        val id = node.viewIdResourceName
        if (id != null) {
            val lower = id.lowercase()
            for (rule in rules.viewIds) {
                if (rule.length >= minViewIdLen && lower.contains(rule.lowercase())) return true
            }
        }
        return false
    }
}
