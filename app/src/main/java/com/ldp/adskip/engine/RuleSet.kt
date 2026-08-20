package com.ldp.adskip.engine

/**
 * 某应用当前生效的完整规则集合（全局规则 + 应用专属规则的合并结果）。
 */
data class RuleSet(
    val keywords: List<String>,
    val viewIds: List<String>,
    val disabled: Boolean = false
) {
    val isEmpty: Boolean get() = keywords.isEmpty() && viewIds.isEmpty()
}
