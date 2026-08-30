package com.ldp.adskip.engine

/**
 * 安全护栏：引擎找到目标后、执行点击前的硬底线。
 *
 * 无论云端规则怎么被污染，客户端都有硬底线：
 * - 防自触发死循环（不点击本应用自身的节点）
 * - 黑名单硬编码，云规则不可覆盖（防止污染规则误触敏感按钮）
 * - 合法性校验（必须可见、面积 > 0）
 */
object SafetyGuard {

    // 硬编码黑名单，云规则不可覆盖
    private val DENY_WORDS = listOf(
        "支付", "付款", "确认", "同意", "购买", "下单",
        "授权", "登录", "免密", "开通", "安装", "下载"
    )
    private const val SELF_PKG = "com.ldp.adskip"

    /**
     * 引擎找到目标后、执行点击前必须通过此检查。
     * @param node 引擎找到的目标节点
     * @param pkg 当前应用包名
     * @return true=可以点击, false=被安全护栏拦截
     */
    fun canClick(node: AdNode, pkg: String): Boolean {
        // 防自触发死循环
        if (pkg == SELF_PKG) return false

        // 黑名单检查：text + desc 中不得包含敏感词
        val label = (node.text.orEmpty()) + (node.desc.orEmpty())
        if (DENY_WORDS.any { label.contains(it) }) return false

        // 合法性校验：必须可见且面积 > 0
        if (!node.isVisible) return false
        return node.boundsWidth() > 0 && node.boundsHeight() > 0
    }
}
