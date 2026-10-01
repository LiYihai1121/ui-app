package com.ldp.adskip.device

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.ldp.adskip.core.LogRing
import com.ldp.adskip.service.SkipAdService

/**
 * 权限 / 系统开关的**唯一探测入口**：把四种互不相关的系统机制归一成 [PermissionItem]。
 *
 * 分层理由（对齐 [KeepAliveNavigator] 与 [QuickTileLogic]）：
 * - 本对象只做「调用平台 API 读状态」，不碰 UI、不拼文案；
 * - 文案与呈现决策在 [PermissionLogic]（纯 Kotlin，可 JVM 单测）；
 * - 跳转一律经 [KeepAliveNavigator]，本对象**不**做 `startActivity`。
 *
 * 边界契约：本包属 `device/` 层，允许依赖 `core/` 与 `service/`，禁止依赖
 * `ui/`、`data/`、`net/`、`sync/`（见 ARCHITECTURE.md 2.1 与 ArchitectureBoundaryTest）。
 *
 * 设计原则：**探测失败即 UNKNOWN，绝不猜测**。四个探测点里只有两个有可靠的公开 API，
 * 厂商自启动在各家 ROM 上都没有；把它报成 DENIED 会诱使用户反复跳转，故一律 fail-safe。
 */
object PermissionInspector {

    private const val TAG = "PermissionInspector"

    /** SystemUI 记录「已添加到快捷设置的磁贴」列表的 `Settings.Secure` 键（未文档化内部约定）。 */
    private const val QS_TILES_SECURE_SETTING = "sysui_qs_tiles"

    /**
     * 一次性采集全部权限项。
     *
     * 顺序即 UI 呈现顺序：先「能不能工作」（无障碍），再「能不能活得久」（电池 / 自启动），
     * 最后是快捷入口（磁贴）。用户排查故障时也按这个顺序逐项确认。
     */
    fun inspectAll(context: Context): List<PermissionItem> = listOf(
        PermissionItem(PermissionKeys.ACCESSIBILITY, inspectAccessibility(context)),
        PermissionItem(PermissionKeys.BATTERY, inspectBatteryExempt(context)),
        PermissionItem(PermissionKeys.VENDOR_KEEPALIVE, inspectVendorKeepAlive(context)),
        PermissionItem(PermissionKeys.QUICK_TILE, inspectQuickTileAdded(context)),
    )

    /**
     * 无障碍服务是否已在系统设置中启用。
     *
     * 用 [SkipAdService.isEnabled]（系统「已启用服务」列表）而非进程内快照 `running`：
     * 用户刚从系统设置页开启回来时，Service 回调可能尚未触发，此时 `running` 仍为 false，
     * 用它会让 UI 显示「未开启」——而用户明明刚开完。
     */
    fun inspectAccessibility(context: Context): PermissionState =
        if (SkipAdService.isEnabled(context)) PermissionState.GRANTED else PermissionState.DENIED

    /**
     * 是否已加入电池优化白名单。
     *
     * [PowerManager.isIgnoringBatteryOptimizations] 在 API 23+ 可用（minSdk 26，无兼容分支）。
     * 注意：返回 true 仅代表**系统**白名单，用户若在厂商后台管理里关了自启动仍会被强杀，
     * 故本项与 [PermissionKeys.VENDOR_KEEPALIVE] 是两件独立的事，不可互相推导。
     */
    fun inspectBatteryExempt(context: Context): PermissionState = try {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm == null) {
            PermissionState.UNKNOWN
        } else if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            PermissionState.GRANTED
        } else {
            PermissionState.DENIED
        }
    } catch (e: Exception) {
        LogRing.w(TAG, "battery probe failed: ${e.message}")
        PermissionState.UNKNOWN
    }

    /**
     * 厂商自启动 / 后台管理是否已放行 —— **恒为 [PermissionState.UNKNOWN]**。
     *
     * 为什么不做探测：MIUI 的 `miui.intent.action.OP_AUTO_START`、EMUI 的
     * `StartupNormalAppListActivity`、ColorOS 的 `StartupAppListActivity` 都是**只有跳转入口、
     * 没有查询入口**的页面。各家自启动开关存储在厂商私有 provider / AppOps 里，
     * 读取要么无公开 API，要么需反射私有字段：
     * - 反射方案随 ROM 升级随时失效，且部分 ROM 对非系统应用直接抛 SecurityException；
     * - 探测失败若被当成 DENIED，UI 会显示「未开启」并反复引导，用户却始终看不到状态变化，
     *   比明确说「查不到」更糟。
     *
     * 因此这里返回 UNKNOWN，由 UI 显示「无法检测，请手动确认」并提供跳转入口。
     * 待某厂商提供稳定公开 API 后，可在此单点接入，无需改动 UI 与呈现逻辑。
     */
    fun inspectVendorKeepAlive(context: Context): PermissionState {
        // 保留一次探测意图的日志，便于将来接入真实实现时对照排查节奏；
        // 不做反射探测的原因见上方注释。
        return PermissionState.UNKNOWN
    }

    /**
     * 快捷磁贴是否已添加到快捷设置面板。
     *
     * **为何不能用 `TileService.queryTileServices`**：该方法在 AOSP 中标注 `@hide`，
     * 未进入公开 SDK（已在 compileSdk 37 的 `android.jar` 上核实：
     * `TileService` 的 `public static` 成员里只有 `requestListeningState`）。
     * 调用它无法通过编译，改为反射则随时可能被隐藏 API 限制拦下，故不采用。
     *
     * 采用的方案是读取 SystemUI 写入的 `Settings.Secure` 键 `sysui_qs_tiles`
     * （形如 `pkg/.cls,pkg2/.cls2`，扁平化 ComponentName 列表）。它同样是未文档化的内部约定，
     * 但**只读、无需任何权限**，且被大量应用用于「磁贴是否已添加」的判断。
     *
     * 失败即 [PermissionState.UNKNOWN] 的两种情况：该键不存在或为空（ROM 未启用 QS 面板 /
     * 厂商魔改），以及读取抛异常。**绝不因为解析失败就报 DENIED**——那会把
     * 「查不到」谎报成「没添加」，用户会被引导去重复添加一个其实已经加过的磁贴。
     */
    fun inspectQuickTileAdded(context: Context): PermissionState = try {
        val raw = Settings.Secure.getString(context.contentResolver, QS_TILES_SECURE_SETTING)
        if (raw.isNullOrBlank()) {
            // 键缺失：无法区分「未添加」与「本机不支持/ROM 魔改」，保守报未知
            PermissionState.UNKNOWN
        } else {
            val mine = ComponentName(context, SkipTileService::class.java).flattenToString()
            if (raw.split(',').any { it.trim() == mine }) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }
        }
    } catch (e: Throwable) {
        // 同 KeepAliveNavigator.requestAddTile 的理由：跨 ROM 差异可能抛 Error 而非 Exception，
        // 在 ViewModel 协程里未捕获会直接崩。
        LogRing.w(TAG, "tile probe failed: ${e.javaClass.simpleName}: ${e.message}")
        PermissionState.UNKNOWN
    }

    /**
     * 厂商自启动项在当前 ROM 上是否适用。
     *
     * GENERIC（AOSP 及轻度定制 ROM）没有自启动/后台管理开关，展示该项只会让用户
     * 去一个不存在的地方找，故直接标记为不适用。
     */
    fun supportsVendorKeepAlive(vendor: Vendor): Boolean = vendor != Vendor.GENERIC

    /** 是否支持应用主动请求添加磁贴（与 [KeepAliveNavigator.canRequestAddTile] 同源，避免两处判空分叉）。 */
    fun supportsQuickTileRequest(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
