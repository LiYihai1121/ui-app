package com.ldp.adskip.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.R
import com.ldp.adskip.core.AppEvents
import com.ldp.adskip.core.Clock
import com.ldp.adskip.core.LogRing
import com.ldp.adskip.data.Prefs
import com.ldp.adskip.data.RulesRepository
import com.ldp.adskip.data.StatsRepository
import com.ldp.adskip.device.AccessibilityStatus
import com.ldp.adskip.engine.SafetyGuard
import com.ldp.adskip.engine.SkipRuleEngine
import com.ldp.adskip.net.SyncClient

/**
 * 无障碍服务（薄编排层）。
 *
 * 职责：接收事件 → 节流去抖 → 委托 [SkipRuleEngine] 找目标 → 安全护栏复核 → 执行点击 → 记录/上报。
 * 匹配策略在 engine 层，规则存取在 data 层，网络在 net 层。
 *
 * v2.2 增强：
 * - 经 [AdskipApp.container] 取依赖（手动 DI）
 * - 使用 [Clock] 注入时间（节流去抖不硬依赖 SystemClock）
 * - 点击前过 [SafetyGuard]（黑名单/合法性护栏）
 * - [onServiceConnected] 重建全部运行态
 * - 监听 [Intent.ACTION_SCREEN_OFF] 重置节流窗口，防休眠唤醒后首帧误判
 */
class SkipAdService : AccessibilityService() {

    companion object {
        const val ACTION_SERVICE_STATE = "com.ldp.adskip.SERVICE_STATE"
        const val ACTION_SKIPPED = "com.ldp.adskip.SKIPPED"
        const val ACTION_REQUEST_SHUTDOWN = "com.ldp.adskip.REQUEST_SHUTDOWN"
        const val ACTION_EXPORT_SNAPSHOT = "com.ldp.adskip.EXPORT_SNAPSHOT"

        /**
         * 内部广播权限（signature 级，manifest 声明）：
         * 动态接收器只接受本应用投递的关停/快照导出指令，
         * 补齐 Android 8–12 动态注册默认可被任意应用投递的口子。
         */
        const val INTERNAL_PERMISSION = "com.ldp.adskip.permission.INTERNAL"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_PKG = "pkg"

        @Volatile var running = false
            private set

        private const val CLICK_INTERVAL_MS = 1200L // 同一应用点击去抖
        private const val SCAN_INTERVAL_MS = 150L // 全局扫描节流
        private const val IGNORE_PACKAGES = "com.android.systemui"

        private val lastClickMap = HashMap<String, Long>()

        @Volatile private var lastScanAt = 0L

        /**
         * 无障碍服务在系统设置中是否已启用。
         *
         * 委托给 [AccessibilityStatus]：那里是全仓**唯一**查询系统已启用列表的地方
         * （依赖边界使然——`ui/` 不能 import `service/`，而 `device/` 可被双方使用）。
         * 本方法保留为 `service/` 层对内的便捷入口。
         */
        fun isEnabled(context: Context): Boolean = AccessibilityStatus.detect(
            context = context,
            serviceClassName = SkipAdService::class.java.name,
            processSignal = running,
        ) == AccessibilityStatus.ON

        /**
         * 请求关闭当前运行中的服务（由快捷磁贴下发），内部走 `disableSelf()`——
         * 与用户在系统设置里关掉开关等价，会正常回调 [onDestroy] 完成清理与状态广播。
         *
         * @return 是否已成功投递关闭请求
         */
        fun requestShutdown(context: Context): Boolean {
            if (!running) return false
            return try {
                context.sendBroadcast(
                    Intent(ACTION_REQUEST_SHUTDOWN).setPackage(context.packageName),
                )
                true
            } catch (e: Exception) {
                LogRing.w("Service", "requestShutdown failed: ${e.message}")
                false
            }
        }
    }

    private val engine = SkipRuleEngine()
    private lateinit var clock: Clock
    private lateinit var rulesRepo: RulesRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var syncClient: SyncClient

    /**
     * 快捷磁贴的关闭请求接收端（进程内、定向投递）。
     *
     * 用广播而非持有 Service 实例的静态引用：`device/` 层只需调用 companion 的
     * [requestShutdown]，不必反向依赖本类的实例状态，避免跨包持有 Service 造成泄漏。
     */
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_REQUEST_SHUTDOWN) return
            LogRing.d("Service", "shutdown requested by quick tile")
            disableSelf()
        }
    }

    private val snapshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_EXPORT_SNAPSHOT) return
            if (context == null) return
            LogRing.d("Service", "snapshot export requested")
            exportSnapshot(context)
        }
    }

    private class ReceiverHandle(
        private val service: Context,
        private val receiver: BroadcastReceiver,
        private val action: String,
    ) {
        private var registered = false

        fun register() {
            if (registered) return
            val filter = IntentFilter(action)
            try {
                // broadcastPermission=INTERNAL_PERMISSION：所有 API 级别都只接受
                // 同签名应用投递；API 33+ 再叠加 RECEIVER_NOT_EXPORTED。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    service.registerReceiver(
                        receiver,
                        filter,
                        INTERNAL_PERMISSION,
                        null,
                        Context.RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    service.registerReceiver(receiver, filter, INTERNAL_PERMISSION, null)
                }
                registered = true
            } catch (e: Exception) {
                LogRing.w("Service", "register $action receiver failed: ${e.message}")
            }
        }

        fun unregister() {
            if (!registered) return
            registered = false
            try {
                service.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // already unregistered
            }
        }
    }

    private val shutdownHandle = ReceiverHandle(this, shutdownReceiver, ACTION_REQUEST_SHUTDOWN)
    private val snapshotHandle = ReceiverHandle(this, snapshotReceiver, ACTION_EXPORT_SNAPSHOT)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = AdskipApp.get(this)
        clock = container.clock
        rulesRepo = container.rulesRepo
        statsRepo = container.statsRepo
        syncClient = container.syncClient
        running = true
        AppEvents.setServiceRunning(true)
        shutdownHandle.register()
        snapshotHandle.register()
        sendBroadcast(Intent(ACTION_SERVICE_STATE).setPackage(packageName).putExtra(EXTRA_RUNNING, true))
        LogRing.d("Service", "onServiceConnected")
    }

    override fun onDestroy() {
        running = false
        AppEvents.setServiceRunning(false)
        shutdownHandle.unregister()
        snapshotHandle.unregister()
        sendBroadcast(Intent(ACTION_SERVICE_STATE).setPackage(packageName).putExtra(EXTRA_RUNNING, false))
        // 强制落盘待写统计
        if (::statsRepo.isInitialized) statsRepo.flush()
        super.onDestroy()
    }

    private fun exportSnapshot(context: Context) {
        val root = rootInActiveWindow ?: run {
            LogRing.w("Service", "snapshot export: no active window")
            Toast.makeText(context, R.string.settings_snapshot_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val pkg = root.packageName?.toString() ?: context.packageName
        val activity = root.className?.toString() ?: "Unknown"
        val snapshot = NodeSnapshot.capture(root, pkg, activity)
        val text = snapshot.toShareText()

        // Service context 启动 Activity 必须带 FLAG_ACTIVITY_NEW_TASK，
        // 否则抛 AndroidRuntimeException 直接崩掉进程（顺带杀死无障碍服务）。
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "AdSkip NodeSnapshot $pkg")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                context.getString(R.string.logs_share),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Toast.makeText(context, R.string.settings_snapshot_exported, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == IGNORE_PACKAGES) return
        if (pkg == packageName && !AppEvents.testActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> trySkip(pkg)
        }
    }

    private fun trySkip(pkg: String) {
        if (Prefs.isDoNotDisturbEnabled(this) && isInDoNotDisturbPeriod()) return
        val now = clock.elapsedRealtime()
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        if (now - (lastClickMap[pkg] ?: 0L) < CLICK_INTERVAL_MS) return

        val rules = rulesRepo.ruleSetFor(pkg)
        if (rules.disabled || rules.isEmpty) return

        val root = rootInActiveWindow ?: return
        lastScanAt = now

        val rootNode = FrameworkAdNode(root)
        val target = engine.findTarget(rootNode, rules) ?: return

        // 安全护栏：点击前复核
        if (!SafetyGuard.canClick(target, pkg)) {
            // 只记录标签长度，不落其他应用的界面文本：LogRing 可被用户导出分享
            LogRing.w(
                "Safety",
                "blocked click on pkg=$pkg textLen=${target.text?.length ?: 0} descLen=${target.desc?.length ?: 0}",
            )
            return
        }

        if (!clickNode(target)) return

        lastClickMap[pkg] = now
        val label = appLabel(pkg)
        statsRepo.recordSkip(pkg, label)
        if (!AppEvents.testActive) {
            Toast.makeText(this, getString(R.string.toast_skipped, label), Toast.LENGTH_SHORT).show()
        }
        syncClient.reportSkip(Prefs.getServerUrl(this), pkg, label, Prefs.getDeviceId(this))
        AppEvents.emitSkipped(label)
        // 广播一律 setPackage 收窄到本应用：ACTION_SKIPPED 携带用户正在使用的应用名，
        // 不加限制会让任意第三方应用注册同名 action 即可监听（隐私泄露）。
        sendBroadcast(Intent(ACTION_SKIPPED).setPackage(packageName).putExtra(EXTRA_PKG, label))
    }

    private fun isInDoNotDisturbPeriod(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(java.util.Calendar.MINUTE)
        val start = Prefs.getDoNotDisturbStart(this)
        val end = Prefs.getDoNotDisturbEnd(this)
        return if (start <= end) minute in start until end else minute >= start || minute < end
    }

    /**
     * 点击执行：优先沿父链寻找可点击节点执行 ACTION_CLICK；
     * 找不到则按节点中心坐标派发模拟手势。
     */
    private fun clickNode(node: com.ldp.adskip.engine.AdNode): Boolean {
        // 先尝试 ACTION_CLICK（通过 AdNode.click()）
        if (node.click()) return true

        // 找父链可点击节点
        val clickableParent = node.clickableParent()
        if (clickableParent != null && clickableParent.click()) return true

        // 兜底：坐标手势
        val cx = node.centerX()
        val cy = node.centerY()
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + 1f, cy + 1f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }
}
