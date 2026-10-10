package com.qingqi.adskip.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 节点快照工具：把当前界面无障碍节点树导出为 JSON，
 * 供规则编写者参考（解决「规则编写靠猜」问题）。
 *
 * 格式与约束见 [docs/planning/DESIGN-PHASE1-SELECTOR.md] 第 8 节。
 * 仅用户手动触发、仅当前屏幕，不自动上传。
 */
object NodeSnapshot {

    private const val MAX_NODES = 500
    private const val MAX_DEPTH = 30
    private const val MAX_TEXT_LEN = 40
    private const val MAX_TOTAL_BYTES = 100 * 1024

    data class Snapshot(
        val pkg: String,
        val ts: Long,
        val screen: String,
        val nodes: List<Node>,
        val truncated: Boolean = false,
    ) {
        fun toJson(): String {
            val root = JSONObject().apply {
                put("pkg", pkg)
                put("ts", ts)
                put("screen", screen)
                put("truncated", truncated)
                val arr = JSONArray()
                nodes.forEach { arr.put(it.toJson()) }
                put("nodes", arr)
            }
            return root.toString()
        }

        /**
         * 导出用文本：保证总字节 ≤ [MAX_TOTAL_BYTES]（DESIGN-PHASE1-SELECTOR 第 8 节「100KB 截断」）。
         *
         * 超过上限时从尾部裁掉节点，并把 `truncated` 标为 `true`。
         * 用二分查找「可容纳的最大节点前缀」，避免每次只丢一个节点的 O(n²) 重编码。
         */
        fun toShareText(): String {
            if (nodes.isEmpty()) return toJson()
            val full = toJson()
            if (full.toByteArray(Charsets.UTF_8).size <= MAX_TOTAL_BYTES) return full
            var lo = 0
            var hi = nodes.size
            var best = full
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                val candidate = Snapshot(pkg, ts, screen, nodes.take(mid), truncated = true).toJson()
                if (candidate.toByteArray(Charsets.UTF_8).size <= MAX_TOTAL_BYTES) {
                    best = candidate
                    lo = mid
                } else {
                    hi = mid - 1
                }
            }
            return best
        }
    }

    data class Node(
        val depth: Int,
        val cls: String,
        val text: String,
        val desc: String?,
        val vid: String?,
        val bounds: List<Int>,
        val flags: Flags,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("d", depth)
            put("cls", cls)
            put("text", text)
            put("desc", desc)
            put("vid", vid)
            put("b", JSONArray(bounds))
            put(
                "flags",
                JSONObject().apply {
                    put("click", flags.clickable)
                    put("vis", flags.visible)
                    put("edit", flags.editable)
                },
            )
        }
    }

    data class Flags(val clickable: Boolean, val visible: Boolean, val editable: Boolean)

    /**
     * 从当前活动窗口的根节点导出快照。
     *
     * @return Snapshot，节点超过上限或被截断时 truncated = true
     */
    fun capture(root: AccessibilityNodeInfo?, packageName: String, activityName: String): Snapshot {
        if (root == null) {
            return Snapshot(
                pkg = packageName,
                ts = System.currentTimeMillis(),
                screen = activityName,
                nodes = emptyList(),
                truncated = false,
            )
        }

        val nodes = ArrayList<Node>(MAX_NODES)
        var truncated = false

        fun dfs(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH || nodes.size >= MAX_NODES) {
                truncated = true
                return
            }

            val text = node.text?.toString()?.take(MAX_TEXT_LEN) ?: ""
            val desc = node.contentDescription?.toString()?.take(MAX_TEXT_LEN)
            val vid = node.viewIdResourceName
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val bounds = listOf(rect.left, rect.top, rect.right, rect.bottom)

            nodes.add(
                Node(
                    depth = depth,
                    cls = node.className?.toString() ?: "",
                    text = text,
                    desc = desc,
                    vid = vid,
                    bounds = bounds,
                    flags = Flags(
                        clickable = node.isClickable,
                        visible = node.isVisibleToUser,
                        editable = node.isEditable,
                    ),
                ),
            )

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                dfs(child, depth + 1)
                // minSdk = 26：自 Android 8 起 AccessibilityNodeInfo 由系统自动回收，
                // recycle() 已是 no-op 且被标记 deprecated，无需（也不应）手动调用。
                if (nodes.size >= MAX_NODES) {
                    truncated = true
                    return
                }
            }
        }

        dfs(root, 0)

        return Snapshot(
            pkg = packageName,
            ts = System.currentTimeMillis(),
            screen = activityName,
            nodes = nodes,
            truncated = truncated,
        )
    }
}
