package com.ldp.adskip.engine

/**
 * 节点抽象：引擎只认这个接口，不依赖 [android.view.accessibility.AccessibilityNodeInfo]。
 *
 * 设计原则：接口中不使用 Android 框架类型（Rect/Point），
 * 使引擎可跑纯 JVM 单测（用 [FakeAdNode] 模拟节点树）。
 * 生产实现 [FrameworkAdNode] 包装 AccessibilityNodeInfo。
 */
interface AdNode {
    val text: String?
    val desc: String?
    val viewId: String?
    val isVisible: Boolean
    val isClickable: Boolean
    val isEditable: Boolean

    /** 子节点列表（实现可做深度/数量截断） */
    fun children(): List<AdNode>

    /** 沿父链找可点击节点 */
    fun clickableParent(): AdNode?

    /** 屏幕中心 X 坐标（用于坐标手势兜底） */
    fun centerX(): Float

    /** 屏幕中心 Y 坐标（用于坐标手势兜底） */
    fun centerY(): Float

    /** 屏幕宽度（合法性校验用） */
    fun boundsWidth(): Int

    /** 屏幕高度（合法性校验用） */
    fun boundsHeight(): Int

    /** 执行点击：ACTION_CLICK；false 时由服务层回退坐标手势 */
    fun click(): Boolean
}
