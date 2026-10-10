package com.qingqi.adskip.device

/**
 * 单项权限/系统开关的状态。
 *
 * 三态而非布尔，沿用无障碍与电池豁免已确立的原则：**不确定不等于未开启**。
 */
enum class PermissionStatus {
    /** 已确认就绪。 */
    READY,

    /** 已确认需要用户处理。 */
    NEEDS_ACTION,

    /**
     * 无法确认（系统无公开查询接口，或查询失败）。
     *
     * 用户需要自己去系统页核对，因此这类项**仍必须给出入口**，否则「无法确认」会变成死路。
     */
    UNKNOWN,
}

/** 权限清单中的一项。 */
data class PermissionItem(
    val key: String,
    /**
     * 已本地化的标题，由 UI 层注入。
     *
     * 判定层不持有资源 id（那会让本文件重新依赖 Android，失去可单测性），
     * 因此 [PermissionCenter.build] 留空，由组件按 [key] 取对应文案。
     */
    val title: String = "",
    val status: PermissionStatus,
    /** 该项是否提供「去设置」入口。 */
    val actionable: Boolean,
)

/** 权限项的稳定标识，供 UI 分发点击与测试断言使用。 */
object PermissionKeys {
    const val ACCESSIBILITY = "accessibility"
    const val BATTERY = "battery"
    const val VENDOR_KEEPALIVE = "vendor_keepalive"
    const val QUICK_TILE = "quick_tile"
}

/**
 * 权限与系统开关的**单一汇总点**（零 Android 依赖，可纯 JVM 单测）。
 *
 * 存在理由：「跳过静默失效」是最高频的用户投诉，成因却是四类互不相干的系统开关——
 * 无障碍没开、后台被电池优化杀掉、厂商自启动没放行、磁贴没加。此前它们分散在
 * 首页状态环、「我的」页无障碍卡片与设置页三处，用户无法一眼看出「到底还差什么」。
 * 这里把四类收敛成一张可核对清单。
 *
 * 判定原则（与 [AccessibilityStatus] / [BatteryExemption] 一致）：
 * **能查的查真值，查不到的如实说不知道，绝不谎报。**
 *
 * @param title 已本地化的标题。文案由调用方（UI 层）注入，本文件不持有资源 id，
 *        以便继续留在「零 Android 依赖」的可单测范围内。
 */
object PermissionCenter {

    /** 无障碍服务状态 → 权限项状态。 */
    fun accessibilityStatus(status: AccessibilityStatus): PermissionStatus = when (status) {
        AccessibilityStatus.ON -> PermissionStatus.READY
        AccessibilityStatus.OFF -> PermissionStatus.NEEDS_ACTION
        AccessibilityStatus.UNKNOWN -> PermissionStatus.UNKNOWN
    }

    /** 电池优化豁免 → 权限项状态。 */
    fun batteryStatus(exemption: BatteryExemption): PermissionStatus = when (exemption) {
        BatteryExemption.EXEMPT -> PermissionStatus.READY
        BatteryExemption.NOT_EXEMPT -> PermissionStatus.NEEDS_ACTION
        BatteryExemption.UNKNOWN -> PermissionStatus.UNKNOWN
    }

    /**
     * 厂商自启动**恒为 [PermissionStatus.UNKNOWN]**。
     *
     * 这是刻意的、不可「优化」掉的：厂商自启动没有公开查询 API。谎报「未开启」
     * 会让已经开好的用户在设置页与系统页之间来回跑，而状态永远不变。
     */
    fun vendorKeepAliveStatus(): PermissionStatus = PermissionStatus.UNKNOWN

    /**
     * 快捷磁贴状态。
     *
     * 系统同样没有「该磁贴是否已被添加」的公开查询，因此以磁贴服务自身的
     * `onTileAdded` 记录为准：有记录 = READY，无记录 = UNKNOWN（而非未添加——
     * 用户可能早已添加，只是本机记录被清过）。
     */
    fun quickTileStatus(added: Boolean): PermissionStatus =
        if (added) PermissionStatus.READY else PermissionStatus.UNKNOWN

    /**
     * 组装清单。顺序即展示顺序：无障碍与电池是「不设就没法工作」的硬前提，
     * 厂商保活与磁贴是增强项，用户按此优先级逐项处理最省事。
     */
    fun build(
        accessibility: AccessibilityStatus,
        battery: BatteryExemption,
        tileAdded: Boolean,
    ): List<PermissionItem> = listOf(
        PermissionItem(
            key = PermissionKeys.ACCESSIBILITY,
            title = "",
            status = accessibilityStatus(accessibility),
            actionable = true,
        ),
        PermissionItem(
            key = PermissionKeys.BATTERY,
            title = "",
            status = batteryStatus(battery),
            actionable = true,
        ),
        PermissionItem(
            key = PermissionKeys.VENDOR_KEEPALIVE,
            title = "",
            status = vendorKeepAliveStatus(),
            actionable = true,
        ),
        PermissionItem(
            key = PermissionKeys.QUICK_TILE,
            title = "",
            status = quickTileStatus(tileAdded),
            actionable = true,
        ),
    )

    /** 已确认就绪的项数。UNKNOWN 不计入——它是「不知道」，不是「已就绪」。 */
    fun readyCount(items: List<PermissionItem>): Int = items.count { it.status == PermissionStatus.READY }
}
