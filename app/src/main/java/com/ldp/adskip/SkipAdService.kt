package com.ldp.adskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 无障碍服务 v2：监听窗口变化，查找并自动点击「跳过」类按钮。
 *
 * 匹配规则（命中任一即点击）：
 *  1. 文本规则 —— 文本/内容描述包含关键词（全局 + 应用专属），限短文本，防误点长文
 *  2. ViewID 规则 —— 控件资源 ID 包含规则串（如 com.x:id/skip_view），
 *     用于纯图片、无文字的跳过按钮
 *
 * 防误触：同应用 1.2s 去抖、150ms 全局节流、单次遍历最多 500 节点、
 * 忽略系统 UI、排除输入框、支持按应用禁用。
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

        private const val CLICK_INTERVAL_MS = 1200L
        private const val SCAN_INTERVAL_MS = 150L
        private const val MAX_NODES = 500
        private const val MAX_TEXT_LEN = 12
        private const val MIN_VIEWID_LEN = 3
        private const val IGNORE_PACKAGES = "com.android.systemui"

        private val lastClickMap = HashMap<String, Long>()
        @Volatile private var lastScanAt = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        sendBroadcast(Intent(ACTION_SERVICE_STATE).putExtra(EXTRA_RUNNING, true))
    }

    override fun onDestroy() {
        running = false
        sendBroadcast(Intent(ACTION_SERVICE_STATE).putExtra(EXTRA_RUNNING, false))
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
        if (Prefs.isPackageDisabled(this, pkg)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> trySkip(pkg)
        }
    }

    private fun trySkip(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        if (now - (lastClickMap[pkg] ?: 0L) < CLICK_INTERVAL_MS) return

        val root = rootInActiveWindow ?: return
        lastScanAt = now

        val keywords = (Prefs.getKeywords(this) + Prefs.getPkgKeywords(this, pkg)).filter { it.isNotBlank() }
        val viewIds = (Prefs.getViewIds(this) + Prefs.getPkgViewIds(this, pkg))
            .filter { it.length >= MIN_VIEWID_LEN }
        if (keywords.isEmpty() && viewIds.isEmpty()) return

        if (findAndClick(root, keywords, viewIds)) {
            lastClickMap[pkg] = now
            val label = appLabel(pkg)
            Prefs.recordSkip(this, pkg, label)
            if (!testActive) {
                Toast.makeText(this, getString(R.string.toast_skipped, label), Toast.LENGTH_SHORT).show()
            }
            // 上报服务端（静默失败，不影响本地）
            SyncUtil.reportSkip(applicationContext, Prefs.getServerUrl(this), pkg, label)
            sendBroadcast(Intent(ACTION_SKIPPED).putExtra(EXTRA_PKG, label))
        }
    }

    /** 深度优先遍历节点树，找到第一个命中的节点并点击。 */
    private fun findAndClick(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        viewIds: List<String>
    ): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var steps = 0
        while (stack.isNotEmpty() && steps < MAX_NODES) {
            val node = stack.removeLast()
            steps++
            if (matches(node, keywords, viewIds) && clickNode(node)) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return false
    }

    private fun matches(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        viewIds: List<String>
    ): Boolean {
        if (!node.isVisibleToUser) return false
        if (node.isEditable) return false

        // 文本 / 内容描述匹配
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        for (candidate in listOf(text, desc)) {
            if (candidate.isNullOrEmpty() || candidate.length > MAX_TEXT_LEN) continue
            val lower = candidate.lowercase()
            for (kw in keywords) {
                if (candidate.contains(kw) || lower.contains(kw.lowercase())) return true
            }
        }

        // ViewID 匹配：com.example:id/skip_view 命中 "skip"
        val id = node.viewIdResourceName
        if (id != null) {
            val lower = id.lowercase()
            for (rule in viewIds) {
                if (lower.contains(rule.lowercase())) return true
            }
        }
        return false
    }

    /**
     * 点击目标：优先沿父链寻找可点击节点执行 ACTION_CLICK；
     * 找不到则按节点中心坐标派发模拟手势。
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
            depth++
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
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
