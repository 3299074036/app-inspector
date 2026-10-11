package dev.appinspector.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 无障碍自动停用的目标应用。
 *
 * 引擎是通用的：只认包名列表，不绑定任何具体应用。
 * 调用方传入想停用的包即可（例如 14 个 ColorOS 保护应用，
 * 或详情页里常规停用被系统拒绝的任意应用）。
 */
data class AutoDisableTarget(val pkg: String, val name: String)

/** 单个应用的停用结果 */
data class AutoDisableItemResult(
    val target: AutoDisableTarget,
    val ok: Boolean,
    val reason: String = "",
)

/** 自动停用整体状态 */
sealed interface AutoDisableState {
    data object Idle : AutoDisableState
    data class Running(val done: Int, val total: Int, val currentName: String) : AutoDisableState
    data class Done(val results: List<AutoDisableItemResult>) : AutoDisableState
}

/**
 * 服务与 UI 之间的状态总线。
 * 无障碍服务与界面同进程，单例共享即可。
 */
object AutoDisableBus {
    private val _state = MutableStateFlow<AutoDisableState>(AutoDisableState.Idle)
    val state: StateFlow<AutoDisableState> = _state.asStateFlow()
    fun update(s: AutoDisableState) {
        _state.value = s
    }
}
