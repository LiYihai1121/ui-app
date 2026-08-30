package com.ldp.adskip.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
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
import com.ldp.adskip.engine.FrameworkAdNode
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
        const val EXTRA_RUNNING = "running"
        const val EXTRA_PKG = "pkg"

        @Volatile var running = false
            private set
        @Volatile var testActive = false

        private const val CLICK_INTERVAL_MS = 1200L   // 同一应用点击去抖
        private const val SCAN_INTERVAL_MS = 150L     // 全局扫描节流
        private const val IGNORE_PACKAGES = "com.android.systemui"

        private val lastClickMap = HashMap<String, Long>()
        @Volatile private var lastScanAt = 0L
    }

    private val engine = SkipRuleEngine()
    private lateinit var clock: Clock
    private lateinit var rulesRepo: RulesRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var syncClient: SyncClient

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = AdskipApp.get(this)
        clock = container.clock
        rulesRepo = container.rulesRepo
        statsRepo = container.statsRepo
        syncClient = container.syncClient
        running = true
        AppEvents.setServiceRunning(true)
        sendBroadcast(Intent(ACTION_SERVICE_STATE).putExtra(EXTRA_RUNNING, true))
        LogRing.d("Service", "onServiceConnected")
    }

    override fun onDestroy() {
        running = false
        AppEvents.setServiceRunning(false)
        sendBroadcast(Intent(ACTION_SERVICE_STATE).putExtra(EXTRA_RUNNING, false))
        // 强制落盘待写统计
        if (::statsRepo.isInitialized) statsRepo.flush()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == IGNORE_PACKAGES) return
        if (pkg == packageName && !testActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> trySkip(pkg)
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
            LogRing.w("Safety", "blocked click on pkg=$pkg label=${target.text}/${target.desc}")
            return
        }

        if (!clickNode(target)) return

        lastClickMap[pkg] = now
        val label = appLabel(pkg)
        statsRepo.recordSkip(pkg, label)
        if (!testActive) {
            Toast.makeText(this, getString(R.string.toast_skipped, label), Toast.LENGTH_SHORT).show()
        }
        syncClient.reportSkip(Prefs.getServerUrl(this), pkg, label, Prefs.getDeviceId(this))
        AppEvents.emitSkipped(label)
        sendBroadcast(Intent(ACTION_SKIPPED).putExtra(EXTRA_PKG, label))
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
