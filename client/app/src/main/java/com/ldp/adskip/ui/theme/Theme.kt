package com.ldp.adskip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色板（延续 v2.x 视觉）
// UI 配色的唯一事实源：运行时一律走 MaterialTheme.colorScheme；
// 下面的字面量仅供状态强调等无法走 colorScheme 的场景使用，且与 res/values/colors.xml 互不重复维护。
val BrandBlue = Color(0xFF1565C0)
val StatusOn = Color(0xFF2E7D32)
val StatusOff = Color(0xFFC62828)
val TextPrimary = Color(0xFF1F2937)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B41),
    secondary = Color(0xFF565F71),
    surface = Color(0xFFFBF8FD),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurface = TextPrimary,
    onSurfaceVariant = Color(0xFF44474E),
    error = StatusOff,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00478D),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFBEC6DC),
    onSurface = Color(0xFFE3E2E9),
)

@Composable
fun AdskipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
