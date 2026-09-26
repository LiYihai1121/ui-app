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

    private var parentNode: FakeAdNode? = null

    init {
        // 构造时回填父子关系（children 先于 parent 构造，由父节点 init 统一接线）
        childList.forEach { it.parentNode = this }
    }

    override val parent: AdNode? get() = parentNode

    override fun previousSibling(): AdNode? {
        val p = parentNode ?: return null
        val index = p.childList.indexOf(this) // FakeAdNode 未覆写 equals → 引用相等
        return if (index > 0) p.childList[index - 1] else null
    }

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
