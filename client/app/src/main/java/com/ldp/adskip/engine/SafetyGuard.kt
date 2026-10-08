package com.ldp.adskip.engine

/**
 * 安全护栏：引擎找到目标后、执行点击前的硬底线。
 *
 * 无论云端规则怎么被污染，客户端都有硬底线：
 * - 防自触发死循环（不点击本应用自身的节点）
 * - 黑名单硬编码，云规则不可覆盖（防止污染规则误触敏感按钮）
 * - 敏感系统界面整包拒绝（授权/安装/设置类界面不参与自动点击）
 * - 合法性校验（必须可见、面积 > 0）
 *
 * 黑名单检查覆盖**目标节点自身 + 父链（最多 [MAX_LABEL_DEPTH] 层）**：
 * 只看目标自身文案会被「无标签图标按钮」绕过——危险对话框里的确认按钮
 * 常常只有图标，敏感文案挂在其父容器上。
 */
object SafetyGuard {

    // 硬编码黑名单，云规则不可覆盖（英文一律小写后子串匹配）
    private val DENY_WORDS = listOf(
        // 中文敏感词
        "支付", "付款", "确认", "同意", "购买", "下单",
        "授权", "登录", "免密", "开通", "安装", "下载",
        "允许", "确定", "继续", "删除", "卸载", "清除", "重置", "格式化", "激活",
        // 英文敏感词（配合 lowercase 匹配）
        "pay", "purchase", "buy", "confirm", "agree", "allow", "grant",
        "install", "uninstall", "delete", "remove", "erase", "reset", "format",
        "login", "log in", "sign in", "password", "trust", "enable", "continue",
    )

    /**
     * 整包拒绝清单：授权/安装/系统设置类界面的任何点击都可能是高危操作，
     * 与规则内容无关一律不点。云规则不可覆盖。
     */
    private val DENY_PACKAGES = setOf(
        "android",
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
    )

    private const val SELF_PKG = "com.ldp.adskip"

    /** 父链文案检查深度：覆盖「按钮无标签、敏感文案在父容器」的绕过 */
    private const val MAX_LABEL_DEPTH = 3

    /**
     * 引擎找到目标后、执行点击前必须通过此检查。
     * @param node 引擎找到的目标节点
     * @param pkg 当前应用包名
     * @return true=可以点击, false=被安全护栏拦截
     */
    fun canClick(node: AdNode, pkg: String): Boolean {
        // 防自触发死循环 + 敏感系统界面整包拒绝
        if (!canClickPackage(pkg)) return false

        // 合法性校验：必须可见且面积 > 0
        if (!node.isVisible) return false
        if (node.boundsWidth() <= 0 || node.boundsHeight() <= 0) return false

        // 黑名单检查：目标自身 + 父链文本，任一层命中即拦
        var current: AdNode? = node
        var depth = 0
        while (current != null && depth < MAX_LABEL_DEPTH) {
            val label = ((current.text.orEmpty()) + (current.desc.orEmpty())).lowercase()
            if (DENY_WORDS.any { label.contains(it) }) return false
            current = current.parent
            depth++
        }
        return true
    }

    /**
     * 包级护栏：自定义取点规则这类**无节点目标**的点击路径也必须过同一份硬底线
     * （防自触发 + 敏感系统界面整包拒绝），否则坐标点击会绕开 [canClick]。
     */
    fun canClickPackage(pkg: String): Boolean = pkg != SELF_PKG && pkg !in DENY_PACKAGES
}
