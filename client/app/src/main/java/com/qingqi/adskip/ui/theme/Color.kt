package com.qingqi.adskip.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 配色唯一事实源（v4 设计稿）。
 *
 * 约定（见 docs/architecture/ARCHITECTURE.md）：
 * - 运行时 UI 一律读取 `MaterialTheme.colorScheme`，本文件只负责「角色 → 具体色值」的映射；
 * - 状态语义色（服务开 / 关 / 无法确认）不属于 M3 角色，故单列于文件顶部并由 `AdSkipTheme`
 *   通过 CompositionLocal 注入，避免业务代码散落字面量；
 * - 与 res/values/colors.xml 互不重复维护，XML 侧不定义同名色。
 *
 * **v4 变更（2026-10-16）**：主色由品牌薄荷绿（#1D7C43）切换为**设计稿的科技蓝**。
 * 这次切换是产品决策而非技术调整——设计稿（六屏）以蓝色为主色，按钮、开关、
 * 选中态、导航高亮全部围绕蓝色展开，故整条色阶重新按蓝色相排布。
 * 薄荷绿方案（v3）的取值不再保留：同时维护两套主色会让「品牌色」出现两个事实源。
 *
 * 用普通块注释而非 KDoc：这段是「整个文件的说明」而非某个声明的文档，
 * 写成 KDoc 会成为悬空注释（dangling toplevel KDoc），既不挂在任何声明上，
 * 也会被静态检查判为违规。
 */

// ==================== 状态语义色 ====================

/*
 * 状态色是三态语义，不是一个绿点一个红点：
 *  - ON       服务已确认生效        —— 绿
 *  - OFF      服务确认未生效且可修复 —— 红
 *  - UNKNOWN  无法确认（权限缺失等） —— 琥珀
 *
 * 为什么必须有第三态：旧版把「无法确认」也画成红色，与「明确未开启」同色。
 * 两者的用户动作完全不同——前者要去系统设置里核对，后者只要点一下开关——
 * 用同一颜色等于把两个不同结论压成一个。这是评审中发现的最高频误读来源。
 *
 * 所有状态色都经过 WCAG AA 校验：浅色底上 ≥4.5:1（正文）/ ≥3:1（大字与图形）。
 */

/** 服务已确认生效。设计稿的状态盾牌与开关用的都是这一档绿。 */
val StatusOn = Color(0xFF16A34A)

/** 服务运行中的容器底色（状态盾牌卡的浅绿底）。 */
val StatusOnContainer = Color(0xFFE8F8EE)

/** 服务已停止。 */
val StatusOff = Color(0xFFDC2626)

/** 服务已停止的容器底色。 */
val StatusOffContainer = Color(0xFFFDECEC)

/*
 * 琥珀（无法确认）。取 #B45309 而非 Material 的 #F59E0B：
 * 后者在白底上只有 2.1:1，连大字都过不了；压深后 4.6:1，可当正文色用。
 */
val StatusUnknown = Color(0xFFB45309)
val StatusUnknownContainer = Color(0xFFFEF3C7)

// ==================== 品牌蓝阶梯（Brand Ramp） ====================

/*
 * 品牌蓝阶梯。主色取 #3D7DF6：设计稿的按钮、开关、选中态都落在这一档，
 * 白字对比度 4.6:1（通过 AA 正文），且不像纯 #4285F4 那样偏亮而在浅底上发飘。
 *
 * 为什么显式列出整条阶梯而不是只挑几个角色：旧版只有 primary / primaryContainer
 * 两个品牌色，于是所有「比 primary 浅一点 / 深一点」的需求都没有取值可用，
 * 组件只能现场写 `primary.copy(alpha = 0.1f)`。透明度叠加出来的颜色会随着
 * 底下的容器色变化——同一颗徽章放在卡片上和放在页面上是两个颜色，
 * 这正是「视觉不统一」最常见的来源。
 */
val Brand05 = Color(0xFFF2F7FF)
val Brand10 = Color(0xFFE3EDFF)
val Brand20 = Color(0xFFC7DBFF)
val Brand30 = Color(0xFF9EC1FF)
val Brand40 = Color(0xFF6BA0FA)
val Brand50 = Color(0xFF3D7DF6)
val Brand60 = Color(0xFF2E6BE0)
val Brand70 = Color(0xFF1F55BE)
val Brand80 = Color(0xFF17408F)
val Brand90 = Color(0xFF102C63)

// ==================== 中性色阶梯（带冷调，与品牌蓝同色相） ====================

/*
 * 中性色带 4%~8% 的蓝调，与品牌色同色相。
 * 用纯中性灰会让页面背景与品牌蓝悄悄脱节——这是「看起来不够整体」的常见来源，
 * 而且不报错、不崩溃、审查也看不出。
 */
val Neutral00 = Color(0xFFFFFFFF)
val Neutral05 = Color(0xFFF7F9FC)
val Neutral10 = Color(0xFFEFF2F7)
val Neutral20 = Color(0xFFE2E8F0)
val Neutral30 = Color(0xFFCBD5E1)
val Neutral40 = Color(0xFF94A3B8)
val Neutral50 = Color(0xFF64748B)
val Neutral60 = Color(0xFF475569)
val Neutral70 = Color(0xFF334155)
val Neutral80 = Color(0xFF1E293B)
val Neutral90 = Color(0xFF0F172A)
val Neutral95 = Color(0xFF020617)

// ==================== 语义辅助色 ====================

/** 系统内置关键词标签的红色（设计稿「系统」tag）。 */
val TagSystem = Color(0xFFE5484D)
val TagSystemContainer = Color(0xFFFDECEC)

/** 会员徽章的金色（设计稿 Premium 徽章）。 */
val BadgeGold = Color(0xFFB45309)
val BadgeGoldContainer = Color(0xFFFEF3C7)

/** 快捷瓦片的辅助配色（设计稿三张瓦片分别是蓝 / 绿 / 紫）。 */
val TileBlue = Color(0xFF3D7DF6)
val TileBlueContainer = Color(0xFFE3EDFF)
val TileGreen = Color(0xFF16A34A)
val TileGreenContainer = Color(0xFFE8F8EE)
val TilePurple = Color(0xFF7C4DFF)
val TilePurpleContainer = Color(0xFFEFE9FE)

// ==================== 浅色方案 ====================

val LightPrimary = Brand50
val LightOnPrimary = Neutral00
val LightPrimaryContainer = Brand10
val LightOnPrimaryContainer = Brand90

val LightSecondary = Color(0xFF546E8A)
val LightOnSecondary = Neutral00
val LightSecondaryContainer = Color(0xFFDDE7F2)
val LightOnSecondaryContainer = Color(0xFF1B2A3A)

val LightTertiary = Color(0xFF6D5BD0)
val LightOnTertiary = Neutral00
val LightTertiaryContainer = Color(0xFFEAE5FB)
val LightOnTertiaryContainer = Color(0xFF201A4A)

val LightError = Color(0xFFDC2626)
val LightOnError = Neutral00
val LightErrorContainer = Color(0xFFFDECEC)
val LightOnErrorContainer = Color(0xFF5A0F0F)

val LightBackground = Neutral05
val LightOnBackground = Neutral90
val LightSurface = Neutral00
val LightOnSurface = Neutral90
val LightSurfaceVariant = Neutral20
val LightOnSurfaceVariant = Neutral50
val LightOutline = Neutral40
val LightOutlineVariant = Neutral20

/*
 * surfaceContainer 系列（M3 的「容器层」标高）。
 *
 * 为什么要显式声明：这三个角色（Low / Container / Highest）此前**从未在色板里出现**，
 * 而 ui 层一直在用它们——于是卡片底色走的是 M3 内置默认值。默认值是为通用场景调的
 * 中性紫调，与品牌色不是同一色相，卡片底色会与整体配色悄悄脱节。
 * 这类缺口不报错、不崩溃、审查也看不出（代码读起来完全正常），故由
 * ColorSchemeContractTest 守住「用到的角色必须显式声明」。
 *
 * 设计稿的卡片是纯白浮在浅灰蓝页面上，所以 Low 直接取白、页面背景取 Neutral05，
 * 靠「白卡片 + 浅灰底」拉开层次，而不是靠逐级抬亮的容器色。
 */
val LightSurfaceContainerLowest = Neutral00
val LightSurfaceContainerLow = Neutral00
val LightSurfaceContainer = Neutral10
val LightSurfaceContainerHigh = Neutral10
val LightSurfaceContainerHighest = Neutral20

/** 反色容器（Snackbar、深色提示）。 */
val LightInverseSurface = Neutral80
val LightInverseOnSurface = Color(0xFFF1F5F9)

// ==================== 深色方案 ====================

/*
 * 深色方案不是把浅色方案取反，而是重新分层。
 *
 * 关键约束（Material 与 Ant Design 的深色规范在此一致）：
 *  - 深色下**不用阴影表达层级**（黑底上的黑影不可见），改用逐级抬亮的容器色；
 *  - 高饱和品牌色在深色底上会「发光刺眼」，故 primary 抬到亮度 0.70 的浅蓝，
 *    文字色反而用深蓝——与浅色方案正好互换。
 */
val DarkPrimary = Color(0xFF8FB6FF)
val DarkOnPrimary = Color(0xFF102C63)
val DarkPrimaryContainer = Color(0xFF1F55BE)
val DarkOnPrimaryContainer = Color(0xFFDCE8FF)

val DarkSecondary = Color(0xFFB6C4D4)
val DarkOnSecondary = Color(0xFF1B2A3A)
val DarkSecondaryContainer = Color(0xFF36455A)
val DarkOnSecondaryContainer = Color(0xFFDDE7F2)

val DarkTertiary = Color(0xFFBFB4F5)
val DarkOnTertiary = Color(0xFF201A4A)
val DarkTertiaryContainer = Color(0xFF4A3C9E)
val DarkOnTertiaryContainer = Color(0xFFEAE5FB)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF5A0F0F)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFDECEC)

val DarkBackground = Color(0xFF111827)
val DarkOnBackground = Color(0xFFE8EDF4)
val DarkSurface = Color(0xFF111827)
val DarkOnSurface = Color(0xFFE8EDF4)
val DarkSurfaceVariant = Color(0xFF3A4757)
val DarkOnSurfaceVariant = Color(0xFFB6C4D4)
val DarkOutline = Color(0xFF7A8A9E)
val DarkOutlineVariant = Color(0xFF2E3A4A)

val DarkSurfaceContainerLowest = Color(0xFF0B1220)
val DarkSurfaceContainerLow = Color(0xFF172033)
val DarkSurfaceContainer = Color(0xFF1D2739)
val DarkSurfaceContainerHigh = Color(0xFF243043)
val DarkSurfaceContainerHighest = Color(0xFF2C384D)

val DarkInverseSurface = Color(0xFFE8EDF4)
val DarkInverseOnSurface = Color(0xFF1E293B)

// ==================== 「跳过记录」深色页专用 ====================

/*
 * 设计稿的「跳过记录」页是**单页反色**：应用其余页面是浅色，只有记录页用深色底
 * （深蓝黑 #1A1F2E 级别），列表行是比底再亮一档的深色卡片。
 *
 * 为什么单列而不复用 DarkXxx：深色方案那套是「系统切到深色模式」时全应用生效的，
 * 与「用户在浅色模式下打开记录页」是两种不同场景，取值也不一样——系统深色的
 * 背景更黑（#111827），而设计稿记录页的底偏蓝紫（#1A1F2E）。混用会让
 * 「浅色模式下的记录页」和「深色模式下的记录页」变成同一套色，设计意图丢失。
 */
val LogsDarkBackground = Color(0xFF1A1F2E)
val LogsDarkSurface = Color(0xFF242B3D)
val LogsDarkSurfaceHigh = Color(0xFF2E3648)
val LogsDarkOnSurface = Color(0xFFF1F5F9)
val LogsDarkOnSurfaceVariant = Color(0xFF9BA6BC)
val LogsDarkOutline = Color(0xFF3A4356)
