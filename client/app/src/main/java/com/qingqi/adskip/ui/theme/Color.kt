package com.qingqi.adskip.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 配色唯一事实源。
 *
 * 约定（见 docs/architecture/ARCHITECTURE.md）：
 * - 运行时 UI 一律读取 `MaterialTheme.colorScheme`，本文件只负责「角色 → 具体色值」的映射；
 * - 状态语义色（服务开 / 关）不属于 M3 角色，故单列于文件顶部并由 `AdSkipTheme`
 *   通过 CompositionLocal 注入，避免业务代码散落字面量；
 * - 与 res/values/colors.xml 互不重复维护，XML 侧不定义同名色。
 *
 * 用普通块注释而非 KDoc：这段是「整个文件的说明」而非某个声明的文档，
 * 写成 KDoc 会成为悬空注释（dangling toplevel KDoc），既不挂在任何声明上，
 * 也会被静态检查判为违规。
 */

// ==================== 状态语义色 ====================

/** 服务运行中 */
val StatusOn = Color(0xFF2E7D32)

/** 服务运行中的容器底色 */
val StatusOnContainer = Color(0xFFC8F0CB)

/** 服务已停止 */
val StatusOff = Color(0xFFC62828)

/** 服务已停止的容器底色 */
val StatusOffContainer = Color(0xFFFFDAD6)

// ==================== 浅色方案 ====================

val LightPrimary = Color(0xFF1565C0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD6E4FF)
val LightOnPrimaryContainer = Color(0xFF001B41)

val LightSecondary = Color(0xFF4F5B6E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD3E4FF)
val LightOnSecondaryContainer = Color(0xFF0B1B2C)

val LightTertiary = Color(0xFF00696E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF9CF1F6)
val LightOnTertiaryContainer = Color(0xFF002022)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFFDFBFF)
val LightOnBackground = Color(0xFF1A1B20)
val LightSurface = Color(0xFFFDFBFF)
val LightOnSurface = Color(0xFF1A1B20)
val LightSurfaceVariant = Color(0xFFE0E2EC)
val LightOnSurfaceVariant = Color(0xFF44474E)
val LightOutline = Color(0xFF74777F)
val LightOutlineVariant = Color(0xFFC4C6D0)

/*
 * surfaceContainer 系列（M3 的「容器层」标高）。
 *
 * 为什么要显式声明：这三个角色（Low / Container / Highest）此前**从未在色板里出现**，
 * 而 ui 层一直在用它们——于是卡片底色走的是 M3 内置默认值。默认值是为通用场景调的
 * 紫调中性色，与上面的品牌蓝不是同一色相，卡片底色会与整体配色悄悄脱节。
 * 这类缺口不报错、不崩溃、审查也看不出（代码读起来完全正常），故由
 * ColorSchemeContractTest 守住「用到的角色必须显式声明」。
 *
 * 取值沿用同一套蓝调中性：越低的层越接近背景，越高的层越靠近 surfaceVariant。
 */
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF6F7FC)
val LightSurfaceContainer = Color(0xFFF0F1F8)
val LightSurfaceContainerHigh = Color(0xFFEAEBF3)
val LightSurfaceContainerHighest = Color(0xFFE4E5EE)

// ==================== 深色方案 ====================

val DarkPrimary = Color(0xFFA8C8FF)
val DarkOnPrimary = Color(0xFF00315D)
val DarkPrimaryContainer = Color(0xFF004881)
val DarkOnPrimaryContainer = Color(0xFFD6E4FF)

val DarkSecondary = Color(0xFFB7C8E6)
val DarkOnSecondary = Color(0xFF223047)
val DarkSecondaryContainer = Color(0xFF384757)
val DarkOnSecondaryContainer = Color(0xFFD3E4FF)

val DarkTertiary = Color(0xFF80D4DA)
val DarkOnTertiary = Color(0xFF00363A)
val DarkTertiaryContainer = Color(0xFF004F53)
val DarkOnTertiaryContainer = Color(0xFF9CF1F6)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF121316)
val DarkOnBackground = Color(0xFFE3E2E9)
val DarkSurface = Color(0xFF121316)
val DarkOnSurface = Color(0xFFE3E2E9)
val DarkSurfaceVariant = Color(0xFF44474E)
val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
val DarkOutline = Color(0xFF8E9099)
val DarkOutlineVariant = Color(0xFF44474E)

/* surfaceContainer 系列深色取值：与浅色同样的蓝调中性，越低的层越接近深色背景。 */
val DarkSurfaceContainerLowest = Color(0xFF0D0E11)
val DarkSurfaceContainerLow = Color(0xFF1A1B20)
val DarkSurfaceContainer = Color(0xFF1F2025)
val DarkSurfaceContainerHigh = Color(0xFF292A2F)
val DarkSurfaceContainerHighest = Color(0xFF34353A)
