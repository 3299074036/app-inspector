package dev.appinspector.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AppType 字阶体系（Token v1 · 2026-10-13）
 * M3 字阶为骨 + 中文调适：行高统一 1.5（标题 1.25~1.4）、字距归零、
 * 中文标题用 Medium（慎用 Bold 伪粗体）、数字等宽 tnum、全部 sp。
 */
private fun appTextStyle(
    size: Int,
    line: Int,
    weight: FontWeight,
    tnum: Boolean = false,
) = TextStyle(
    fontFamily = FontFamily.Default, // 系统字体，零成本，跟随用户主题字体
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = if (tnum) "tnum" else null,
)

object AppType {
    /** 大数字统计 */
    val display = appTextStyle(32, 40, FontWeight.Medium, tnum = true)
    /** 页面标题 */
    val headline = appTextStyle(24, 32, FontWeight.Medium)
    /** 二级标题 */
    val titleLg = appTextStyle(20, 28, FontWeight.Medium)
    /** 卡片标题 */
    val titleMd = appTextStyle(16, 24, FontWeight.Medium)
    /** 列表主行 */
    val bodyLg = appTextStyle(16, 24, FontWeight.Normal)
    /** 列表副行 / 正文 */
    val bodyMd = appTextStyle(14, 21, FontWeight.Normal)
    /** 次要信息 */
    val bodySm = appTextStyle(12, 18, FontWeight.Normal)
    /** 徽章 / 小字按钮 */
    val label = appTextStyle(12, 16, FontWeight.Medium)
}

/** 接入 MaterialTheme.typography，供 MaterialTheme.typography.xxx 全局引用 */
internal val AppTypography = Typography(
    displayLarge = AppType.display,
    headlineLarge = AppType.headline,
    headlineMedium = AppType.titleLg,
    titleLarge = AppType.titleMd,
    bodyLarge = AppType.bodyLg,
    bodyMedium = AppType.bodyMd,
    bodySmall = AppType.bodySm,
    labelLarge = appTextStyle(14, 20, FontWeight.Medium),
    labelMedium = AppType.label,
    labelSmall = appTextStyle(11, 16, FontWeight.Normal),
)
