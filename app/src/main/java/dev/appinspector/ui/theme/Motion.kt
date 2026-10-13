package dev.appinspector.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * 动效 Token（Token v1 · 2026-10-13）
 * 曲线统一 M3 emphasized；M2 的 FastOutSlowInEasing 弃用。
 * expressive spring 仅用于关键操作（批量完成等），列表滚动区禁用。
 */
object AppMotion {
    /** M3 emphasized 标准曲线 */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** expressive 弹簧（带 overshoot），仅关键操作 */
    val expressive: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** 涟漪 / 按压缩放 / 小反馈 */
    const val fast = 150

    /** 页面元素进出 / 卡片展开 */
    const val normal = 300

    /** 大过渡 / 全屏页切换 */
    const val slow = 500

    /** 常规 tween（emphasized + 时长） */
    fun <T> tweenFast() = tween<T>(fast, easing = emphasized)
    fun <T> tweenNormal() = tween<T>(normal, easing = emphasized)
    fun <T> tweenSlow() = tween<T>(slow, easing = emphasized)
}
