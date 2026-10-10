package com.qingqi.adskip.device

import java.util.Locale

/**
 * 厂商 ROM 识别与「自启动 / 后台运行白名单」跳转元数据表。
 *
 * **纯 Kotlin、零 Android 依赖**：识别规则与入口表是纯数据，可在纯 JVM 单测中断言；
 * 真正的 Intent 组装与可用性探测在 [KeepAliveNavigator]（Android 侧）完成。
 *
 * 为什么需要它：国内深度定制 ROM（MIUI / HarmonyOS / ColorOS / OriginOS / Flyme / One UI）
 * 会在息屏或切后台一段时间后强杀无障碍服务，「跳过」随之静默失效；
 * 唯一切实有效的补救是把本应用加入厂商自启动 / 后台白名单，而各家的入口组件名各不相同。
 *
 * 入口表属于**尽力而为的社区经验值**：可能随 ROM 升级失效，
 * 因此 [KeepAliveNavigator] 每次跳转前都会用 `PackageManager` 复核可解析性，失败即降级到下一个候选。
 * 契约提醒：新增/修改入口表时必须同步在 `AndroidManifest.xml` 的 `<queries>` 中声明对应
 * 包名与 action（Android 11+ 包可见性），由 `ManifestContractTest` 在 CI 强制校验。
 */
enum class Vendor(val id: String) {
    XIAOMI("xiaomi"),
    HUAWEI("huawei"),
    HONOR("honor"),
    OPPO("oppo"),
    VIVO("vivo"),
    MEIZU("meizu"),
    ONEPLUS("oneplus"),
    SAMSUNG("samsung"),
    GENERIC("generic"),
}

/** 跳转时如何把「本应用包名」传递给目标界面。 */
enum class PackagePayload {
    /** 不传包名（目标页本身就是本应用的配置页）。 */
    NONE,

    /** 以 `package:<本应用包名>` 数据 URI 传递（系统「应用详情」类页面）。 */
    DATA_URI,

    /** 以 `packageName` 字符串 extra 传递（少数厂商自定义约定，如魅族）。 */
    EXTRA_NAME,
}

/**
 * 一个候选跳转入口。
 *
 * @param action 目标 action（与 [pkg] 可同时存在，用于限定搜索范围）
 * @param pkg 目标包名；与 [cls] 同时存在时按显式组件跳转
 * @param cls 目标类名（全限定，不使用相对 `.Foo` 写法，避免歧义）
 * @param payload 本应用包名的传递方式
 */
data class GuideEntry(
    val action: String? = null,
    val pkg: String? = null,
    val cls: String? = null,
    val payload: PackagePayload = PackagePayload.NONE,
) {
    init {
        require(action != null || pkg != null) { "GuideEntry 至少需要 action 或 pkg" }
        require(cls == null || pkg != null) { "GuideEntry 指定 cls 时必须同时指定 pkg" }
    }

    /** 是否为显式组件跳转（组件名稳定，优先使用）。 */
    val isComponent: Boolean get() = pkg != null && cls != null

    /** 日志用稳定标识。 */
    val key: String get() = "${action ?: "-"}|${pkg ?: "-"}|${cls ?: "-"}"
}

object VendorKeepAlive {

    /**
     * 厂商别名表，**顺序即优先级**（先匹配到的胜出）。
     *
     * 取值来自 `Build.MANUFACTURER` / `Build.BRAND` 的小写拼接，覆盖同一品牌下的子品牌
     * （如 `Redmi`/`POCO` 属小米、`realme` 属 OPPO 系、`iQOO` 属 vivo 系）。
     */
    private val ALIASES: List<Pair<String, Vendor>> = listOf(
        "honor" to Vendor.HONOR,
        "huawei" to Vendor.HUAWEI,
        "xiaomi" to Vendor.XIAOMI,
        "redmi" to Vendor.XIAOMI,
        "poco" to Vendor.XIAOMI,
        "oneplus" to Vendor.ONEPLUS,
        "oppo" to Vendor.OPPO,
        "realme" to Vendor.OPPO,
        "vivo" to Vendor.VIVO,
        "iqoo" to Vendor.VIVO,
        "meizu" to Vendor.MEIZU,
        "samsung" to Vendor.SAMSUNG,
    )

    /** 由厂商/子品牌识别所属 ROM；无法识别或入参为空时返回 [Vendor.GENERIC]。 */
    fun detect(manufacturer: String? = null, brand: String? = null): Vendor {
        val raw = listOfNotNull(manufacturer, brand).joinToString(" ").lowercase(Locale.ROOT)
        if (raw.isBlank()) return Vendor.GENERIC
        return ALIASES.firstOrNull { (alias, _) -> raw.contains(alias) }?.second ?: Vendor.GENERIC
    }

    /**
     * 通用兜底入口：AOSP 与轻度定制 ROM 上都能落地的系统页面。
     *
     * action 以字面量书写（而非引用 `android.provider.Settings` 常量），
     * 以保持本文件「零 Android 依赖」，从而可纯 JVM 单测。
     */
    fun genericEntries(): List<GuideEntry> = listOf(
        GuideEntry(
            action = "android.settings.APPLICATION_DETAILS_SETTINGS",
            payload = PackagePayload.DATA_URI,
        ),
        GuideEntry(action = "android.settings.ACCESSIBILITY_SETTINGS"),
        GuideEntry(action = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
        GuideEntry(
            action = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            payload = PackagePayload.DATA_URI,
        ),
    )

    /** 厂商入口 + 通用兜底构成的完整候选链（按顺序尝试）。 */
    fun candidates(vendor: Vendor): List<GuideEntry> = entries(vendor) + genericEntries()

    /** 全部候选链中出现的包名集合，用于生成/校验 `<queries>` 包可见性声明。 */
    fun allPackages(): Set<String> = Vendor.entries.flatMap { candidates(it) }.mapNotNull { it.pkg }.toSet()

    /** 全部候选链中出现的 action 集合，用于生成/校验 `<queries>` 可见性声明。 */
    fun allActions(): Set<String> = Vendor.entries.flatMap { candidates(it) }.mapNotNull { it.action }.toSet()

    /** 指定 ROM 的自启动/后台管理候选入口（按经验成功率排序）。 */
    fun entries(vendor: Vendor): List<GuideEntry> = when (vendor) {
        Vendor.XIAOMI -> listOf(
            GuideEntry(
                action = "miui.intent.action.OP_AUTO_START",
                pkg = "com.miui.securitycenter",
            ),
            GuideEntry(
                pkg = "com.miui.securitycenter",
                cls = "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            GuideEntry(
                pkg = "com.miui.securitycenter",
                cls = "com.miui.powercenter.PowerSettings",
            ),
        )

        Vendor.HUAWEI -> listOf(
            GuideEntry(
                pkg = "com.huawei.systemmanager",
                cls = "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            GuideEntry(
                pkg = "com.huawei.systemmanager",
                cls = "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            ),
            GuideEntry(
                pkg = "com.huawei.systemmanager",
                cls = "com.huawei.systemmanager.mainscreen.MainScreenActivity",
            ),
        )

        Vendor.HONOR -> listOf(
            GuideEntry(
                pkg = "com.hihonor.systemmanager",
                cls = "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            GuideEntry(
                pkg = "com.hihonor.systemmanager",
                cls = "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity",
            ),
        )

        Vendor.OPPO -> listOf(
            GuideEntry(
                pkg = "com.coloros.safecenter",
                cls = "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            GuideEntry(
                pkg = "com.coloros.safecenter",
                cls = "com.coloros.safecenter.startupapp.StartupAppListActivity",
            ),
            GuideEntry(
                pkg = "com.oplus.safecenter",
                cls = "com.oplus.safecenter.startupapp.StartupAppListActivity",
            ),
        )

        Vendor.VIVO -> listOf(
            GuideEntry(
                pkg = "com.vivo.permissionmanager",
                cls = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
            GuideEntry(
                pkg = "com.iqoo.secure",
                cls = "com.iqoo.secure.safeguard.PurviewTabActivity",
            ),
        )

        Vendor.MEIZU -> listOf(
            GuideEntry(
                action = "com.meizu.safe.security.SHOW_APPSEC",
                pkg = "com.meizu.safe",
                payload = PackagePayload.EXTRA_NAME,
            ),
            GuideEntry(
                pkg = "com.meizu.safe",
                cls = "com.meizu.safe.permission.SystemAppActivity",
            ),
        )

        Vendor.ONEPLUS -> listOf(
            GuideEntry(
                pkg = "com.oneplus.security",
                cls = "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            ),
        )

        Vendor.SAMSUNG -> listOf(
            GuideEntry(
                pkg = "com.samsung.android.lool",
                cls = "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
            GuideEntry(
                pkg = "com.samsung.android.sm_cn",
                cls = "com.samsung.android.sm.ui.battery.BatteryActivity",
            ),
        )

        Vendor.GENERIC -> emptyList()
    }
}
