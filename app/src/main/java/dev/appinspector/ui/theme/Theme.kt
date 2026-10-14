package dev.appinspector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------- 品牌色：晴空蓝（冷调清新） ----------
private val BrandBlue = Color(0xFF1A73E8)
private val BrandBlueDark = Color(0xFF8AB4FF)
private val BrandContainer = Color(0xFFD9E9FF)
private val BrandContainerDark = Color(0xFF0A2A5E)
private val OnBrandContainer = Color(0xFF0A2A5E)
private val OnBrandContainerDark = Color(0xFFD9E9FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandContainer,
    onPrimaryContainer = OnBrandContainer,
    secondaryContainer = Color(0xFFE8DEF8), // 社区徽标紫
    background = Color(0xFFF4F6FA), // 页面底：冷调浅蓝灰（去灰绿调）
    surface = Color(0xFFF4F6FA), // 与 background 保持一致
    surfaceContainerLow = Color.White, // 卡片底色：纯白
    surfaceContainer = Color(0xFFECEFF4),
    surfaceContainerHigh = Color(0xFFE2E7EE),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF0A2A5E),
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = OnBrandContainerDark,
    background = Color(0xFF0F1216),
    surface = Color(0xFF0F1216),
    surfaceContainerLow = Color(0xFF171B21),
    surfaceContainer = Color(0xFF1A1F26),
    surfaceContainerHigh = Color(0xFF20262E),
)

// ---------- 大圆角 Shape 体系 ----------
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------- v5 图标彩色底（浅色柔和彩底 / 深色深底） ----------
enum class TileKind { BLUE, GREEN, ORANGE, PURPLE }

/** 返回该色系的 (底色, 图标色)，跟随系统深浅 */
@Composable
fun tileColors(kind: TileKind): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (kind) {
        TileKind.BLUE -> (if (dark) Color(0xFF1E3A5F) else Color(0xFFE3EEFE)) to
            (if (dark) Color(0xFF8AB4FF) else Color(0xFF1A73E8))
        TileKind.GREEN -> (if (dark) Color(0xFF1B4332) else Color(0xFFE7F6EC)) to
            (if (dark) Color(0xFF6FCF97) else Color(0xFF16A34A))
        TileKind.ORANGE -> (if (dark) Color(0xFF4A2F14) else Color(0xFFFEF3E2)) to
            (if (dark) Color(0xFFF5B453) else Color(0xFFD97706))
        TileKind.PURPLE -> (if (dark) Color(0xFF3B2A5E) else Color(0xFFF3E8FF)) to
            (if (dark) Color(0xFFB794F6) else Color(0xFF7C3AED))
    }
}

// ---------- v5 主按钮渐变 ----------
fun brandGradient(): Brush =
    Brush.horizontalGradient(listOf(Color(0xFF4A90F5), Color(0xFF1A73E8)))

// ---------- 状态色（Token v1）：状态徽章编码 ----------
@Immutable
data class AppStatusColors(
    val caution: Color,
    val onCaution: Color,
    val warning: Color,
    val onWarning: Color,
    val disabled: Color,
    val onDisabled: Color,
    val success: Color,
    val onSuccess: Color,
)

private val LightStatus = AppStatusColors(
    caution = Color(0xFFE8DEF8),
    onCaution = Color(0xFF4A2E7A),
    warning = Color(0xFFF9DEDC),
    onWarning = Color(0xFF7A2E1F),
    disabled = Color(0xFFFFF3DC),
    onDisabled = Color(0xFF7A5200),
    success = Color(0xFFDDF0DD),
    onSuccess = Color(0xFF1B5E20),
)

private val DarkStatus = AppStatusColors(
    caution = Color(0xFF3B2A5E),
    onCaution = Color(0xFFD6BCFA),
    warning = Color(0xFF4A2A22),
    onWarning = Color(0xFFFFB4A8),
    disabled = Color(0xFF4A3A14),
    onDisabled = Color(0xFFF5B453),
    success = Color(0xFF1B4332),
    onSuccess = Color(0xFF6FCF97),
)

val LocalAppStatusColors = staticCompositionLocalOf { LightStatus }

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
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppStatusColors provides if (dark) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
