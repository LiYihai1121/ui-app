package com.ldp.adskip.ui.theme

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
 * - [dynamicColor] 默认开启：Android 12+（API 31）跟随系统取色，低于该版本回退到品牌色板；
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
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
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
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private val LightStatusPalette = StatusPalette(
    on = StatusOn,
    onContainer = StatusOnContainer,
    off = StatusOff,
    offContainer = StatusOffContainer
)

private val DarkStatusPalette = StatusPalette(
    on = Color(0xFF7ED98A),
    onContainer = Color(0xFF1B4A22),
    off = Color(0xFFFFB4AB),
    offContainer = Color(0xFF5C1416)
)

/** 圆角尺度：卡片 20dp（比 M3 默认更柔和），控件 12dp，胶囊全圆。 */
private val AdskipShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

@Composable
fun AdskipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
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
        LocalStatusPalette provides if (darkTheme) DarkStatusPalette else LightStatusPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AdskipTypography,
            shapes = AdskipShapes,
            content = content
        )
    }
}

/** 便捷访问：服务运行中的前景 / 容器色。 */
object StatusColors {
    val on: Color
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current.on

    val onContainer: Color
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current.onContainer

    val off: Color
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current.off

    val offContainer: Color
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current.offContainer
}

@Immutable
data class StatusPalette(
    val on: Color,
    val onContainer: Color,
    val off: Color,
    val offContainer: Color
)

