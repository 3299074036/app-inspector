package dev.appinspector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------- 品牌色：深邃蓝 ----------
private val BrandBlue = Color(0xFF1B6EF3)
private val BrandBlueDark = Color(0xFF7AA5FF)
private val BrandContainer = Color(0xFFDBE7FF)
private val BrandContainerDark = Color(0xFF06275F)
private val OnBrandContainer = Color(0xFF06275F)
private val OnBrandContainerDark = Color(0xFFDBE7FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandContainer,
    onPrimaryContainer = OnBrandContainer,
    secondaryContainer = Color(0xFFE8DEF8), // 社区徽标紫
    surface = Color(0xFFF7F8FA), // 页面底：中性浅灰
    surfaceContainerLow = Color.White, // 卡片底色：纯白
    surfaceContainer = Color(0xFFF0F1F4),
    surfaceContainerHigh = Color(0xFFE8EAEE),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF06275F),
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = OnBrandContainerDark,
    surface = Color(0xFF111318),
    surfaceContainerLow = Color(0xFF1A1D24),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF23262E),
)

// ---------- 大圆角 Shape 体系 ----------
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 主题模式：system=跟随系统，light=浅色，dark=深色 */
@Composable
fun AppInspectorTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
