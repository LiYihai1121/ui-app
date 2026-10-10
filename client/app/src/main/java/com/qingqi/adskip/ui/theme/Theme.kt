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
 * - 状态语义色（服务开 / 关）不参与动态取色——它们承载的是「是否生效」这一安全语义，
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
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightStatusPalette = StatusPalette(
    on = StatusOn,
    onContainer = StatusOnContainer,
    off = StatusOff,
    offContainer = StatusOffContainer,
)

private val DarkStatusPalette = StatusPalette(
    on = Color(0xFF7ED98A),
    onContainer = Color(0xFF1B4A22),
    off = Color(0xFFFFB4AB),
    offContainer = Color(0xFF5C1416),
)

/** 圆角尺度：卡片 20dp（比 M3 默认更柔和），控件 12dp，胶囊全圆。 */
private val AdSkipShapes = Shapes(
    extraSmall = RoundedCornerShape(Spacing.sm),
    small = RoundedCornerShape(Spacing.md),
    medium = RoundedCornerShape(Spacing.lg),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

/**
 * 应用主题。
 *
 * [dynamicColor] 默认 **false**，这是一次有代价的取舍，记录理由以便日后复核：
 *
 * 开启壁纸取色时（Android 12+），`Color.kt` 里维护的品牌蓝 #1565C0 与整套 M3 明暗
 * 方案会被整体绕过。于是一次运行中会同时存在三套互相独立的色彩来源：
 *  - 应用底色 = 壁纸取色（colorScheme）
 *  - 启动器图标 = 固定品牌蓝渐变（不参与主题）
 *  - 状态语义色 = 固定绿/红（LocalStatusPalette，同样不参与主题）
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

/** 便捷访问：服务运行中的前景 / 容器色。 */
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
}

@Immutable
data class StatusPalette(val on: Color, val onContainer: Color, val off: Color, val offContainer: Color)
