package com.qingqi.adskip.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 配色唯一事实源。
 *
 * 约定（由 ColorSchemeContractTest 守护）：
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

/*
 * 品牌色 = 薄荷绿 #57E08E（来自 2026-10 设计稿「轻羽掠影」，即启动器图标底色）。
 *
 * #57E08E 本身明度太高（相对亮度 0.57，白字对比度仅 1.7:1），不能直接当
 * 界面主色用。故以下色值全部固定在同一色相（HSL 144°）上分层：
 *  - 需要承载白字的角色（primary / secondary）压到亮度 0.30 左右，白字对比度 ≥5:1；
 *  - 容器类角色抬高到亮度 0.90 左右，配深绿前景字；
 *  - 深色方案反过来：primary 抬到亮度 0.70 的浅薄荷，配深绿字。
 * 图标仍用原样 #57E08E（见 drawable/ic_launcher_background.xml），那是品牌标识而非界面色。
 *
 * 中性色（background / surface / surfaceContainer*）同样带 6%~14% 的绿调，
 * 与品牌色同色相；用中性灰会让卡片底色与品牌色悄悄脱节。
 */

// ==================== 浅色方案 ====================

val LightPrimary = Color(0xFF1D7C43)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD6F5E2)
val LightOnPrimaryContainer = Color(0xFF0A381D)

val LightSecondary = Color(0xFF455F4F)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDAE7DF)
val LightOnSecondaryContainer = Color(0xFF1B3224)

val LightTertiary = Color(0xFF00696E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF9CF1F6)
val LightOnTertiaryContainer = Color(0xFF002022)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF8F9F8)
val LightOnBackground = Color(0xFF171C19)
val LightSurface = Color(0xFFF8F9F8)
val LightOnSurface = Color(0xFF171C19)
val LightSurfaceVariant = Color(0xFFE2E9E5)
val LightOnSurfaceVariant = Color(0xFF424D46)
val LightOutline = Color(0xFF717F76)
val LightOutlineVariant = Color(0xFFC7D1CB)

/*
 * surfaceContainer 系列（M3 的「容器层」标高）。
 *
 * 为什么要显式声明：这三个角色（Low / Container / Highest）此前**从未在色板里出现**，
 * 而 ui 层一直在用它们——于是卡片底色走的是 M3 内置默认值。默认值是为通用场景调的
 * 紫调中性色，与上面的品牌绿不是同一色相，卡片底色会与整体配色悄悄脱节。
 * 这类缺口不报错、不崩溃、审查也看不出（代码读起来完全正常），故由
 * ColorSchemeContractTest 守住「用到的角色必须显式声明」。
 *
 * 取值沿用同一套绿调中性：越低的层越接近背景，越高的层越靠近 surfaceVariant。
 */
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF5F7F6)
val LightSurfaceContainer = Color(0xFFEFF3F1)
val LightSurfaceContainerHigh = Color(0xFFE9EFEB)
val LightSurfaceContainerHighest = Color(0xFFE3EAE6)

// ==================== 深色方案 ====================

val DarkPrimary = Color(0xFF88DDAA)
val DarkOnPrimary = Color(0xFF0C4523)
val DarkPrimaryContainer = Color(0xFF225938)
val DarkOnPrimaryContainer = Color(0xFFD3EEDE)

val DarkSecondary = Color(0xFFAEC2B6)
val DarkOnSecondary = Color(0xFF1B3726)
val DarkSecondaryContainer = Color(0xFF35463C)
val DarkOnSecondaryContainer = Color(0xFFD5E2DA)

val DarkTertiary = Color(0xFF80D4DA)
val DarkOnTertiary = Color(0xFF00363A)
val DarkTertiaryContainer = Color(0xFF004F53)
val DarkOnTertiaryContainer = Color(0xFF9CF1F6)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF111513)
val DarkOnBackground = Color(0xFFDFE2E0)
val DarkSurface = Color(0xFF111513)
val DarkOnSurface = Color(0xFFDFE2E0)
val DarkSurfaceVariant = Color(0xFF424D46)
val DarkOnSurfaceVariant = Color(0xFFC2CBC6)
val DarkOutline = Color(0xFF85938B)
val DarkOutlineVariant = Color(0xFF424D46)

/* surfaceContainer 系列深色取值：与浅色同样的绿调中性，越低的层越接近深色背景。 */
val DarkSurfaceContainerLowest = Color(0xFF0D0F0E)
val DarkSurfaceContainerLow = Color(0xFF181D1A)
val DarkSurfaceContainer = Color(0xFF1E2421)
val DarkSurfaceContainerHigh = Color(0xFF272F2B)
val DarkSurfaceContainerHighest = Color(0xFF313A35)
