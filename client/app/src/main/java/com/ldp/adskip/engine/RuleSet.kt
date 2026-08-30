package com.ldp.adskip.engine

/**
 * 某应用当前生效的完整规则集合（全局规则 + 应用专属规则的合并结果）。
 */
data class RuleSet(
    val keywords: List<String>,
    val viewIds: List<String>,
    val disabled: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    val isEmpty: Boolean get() = keywords.isEmpty() && viewIds.isEmpty()

    companion object {
        /** 当前客户端支持的协议 schema 版本 */
        const val SCHEMA_VERSION = 1

        /** 低于此版本拒载并提示升级 */
        const val MIN_SCHEMA_VERSION = 1
    }
}
