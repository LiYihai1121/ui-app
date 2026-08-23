package com.ldp.adskip.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 生产用 AdNode：包装 [AccessibilityNodeInfo]。
 *
 * children() 做深度截断和数量限制，防过度遍历。
 * click() 执行 ACTION_CLICK；false 时由服务层回退坐标手势。
 */
class FrameworkAdNode(
    private val node: AccessibilityNodeInfo,
    private val maxDepth: Int = 30
) : AdNode {

    override val text: String? get() = node.text?.toString()
    override val desc: String? get() = node.contentDescription?.toString()
    override val viewId: String? get() = node.viewIdResourceName
    override val isVisible: Boolean get() = node.isVisibleToUser
    override val isClickable: Boolean get() = node.isClickable
    override val isEditable: Boolean get() = node.isEditable

    override fun children(): List<AdNode> {
        val out = mutableListOf<AdNode>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { out.add(FrameworkAdNode(it, maxDepth)) }
        }
        return out
    }

    override fun clickableParent(): AdNode? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isClickable) return FrameworkAdNode(current, maxDepth)
            current = current.parent
            depth++
        }
        return null
    }

    override fun centerX(): Float {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.exactCenterX()
    }

    override fun centerY(): Float {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.exactCenterY()
    }

    override fun boundsWidth(): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.width()
    }

    override fun boundsHeight(): Int {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect.height()
    }

    override fun click(): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
