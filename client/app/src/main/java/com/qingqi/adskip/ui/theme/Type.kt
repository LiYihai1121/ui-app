package com.qingqi.adskip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 全局字阶。
 *
 * 这是一次针对**中文界面**的字阶重校。旧版的问题不在「用的是不是 M3 角色」，
 * 而在两个被忽略的度量：
 *
 * 1. **字重**。旧版标题统一 SemiBold(600)、正文 Normal(400)。但 Android 的
 *    `FontFamily.Default` 在中文字形上落到 Noto Sans CJK，该字族的 600 与 700
 *    在中文下的字面宽度差极小、笔画却明显变粗，结果「标题 SemiBold」在中文里
 *    既不够突出（与正文对比不足）又糊（笔画粘连）。中文标题需要的是**更大的字号**
 *    与**更大的字重跨度**，而不是在 600 这一档上反复微调。
 *    故正文降到 400 保持 Regular，标题统一到 Bold(700) 拉开对比。
 *
 * 2. **行高**。旧版标题行高收到 1.35 倍，对拉丁字母合适，对中文偏挤——
 *    中文字面是满框方块，行距不足时上下行笔画会「顶」在一起。中文正文舒适区是
 *    1.6~1.75，标题可略收但仍需 ≥1.4。本文件按此重排。
 *
 * 另有两处刻意决定：
 * - `letterSpacing` 在**中文主体**上取 0：中文不需要字距，加字距只会让词间距失衡。
 *   旧版给 bodyLarge 加了 0.15sp、bodyMedium 加了 0.2sp，那是为拉丁文设计的做法，
 *   在中文下会让每个字之间出现可见缝隙，破坏方块字的密排节奏。
 * - 保留 `lineHeightStyle` 的 Center 对齐：中英文混排时首行基线才与图标垂直居中。
 */
private val LineHeightCentered = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AdSkipTypography = Typography(
    // ---- Display：全屏级数字（跳过总数等）。中文标题很少用到，主要为数字准备。 ----
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 62.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = LineHeightCentered,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.25).sp,
        lineHeightStyle = LineHeightCentered,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // ---- Headline：页面主标题 ----
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 42.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // headlineMedium 为首页状态结论与页面标题使用的档位。
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // headlineSmall 用于次级页面标题。
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // ---- Title：区块标题、列表主行 ----
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // titleMedium 为卡片小节标题（SectionTitle）使用的档位。
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // ---- Body：正文。行高压到 1.6~1.75，letterSpacing 归零（中文密排） ----
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    // ---- Label：按钮、导航标签、徽章。字重要够，它们在大字号背景下反而更小 ----
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = LineHeightCentered,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        lineHeightStyle = LineHeightCentered,
    ),
)
