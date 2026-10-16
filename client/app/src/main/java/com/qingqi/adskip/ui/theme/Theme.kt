package com.qingqi.adskip.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 应用主题。
 *
 * - [dynamicColor] 默认关闭（见下方 [AdSkipTheme] 的取值与理由）：品牌敏感产品优先保配色一致，
 *   低于 Android 12 时本就没有动态取色能力，两种情况下都回退到品牌色板；
 * - 状态语义色（服务开 / 关 / 无法确认）不参与动态取色——它们承载的是「是否生效」这一安全语义，
 *   必须保持稳定可辨，故通过 [LocalStatusPalette] 单独注入。
 */

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightStatusPalette = StatusPalette(
    on = StatusOn,
    onContainer = StatusOnContainer,
    off = StatusOff,
    offContainer = StatusOffContainer,
    unknown = StatusUnknown,
    unknownContainer = StatusUnknownContainer,
)

private val DarkStatusPalette = StatusPalette(
    on = Color(0xFF7ED98A),
    onContainer = Color(0xFF1B4A22),
    off = Color(0xFFFFB4AB),
    offContainer = Color(0xFF5C1416),
    unknown = Color(0xFFFFC46B),
    unknownContainer = Color(0xFF4A3200),
)

/*
 * 圆角尺度（Shape Scale）。
 *
 * 旧版把「卡片 20dp / 控件 12dp」直接写成 M3 槽位映射，问题出在**槽位语义被挪用**：
 * M3 的 `large` 在组件库里的默认消费者是 BottomSheet（28dp 级），`medium` 是卡片，
 * 而这里把 `medium` 当成小控件、`large` 当成卡片，后面任何按角色取形状的组件
 * （Dialog、Menu、SearchBar）都会拿到不符合预期的圆角。
 *
 * 现在改为「先按尺寸排梯队，再由组件按语义挑」：
 *   xsmall 6  → Chip / 徽章 / 骨架条
 *   small  10 → 输入框 / 小按钮
 *   medium 16 → 卡片 / 列表容器（默认）
 *   large  20 → 主视觉卡 / 底部弹层容器
 *   xlarge 28 → 全屏 Dialog
 *
 * 数值本身贴合大厂移动端惯例（Ant Design Mobile 卡片 12、Material 3 卡片 12/16），
 * v4 设计稿的卡片圆角实测在 16 上下（比常见的 12 更「软」一档），故 medium 由 14 提到 16，
 * 与设计稿对齐。再大就会回到「圆润玩具感」，信息密集的管理类应用需要克制。
 */
private val AdSkipShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.XS),
    small = RoundedCornerShape(Radii.SM),
    medium = RoundedCornerShape(Radii.MD),
    large = RoundedCornerShape(Radii.LG),
    extraLarge = RoundedCornerShape(Radii.XL),
)

/**
 * 圆角常量（不叫 Spacing：它不是间距，混进间距标度会被误当间距用）。
 *
 * 命名注意：**不能叫 `Shapes`**——本文件已 `import androidx.compose.material3.Shapes`
 * 用于构造 [AdSkipShapes]，同名 object 会让 `Shapes.XS` 解析到 M3 的 `Shapes`
 * 类（其无 `XS` 成员）而编译失败。曾因此让整仓无法构建。
 *
 * 放在 object 里而不是散落字面量：`UiContractTest` 只允许间距取标度内的值，
 * 圆角走这里才不会被误判；同时让「卡片圆角」在全项目只有一处定义。
 */
private object Radii {
    val XS = 6.dp
    val SM = 10.dp
    val MD = 16.dp
    val LG = 20.dp
    val XL = 28.dp
}

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

/**
 * 应用主题。
 *
 * [dynamicColor] 默认 **false**，这是一次有代价的取舍，记录理由以便日后复核：
 *
 * 开启壁纸取色时（Android 12+），`Color.kt` 里维护的品牌蓝 #3D7DF6 与整套 M3 明暗
 * 方案会被整体绕过。于是一次运行中会同时存在三套互相独立的色彩来源：
 *  - 应用底色 = 壁纸取色（colorScheme）
 *  - 启动器图标 = 固定品牌渐变（不参与主题）
 *  - 状态语义色 = 固定绿/红/琥珀（LocalStatusPalette，同样不参与主题）
 * 换一张紫色壁纸，就会得到「紫色应用 + 蓝色图标 + 绿/红状态点」的三色割裂。
 *
 * Material 把动态色定位为**可选的个性化增强**，品牌敏感产品通常关闭；
 * 本应用刚完成改名与品牌重建，图标与配色是它最外露的识别物，因此以品牌一致性优先。
 * 参数保留：若日后希望跟随壁纸，只需把它改回 true 即可，无需改动任何调用方。
 */
@Composable
fun AdSkipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalStatusPalette provides if (darkTheme) DarkStatusPalette else LightStatusPalette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AdSkipTypography,
            shapes = AdSkipShapes,
            content = content,
        )
    }
}

/**
 * 便捷访问：服务状态的三态前景 / 容器色。
 *
 * 用 object + @Composable getter 而不是直接暴露 [LocalStatusPalette]：
 * 调用处写 `StatusColors.on` 是一句明确的话，「服务生效了」；
 * 写 `LocalStatusPalette.current.on` 则是把实现细节摊在业务代码里。
 */
object StatusColors {
    val on: Color
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current.on

    val onContainer: Color
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current.onContainer

    val off: Color
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current.off

    val offContainer: Color
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current.offContainer

    val unknown: Color
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current.unknown

    val unknownContainer: Color
        @Composable @ReadOnlyComposable
        get() = LocalStatusPalette.current.unknownContainer
}

@Immutable
data class StatusPalette(
    val on: Color,
    val onContainer: Color,
    val off: Color,
    val offContainer: Color,
    val unknown: Color,
    val unknownContainer: Color,
)
