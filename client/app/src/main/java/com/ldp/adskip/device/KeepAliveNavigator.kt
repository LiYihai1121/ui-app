package com.ldp.adskip.device

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.ldp.adskip.R
import com.ldp.adskip.core.LogRing
import java.util.concurrent.Executor

/**
 * 系统级入口的**唯一跳转出口**：厂商保活引导、无障碍设置、应用详情、磁贴添加。
 *
 * 分层职责：本对象只做「Intent 组装 + 可用性探测 + 降级重试」，
 * 候选入口表（[VendorKeepAlive]）与磁贴交互决策（[QuickTileLogic]）均为纯 Kotlin，便于 JVM 单测。
 *
 * 边界契约：本包属 `device/` 层，允许依赖 `core/`（日志）与 `service/`（无障碍真实状态与关闭请求），
 * 禁止依赖 `ui/`、`data/`（见 ARCHITECTURE.md 2.1 与 ArchitectureBoundaryTest）。
 */
object KeepAliveNavigator {

    private const val TAG = "KeepAlive"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    /** 当前设备所属 ROM（[Build] 读取失败时返回 GENERIC，不抛异常）。 */
    fun detectVendor(): Vendor = try {
        VendorKeepAlive.detect(Build.MANUFACTURER, Build.BRAND)
    } catch (e: Exception) {
        LogRing.w(TAG, "detect vendor failed: ${e.message}")
        Vendor.GENERIC
    }

    /** 当前设备的完整保活候选链（厂商入口优先，通用兜底在后）。 */
    fun keepAliveCandidates(): List<GuideEntry> = VendorKeepAlive.candidates(detectVendor())

    /**
     * 打开「自启动 / 后台管理」设置页：按候选链依次尝试可解析的入口。
     *
     * @return 成功跳转的入口；全部候选均不可用时返回 `null`（由 UI 提示手动路径）
     */
    fun openKeepAliveSettings(context: Context): GuideEntry? {
        for (entry in keepAliveCandidates()) {
            if (start(context, entry)) {
                LogRing.d(TAG, "keep-alive opened: ${entry.key}")
                return entry
            }
        }
        LogRing.w(TAG, "no keep-alive entry resolvable on ${detectVendor().id}")
        return null
    }

    /** 打开系统无障碍设置；无法跳转时返回 `false`（调用方负责提示手动路径）。 */
    fun openAccessibilitySettings(context: Context): Boolean =
        start(context, GuideEntry(action = Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** 打开本应用的系统详情页。 */
    fun openAppDetails(context: Context): Boolean = start(
        context,
        GuideEntry(
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            payload = PackagePayload.DATA_URI,
        ),
    )

    /**
     * 打开「电池优化白名单」设置。
     *
     * 先尝试带本应用包名的直连请求页（Android 官方推荐），不可用时回退到白名单总列表页。
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean = listOf(
        GuideEntry(
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            payload = PackagePayload.DATA_URI,
        ),
        GuideEntry(action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    ).any { start(context, it) }

    // ---------- 快捷磁贴 ----------

    /** 当前系统是否支持「应用主动请求添加磁贴」（Android 13+ 的 `requestAddTileService`）。 */
    fun canRequestAddTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * 请求系统弹出「添加到快捷设置」确认框（Android 13+）。
     *
     * @param label 磁贴在确认框中的标题
     * @param onResult 归一化后的结果，在主线程回调
     * @return 是否已成功发起请求（`false` 表示版本不支持或系统服务缺失）
     */
    fun requestAddTile(context: Context, label: String, onResult: (TileAddResult) -> Unit): Boolean {
        if (!canRequestAddTile()) return false
        val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
        val component = ComponentName(context, SkipTileService::class.java)
        val icon = Icon.createWithResource(context, R.drawable.ic_qs_skip)
        return try {
            manager.requestAddTileService(component, label, icon, mainExecutor) { code ->
                onResult(toTileAddResult(code))
            }
            true
        } catch (e: Throwable) {
            // 这里必须捕获 Throwable 而非 Exception。
            //
            // `StatusBarManager.requestAddTileService` 的第 5 个参数在 AOSP 中经历过
            // `RemoteCallback` → `Consumer<Integer>` 的变更（已核对：android-34/35 均为
            // `Consumer<Integer>`；android-33 引入时的形态未能在本机证实）。若目标系统仍是
            // 旧签名，调用点会抛 `NoSuchMethodError`——它是 **Error**，`catch (Exception)`
            // 抓不到，会在 `viewModelScope` 协程里直接崩溃，且崩溃栈与「添加磁贴」毫无关联，
            // 极难归因。降级为提示手动添加，代价远小于崩溃。
            LogRing.w(TAG, "requestAddTileService failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * 把 [StatusBarManager] 的整型返回码归一化为 [TileAddResult]。
     *
     * 常量本身是 `static final int`，编译期即内联，故低版本设备上引用它们是安全的。
     */
    fun toTileAddResult(result: Int): TileAddResult = when (result) {
        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileAddResult.ADDED
        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileAddResult.ALREADY_ADDED
        StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> TileAddResult.NOT_FOREGROUND
        else -> TileAddResult.FAILED
    }

    // ---------- 内部实现 ----------

    /** 按入口组装 Intent（纯函数式，便于单独推演）。 */
    fun toIntent(context: Context, entry: GuideEntry): Intent {
        val intent = Intent()
        entry.action?.let { intent.action = it }
        entry.pkg?.let { intent.setPackage(it) }
        entry.cls?.let { intent.setClassName(entry.pkg!!, it) }
        when (entry.payload) {
            PackagePayload.NONE -> Unit

            PackagePayload.DATA_URI ->
                intent.data = Uri.fromParts("package", context.packageName, null)

            PackagePayload.EXTRA_NAME ->
                intent.putExtra("packageName", context.packageName)
        }
        return intent
    }

    private fun start(context: Context, entry: GuideEntry): Boolean {
        val intent = toIntent(context, entry)
        if (!isResolvable(context, intent)) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            // 部分 ROM 会在 resolve 通过后仍抛 SecurityException/ActivityNotFound
            LogRing.w(TAG, "startActivity failed for ${entry.key}: ${e.message}")
            false
        }
    }

    private fun isResolvable(context: Context, intent: Intent): Boolean = try {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    } catch (e: Exception) {
        LogRing.w(TAG, "resolveActivity failed: ${e.message}")
        false
    }
}
