package com.ldp.adskip.engine

/**
 * JVM 测试用假节点：不依赖 Android 框架。
 *
 * 可模拟任意节点树结构，用于引擎单测。
 */
class FakeAdNode(
    override val text: String? = null,
    override val desc: String? = null,
    override val viewId: String? = null,
    override val isVisible: Boolean = true,
    override val isClickable: Boolean = false,
    override val isEditable: Boolean = false,
    private val childList: List<FakeAdNode> = emptyList(),
    private val w: Int = 100,
    private val h: Int = 100,
    private val cx: Float = 50f,
    private val cy: Float = 50f,
    private val clickResult: Boolean = true
) : AdNode {

    override fun children(): List<AdNode> = childList.toList()
    override fun clickableParent(): AdNode? = null
    override fun centerX(): Float = cx
    override fun centerY(): Float = cy
    override fun boundsWidth(): Int = w
    override fun boundsHeight(): Int = h
    override fun click(): Boolean = clickResult

    /** DSL 构造器 */
    companion object {
        fun node(
            text: String? = null,
            desc: String? = null,
            viewId: String? = null,
            visible: Boolean = true,
            clickable: Boolean = false,
            editable: Boolean = false,
            width: Int = 100,
            height: Int = 100,
            clickResult: Boolean = true,
            children: List<FakeAdNode> = emptyList()
        ) = FakeAdNode(text, desc, viewId, visible, clickable, editable, children, width, height, 50f, 50f, clickResult)
    }
}
