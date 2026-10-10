package com.qingqi.adskip.engine

/**
 * 安全护栏：引擎找到目标后、执行点击前的硬底线。
 *
 * 无论云端规则怎么被污染，客户端都有硬底线：
 * - 防自触发死循环（不点击本应用自身的节点）
 * - 关键词黑名单硬编码，云规则不可覆盖（防止污染规则误触敏感按钮）
 * - 包级黑名单硬编码：支付/银行/数字钥匙类应用整包不进引擎
 *   （关键词黑名单只挡按钮文案，挡不住「换一个不含黑名单词的文案」
 *   在支付页面里点的场景——那一类场景的代价是资金损失）
 * - 合法性校验（必须可见、面积 > 0）
 */
object SafetyGuard {

    // 硬编码黑名单，云规则不可覆盖
    private val DENY_WORDS = listOf(
        "支付", "付款", "确认", "同意", "购买", "下单",
        "授权", "登录", "免密", "开通", "安装", "下载",
    )

    /**
     * 包级硬编码黑名单（精确包名）。
     *
     * 覆盖主流支付/银行/数字钥匙宿主：这些页面里的"跳过/继续"按钮
     * 出现在确认环节时会直接造成资金后果，宁可不跳过也不误触。
     */
    private val DENY_PACKAGES = listOf(
        "com.eg.android.AlipayGphone", // 支付宝
        "com.tencent.mm", // 微信（收付款/支付确认均在其中）
        "com.unionpay", // 云闪付
        "com.unionpay.tsmservice", // 云闪付卡服务
        "com.chinamworld.main", // 中国工商银行
        "com.chinamworld.lean", // 中国工商银行（旧版）
        "cmb.pb", // 招商银行
        "com.chinamworld.bocmbci", // 中国银行
        "com.android.bankabc", // 中国农业银行
        "com.icbc", // 工商银行（旧版）
        "com.baidu.wallet", // 度小满钱包
        "com.jd.payment", // 京东支付
    )

    /**
     * 包级黑名单的保守关键词标记。
     *
     * 只匹配包名**分段前缀**（以 `.` 或串首切分后的整段相等），不匹配
     * 任意子串——否则 `com.example.payment.demo` 这类正常应用会被误伤，
     * 而 `com.foo.pay` 这种与 `pay` 整段同名的包名才会命中。
     */
    private val DENY_PACKAGE_SEGMENTS = listOf(
        "alipay",
        "wallet",
        "bankabc",
        "unionpay",
        "purse",
    )

    private const val SELF_PKG = "com.qingqi.adskip"

    /**
     * 引擎找到目标后、执行点击前必须通过此检查。
     * @param node 引擎找到的目标节点
     * @param pkg 当前应用包名
     * @return true=可以点击, false=被安全护栏拦截
     */
    fun canClick(node: AdNode, pkg: String): Boolean {
        // 防自触发死循环
        if (pkg == SELF_PKG) return false

        // 包级硬底线：支付/银行类应用不进引擎（先于关键词检查）
        if (isPackageDenied(pkg)) return false

        // 黑名单检查：text + desc 中不得包含敏感词
        val label = (node.text.orEmpty()) + (node.desc.orEmpty())
        if (DENY_WORDS.any { label.contains(it) }) return false

        // 合法性校验：必须可见且面积 > 0
        if (!node.isVisible) return false
        return node.boundsWidth() > 0 && node.boundsHeight() > 0
    }

    /**
     * 包级黑名单判定：精确名单命中，或包名任一**整段**等于保守关键词。
     * 服务在匹配入口调用，命中即整包跳过，不进入任何通道。
     */
    fun isPackageDenied(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (pkg in DENY_PACKAGES) return true
        val segments = pkg.split('.')
        return segments.any { it.lowercase() in DENY_PACKAGE_SEGMENTS }
    }
}
