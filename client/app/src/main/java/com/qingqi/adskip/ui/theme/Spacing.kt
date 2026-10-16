package com.qingqi.adskip.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 间距标度（全 app 唯一）。
 *
 * 背景：此前 `ui/` 下出现过 2 / 4 / 6 / 8 / 12 / 16 / 20 / 40 / 48 / 56 / 108 / 150 /
 * 300 / 640 共十余种 dp 字面量，其中 2 与 6 脱离 4 的倍数体系、20 既不像间距又不像
 * 组件内边距。Ant Design 明确禁止「在组件外部用 margin 制造间距」，Tailwind 与
 * M3 的做法都是先有标度再取用。本文件把标度固化，页面与组件只允许引用这里的名字。
 *
 * 取值说明：全部为 4 的倍数（Tailwind 的 4px 栅格同源）。标度比旧版多出 `lg2`(20) 与
 * `xxxl`(40) 两档——旧版把 20dp 判为「既不像间距又不像内边距」而拒绝收录，
 * 结果是页面里 20dp 以字面量形式散落了十几处。**标度缺档不会让那个值消失，
 * 只会让它以字面量形式失控**；补进标度反而能统一管理。
 */
object Spacing {
    /** 4dp：紧贴的元素间距（如两行文字之间） */
    val xs = 4.dp

    /** 8dp：同一分组内的元素间距 */
    val sm = 8.dp

    /** 12dp：组件内元素与分隔线之间 */
    val md = 12.dp

    /** 16dp：内容区之间的标准间距（页面级默认） */
    val lg = 16.dp

    /** 20dp：卡片内边距、列表行纵向内边距 */
    val lg2 = 20.dp

    /** 24dp：区块之间的间距、页面底部留白 */
    val xl = 24.dp

    /** 32dp：大块留白（如主视觉卡的内边距） */
    val xxl = 32.dp

    /** 40dp：空态与正文之间的疏朗留白 */
    val xxxl = 40.dp

    /** 48dp：页面级首屏留白（主视觉区之上） */
    val huge = 48.dp
}

/**
 * 固定尺寸常量。
 *
 * 与 [Spacing] 分开：这些不是「间距」，而是组件/布局的固定边长，混进间距标度会
 * 让人误以为 48dp 可以拿来当间距用。
 */
object UiSizes {
    /** 列表行图标边长 */
    val listIcon = 40.dp

    /** 空态占位图标边长 */
    val emptyStateIcon = 48.dp

    /** 可点击元素的最小触控目标边长（Material 无障碍基线） */
    val touchTarget = 48.dp

    /** 行内次要图标边长（日志行状态点等） */
    val inlineIcon = 18.dp

    /** 设置行/链接行的主图标边长（M3 列表行图标的标准尺寸） */
    val rowIcon = 24.dp

    /** 图标底衬（图标背板）边长 */
    val iconPlate = 36.dp

    /** 首页状态环直径（紧凑态） */
    val statusOrb = 56.dp

    /** 首页主视觉状态环直径 */
    val statusOrbHero = 108.dp

    /** 状态环外层光晕直径 */
    val orbHalo = 96.dp

    /** 骨架屏列表行的默认高度 */
    val skeletonRow = 60.dp

    /** 文本占位条的默认高度 */
    val skeletonLine = 14.dp

    /** 文本占位条的紧凑高度 */
    val skeletonLineCompact = 10.dp

    /**
     * 空态 / 错误态插图的外圈直径。
     *
     * 72dp 是 Material 与 Ant Design 空态插图的共同量级（48~80dp），
     * 它比列表图标大但不到整屏，正好在视觉上「占住空态的位置」而不显得空旷。
     */
    val stateIllustration = 72.dp

    /** 空态插图内的图标边长（外圈直径的一半少一点，留出呼吸边距） */
    val stateIllustrationIcon = 32.dp

    /**
     * 图标底衬内的图标边长。
     *
     * 底衬 36dp、内图标 20dp 是一组固定搭配：24dp 图标放进 36dp 底衬只剩 6dp 边距，
     * 视觉上过于紧贴；20dp 是 Material 的「小图标」档，配 36dp 底衬边距 8dp，比例稳定。
     */
    val iconPlateGlyph = 20.dp

    /**
     * 细描边宽度（分隔线、空心圆环）。
     *
     * 1dp 是密度无关的**最小可见描边**——低于它在 xhdpi 以下屏幕会因抗锯齿变成灰雾。
     * 这类「结构线」与间距是两件事，故列入本对象而非标度。
     */
    val hairline = 1.dp

    /** 状态点直径（行内静态标记）。 */
    val statusDot = 10.dp

    /** 状态点空心时的环宽：1.5dp 在 10dp 直径上才能看清「空心」而不是「脏点」。 */
    val statusDotRing = 1.5.dp

    /** 页面内容最大宽度（宽屏下封顶居中，见 MainActivity） */
    val contentMaxWidth = 640.dp

    /** 顶栏高度（页面标题位于顶栏时的滚动容器基准） */
    val topBar = 64.dp

    /*
     * ---- 以下为 v4 设计稿新增 ----
     *
     * 设计稿里出现了一批新的固定边长（应用图标、头像、图标底衬、搜索框等）。
     * 它们全部收口到这里而不是写在页面里：`UiContractTest` 只允许间距取标度内的值，
     * 页面里直接写 `44.dp` 会被判为越标度；而它们本就**不是间距**，
     * 塞进 Spacing 标度又会让人误以为 44dp 可以拿来当间距用。
     */

    /** 页面头部 / 列表中的应用图标边长（圆角方形）。 */
    val appIcon = 44.dp

    /** 用户头像边长（圆形）。 */
    val avatar = 48.dp

    /** 首页状态盾牌的外圈直径（设计稿的主视觉尺寸）。 */
    val statusShield = 120.dp

    /**
     * 状态盾牌内圈直径。
     *
     * 与外圈的 120dp 差 8dp，即两侧各 4dp 的环宽——用「两个同心圆叠放」而不是
     * `Modifier.border` 画环，是为了避免再引入一个描边宽度字面量（间距门禁只放行
     * 0/4/8/12/16/24/32/40 的 dp 取值，而环宽 4dp 恰好可以转述为直径之差）。
     */
    val statusShieldRing = 112.dp

    /** 状态盾牌内圈的图标边长。 */
    val statusShieldGlyph = 52.dp

    /** 快捷入口瓦片的图标底衬边长（圆角方形）。 */
    val tileIcon = 48.dp

    /** 快捷入口瓦片内的图标边长。 */
    val tileGlyph = 24.dp

    /** 搜索框高度。 */
    val searchField = 44.dp

    /** 列表行尾的指示符边长（chevron / 尾部小图标）。 */
    val chevron = 20.dp

    /** 分段标签（SegmentedTabs）内单颗胶囊的高度。 */
    val segmentHeight = 36.dp

    /**
     * 卡片阴影高度。
     *
     * 设计稿的卡片是「白卡 + 极淡投影」浮在浅灰蓝页面上。M3 自 2023 年起默认
     * **不用** elevation 表达层级（改走容器色），但设计稿明确带投影，故恢复一档
     * 最轻的 elevation：2dp 在浅色底上刚好看得见轮廓，再重就会显脏。
     * 深色模式下由组件侧跳过（黑底上的黑影不可见，只会变成一圈灰边）。
     */
    val cardElevation = 2.dp

    /** 引导页插图区域高度。 */
    val illustrationHeight = 200.dp

    /** 引导页插图中「设备」的宽 / 高。 */
    val illustrationBackWidth = 132.dp
    val illustrationBackHeight = 176.dp

    /** 引导页页码点：当前页与其余页的直径。 */
    val dotActive = 8.dp
    val dotIdle = 6.dp
}

/**
 * 大屏内容封顶 + 居中。
 *
 * 为什么需要它（三家的规范在这里是同一个结论）：
 * - Material：`WindowSizeClass` 提示 expanded 宽度下应改用 NavigationRail / ListDetail；
 * - Ant Design：栅格 + Container 居中 + max-width 封顶；
 * - Tailwind：`max-w-*` 居中 + 断点。
 *
 * 本应用是单列信息流，不需要栅格分栏，但**必须封顶**：否则平板、折叠屏展开态与横屏下
 * 卡片会被拉成整屏宽的条带，一行文字横跨上千 dp，阅读时视线回到行首的行程过长。
 *
 * 放在 `Spacing.kt` 而不是某个屏幕里：它是尺寸体系的一部分，且本文件是间距/尺寸门禁
 * 唯一豁免的地方——屏幕侧若自己写 `widthIn(max = 640.dp)`，会被 `UiContractTest`
 * 判为越标度字面量（640 不在间距标度上）。收口在这里，屏幕只表达「我要封顶」。
 *
 * 返回 `fillMaxWidth()` 而非 `fillMaxSize()`：宽度封顶 + 水平居中，
 * 纵向留给调用方决定（NavHost 需要占满高度来承载滚动）。
 */
fun Modifier.screenContentWidth(): Modifier = this
    .fillMaxWidth()
    .widthIn(max = UiSizes.contentMaxWidth)
