package com.ldp.adskip.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距标度（全 app 唯一）。
 *
 * 背景：此前 `ui/` 下出现过 2 / 4 / 6 / 8 / 12 / 16 / 20 / 40 / 48 / 56 / 108 / 150 /
 * 300 / 640 共十余种 dp 字面量，其中 2 与 6 脱离 4 的倍数体系、20 既不像间距又不像
 * 组件内边距。Ant Design 明确禁止「在组件外部用 margin 制造间距」，Tailwind 与
 * M3 的做法都是先有标度再取用。本文件把标度固化，页面与组件只允许引用这里的名字。
 *
 * 取值说明：全部为 4 的倍数（Tailwind 的 4px 栅格同源）；40 / 48 / 56 之所以在标度
 * 内而不是当例外，是因为它们分别对应「列表图标」「触控目标最小边长」「状态环直径」
 * 这三个反复出现的固定尺寸——写成标度成员比散落字面量更容易保持一致。
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

    /** 24dp：区块之间的间距、页面底部留白 */
    val xl = 24.dp

    /** 32dp：大块留白（如表单字段之间） */
    val xxl = 32.dp

    /** 40dp：空态与正文之间的疏朗留白 */
    val xxxl = 40.dp
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

    /** 可点击元素的最小触控目标边长（Material 无障碍基线） */
    val touchTarget = 48.dp

    /** 首页状态环直径 */
    val statusOrb = 56.dp

    /** 页面内容最大宽度（宽屏下封顶居中，见 MainActivity） */
    val contentMaxWidth = 640.dp
}
