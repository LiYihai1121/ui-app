package com.ldp.adskip.device

/**
 * 权限 / 系统开关状态的**纯逻辑模型** —— 零 Android 依赖，可在纯 JVM 单测中断言。
 *
 * 为什么要有这层：本应用的能力开关分散在四个互不相关的系统机制里（无障碍服务、电池优化、
 * 厂商自启动、快捷磁贴），而它们**都没有统一的权限 API**，只能各自单独探测。
 * 「探测得到什么」与「该怎么呈现」必须分离：探测是易错的平台调用，呈现是稳定的产品决策。
 * 本文件承载后者，前者在 [PermissionInspector]（Android 侧）。
 *
 * 关键设计：状态是**三态**而非布尔。厂商自启动在 MIUI/EMUI/ColorOS 上均无公开查询 API，
 * 强行用布尔表达会诱使 UI 断言「已开启/未开启」——那是在编造事实。这里显式区分
 * [PermissionState.UNKNOWN]，让 UI 有条件显示「无法检测，请手动确认」而不是给一个假状态。
 */
enum class PermissionState {
    /** 已开启 / 已就绪。 */
    GRANTED,

    /** 明确未开启。 */
    DENIED,

    /**
     * 无法程序化判定（无公开 API、探测异常、或该 ROM 不适用）。
     *
     * **不得降级为 [DENIED]**：那会让 UI 谎报「未开启」，用户反复跳转却永远看不到变化。
     * 也**不得降级为 [GRANTED]**：那会让用户以为已受保护，实则保护并未生效。
     */
    UNKNOWN,
}

/** 一个可查询的系统开关/权限项。 [key] 用于日志与 UI 侧稳定匹配，不随文案变化。 */
data class PermissionItem(val key: String, val state: PermissionState)

/**
 * 权限项的稳定标识。
 *
 * 与 [PermissionItem.key] 分离：key 是数据，这里是编译期常量，
 * 避免调用处出现裸字符串导致的拼写错误（编译器可查）。
 */
object PermissionKeys {
    const val ACCESSIBILITY = "accessibility"
    const val BATTERY = "battery"
    const val VENDOR_KEEPALIVE = "vendor_keepalive"
    const val QUICK_TILE = "quick_tile"
}

/** 权限项的呈现决策结果，供 UI 直接消费。 */
data class PermissionPresentation(
    val key: String,
    /** 状态圆点是否呈现为「已就绪」。 */
    val ready: Boolean,
    /** 状态文案。 */
    val statusText: String,
    /** 该项是否值得提供跳转入口。 */
    val actionable: Boolean,
)

object PermissionLogic {

    /**
     * 单项状态的呈现决策（纯函数）。
     *
     * @param state 探测结果
     * @param supported 该项在当前 ROM 上是否适用（如 GENERIC ROM 无厂商自启动项）
     * @param readyText / deniedText / unknownText / unsupportedText 已本地化的文案
     */
    fun present(
        key: String,
        state: PermissionState,
        supported: Boolean,
        readyText: String,
        deniedText: String,
        unknownText: String,
        unsupportedText: String,
    ): PermissionPresentation = when {
        !supported -> PermissionPresentation(
            key = key,
            ready = true,
            statusText = unsupportedText,
            actionable = false,
        )

        // UNKNOWN 与 DENIED 必须给出不同文案：前者是「查不到」，后者是「确实没开」。
        // 混为一谈会让用户在「其实已开启」的系统上被反复引导去设置页。
        state == PermissionState.UNKNOWN -> PermissionPresentation(
            key = key,
            ready = false,
            statusText = unknownText,
            actionable = true,
        )

        state == PermissionState.GRANTED -> PermissionPresentation(
            key = key,
            ready = true,
            statusText = readyText,
            actionable = true,
        )

        else -> PermissionPresentation(
            key = key,
            ready = false,
            statusText = deniedText,
            actionable = true,
        )
    }

    /**
     * 整组呈现：**仅统计「确定已就绪」**，UNKNOWN 与 DENIED 都不算就绪。
     *
     * 用于「N 项已就绪 / 共 M 项」这类汇总文案。把 UNKNOWN 计入未就绪是保守且诚实的：
     * 查不到状态时不应向用户保证「已受保护」。
     */
    fun readyCount(items: List<PermissionItem>): Int = items.count { it.state == PermissionState.GRANTED }

    /** 是否存在任何未就绪项（UNKNOWN 亦计入），决定是否需要给出行内的引导语气。 */
    fun needsAttention(items: List<PermissionItem>): Boolean = items.any {
        it.state != PermissionState.GRANTED
    }
}
