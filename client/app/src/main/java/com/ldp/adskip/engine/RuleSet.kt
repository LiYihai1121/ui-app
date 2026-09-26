package com.ldp.adskip.engine

import com.ldp.adskip.engine.selector.SelectorAst

/**
 * 某应用当前生效的完整规则集合（全局规则 + 应用专属规则的合并结果）。
 *
 * 三通道：keywords（文本关键词）/ viewIds（控件 ID）/ selectors（已编译选择器）。
 */
data class RuleSet(
    val keywords: List<String>,
    val viewIds: List<String>,
    /** 第三通道：已编译选择器（上游加载时编译一次；解析失败的条目已在上游丢弃） */
    val selectors: List<SelectorAst> = emptyList(),
    val disabled: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    val isEmpty: Boolean
        get() = keywords.isEmpty() && viewIds.isEmpty() && selectors.isEmpty()

    companion object {
        /**
         * 当前客户端支持的协议 schema 版本。
         *
         * selectors 字段已就绪，但 v2 载荷（`globalSelectors` / `apps.*.selectors`）随
         * DESIGN-PHASE1 步骤 C（服务端字段 + SyncClient 解析）落地时才升为 2——
         * 提前声明 2 无服务端配合且会让存量断言失真。
         */
        const val SCHEMA_VERSION = 1

        /** 低于此版本拒载并提示升级 */
        const val MIN_SCHEMA_VERSION = 1
    }
}
