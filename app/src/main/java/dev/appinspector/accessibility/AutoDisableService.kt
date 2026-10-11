package dev.appinspector.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍自动停用服务。
 *
 * 工作方式：按队列逐个打开系统「应用详情」页，在页面里找到「停用」
 * 按钮并点击，再在确认框里点确定，最后读取 PackageManager 验真。
 *
 * 这是通用引擎：[start] 接收任意包名列表，不绑定具体应用。
 * 只有在自动停用运行中才会触碰界面，平时对系统设置不做任何干预。
 *
 * 安全约束：
 * - 只点文字恰好为「停用」的 Button，绝不碰「卸载」；
 * - 确认框只点「确定」/「确认」（都不在才点框里的「停用」），绝不点「取消」之外的其他按钮；
 * - 每一步都有超时，卡住自动记失败并处理下一个。
 */
class AutoDisableService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<AutoDisableTarget>()
    private val results = mutableListOf<AutoDisableItemResult>()
    private var total = 0
    private var current: AutoDisableTarget? = null
    private var phase = Phase.IDLE
    private var timeoutTask: Runnable? = null

    private enum class Phase { IDLE, OPENING, CLICK_DISABLE, CONFIRM, VERIFY }

    override fun onServiceConnected() {
        instance = this
        if (pendingTargets.isNotEmpty()) {
            begin(pendingTargets.toList())
            pendingTargets.clear()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (phase == Phase.IDLE || event == null) return
        if (event.packageName?.toString() != SETTINGS_PKG) return
        val root = rootInActiveWindow ?: return
        when (phase) {
            Phase.OPENING, Phase.CLICK_DISABLE -> tryClickDisable(root, event.className)
            Phase.CONFIRM -> tryClickConfirm(root)
            else -> Unit
        }
    }

    override fun onInterrupt() {
        stopInternal()
    }

    // ---------------- 对外入口 ----------------

    companion object {
        private const val SETTINGS_PKG = "com.android.settings"
        private const val TIMEOUT_MS = 12_000L
        private const val VERIFY_DELAY_MS = 1_500L
        private const val NEXT_DELAY_MS = 600L

        @Volatile
        private var instance: AutoDisableService? = null
        private val pendingTargets = mutableListOf<AutoDisableTarget>()

        fun componentName(context: Context): ComponentName =
            ComponentName(context, AutoDisableService::class.java)

        /**
         * 开始自动停用（通用：任意包名列表）。
         * 若服务尚未连接（无障碍未开启），先暂存，等 onServiceConnected 再跑；
         * ViewModel 层会先检查无障碍开关并引导用户开启。
         */
        fun start(targets: List<AutoDisableTarget>) {
            val svc = instance
            if (svc == null) {
                pendingTargets.clear()
                pendingTargets.addAll(targets)
                return
            }
            svc.begin(targets)
        }

        fun stop() {
            instance?.stopInternal()
        }
    }

    // ---------------- 队列调度 ----------------

    private fun begin(targets: List<AutoDisableTarget>) {
        stopInternal(silent = true)
        if (targets.isEmpty()) return
        queue.addAll(targets)
        total = targets.size
        AutoDisableBus.update(AutoDisableState.Running(0, total, targets.first().name))
        processNext()
    }

    private fun stopInternal(silent: Boolean = false) {
        timeoutTask?.let { handler.removeCallbacks(it) }
        timeoutTask = null
        queue.clear()
        current = null
        phase = Phase.IDLE
        if (!silent) {
            if (results.isNotEmpty()) AutoDisableBus.update(AutoDisableState.Done(results.toList()))
            else AutoDisableBus.update(AutoDisableState.Idle)
            results.clear()
        }
    }

    private fun processNext() {
        timeoutTask?.let { handler.removeCallbacks(it) }
        timeoutTask = null
        val next = queue.removeFirstOrNull()
        if (next == null) {
            phase = Phase.IDLE
            AutoDisableBus.update(AutoDisableState.Done(results.toList()))
            results.clear()
            bringAppToFront()
            return
        }
        current = next
        phase = Phase.OPENING
        val done = total - queue.size - 1
        AutoDisableBus.update(AutoDisableState.Running(done, total, next.name))
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${next.pkg}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isFailure) {
            finishItem(false, "无法打开应用详情页")
            return
        }
        val task = Runnable {
            if (current == next && phase != Phase.IDLE) finishItem(false, "操作超时")
        }
        timeoutTask = task
        handler.postDelayed(task, TIMEOUT_MS)
    }

    private fun finishItem(ok: Boolean, reason: String = "") {
        timeoutTask?.let { handler.removeCallbacks(it) }
        timeoutTask = null
        val cur = current ?: return
        results.add(AutoDisableItemResult(cur, ok, reason))
        current = null
        phase = Phase.IDLE
        handler.postDelayed({ processNext() }, NEXT_DELAY_MS)
    }

    private fun bringAppToFront() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    // ---------------- 界面操作 ----------------

    /** 文字恰好为 text 的可点击 Button */
    private fun clickableButtons(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> =
        root.findAccessibilityNodeInfosByText(text).filter {
            it.isClickable && it.isEnabled &&
                it.className == "android.widget.Button" &&
                it.text?.toString() == text
        }

    /** 是否应用详情页：窗口类名含 AppDetails/AppInfo，或页面里同时出现「卸载」按钮 */
    private fun looksLikeAppDetails(root: AccessibilityNodeInfo, eventClass: CharSequence?): Boolean {
        val cls = eventClass?.toString() ?: ""
        if (cls.contains("AppDetails") || cls.contains("AppInfo")) return true
        return root.findAccessibilityNodeInfosByText("卸载").any {
            it.isClickable && it.className == "android.widget.Button"
        }
    }

    private fun tryClickDisable(root: AccessibilityNodeInfo, eventClass: CharSequence?) {
        val disableBtn = clickableButtons(root, "停用").firstOrNull()
        if (disableBtn != null) {
            if (disableBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                phase = Phase.CONFIRM
            }
            return
        }
        // 已停用的应用显示的是「启用」按钮：视为已完成
        val enableBtn = clickableButtons(root, "启用").firstOrNull()
        if (enableBtn != null && looksLikeAppDetails(root, eventClass)) {
            finishItem(true)
            return
        }
        // 「停用」存在但灰色不可点 → 保护未解除（解锁组件没装）
        if (!looksLikeAppDetails(root, eventClass)) return
        val grey = root.findAccessibilityNodeInfosByText("停用").any {
            it.isClickable && !it.isEnabled &&
                it.className == "android.widget.Button" &&
                it.text?.toString() == "停用"
        }
        if (grey) finishItem(false, "停用按钮不可用，请先安装解锁组件")
        // 否则继续等页面加载（靠后续事件再次触发）
    }

    private fun tryClickConfirm(root: AccessibilityNodeInfo) {
        val confirm = (clickableButtons(root, "确定") + clickableButtons(root, "确认")).firstOrNull()
            ?: clickableButtons(root, "停用").firstOrNull()
        if (confirm != null && confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            phase = Phase.VERIFY
            handler.postDelayed({
                if (phase == Phase.VERIFY) verifyCurrent()
            }, VERIFY_DELAY_MS)
            return
        }
        // 有些系统版本点停用后没有确认框：直接验真，成了就过
        if (isCurrentDisabled()) finishItem(true)
    }

    private fun isCurrentDisabled(): Boolean {
        val pkg = current?.pkg ?: return false
        return runCatching {
            val pm = packageManager
            val state = pm.getApplicationEnabledSetting(pkg)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
                !pm.getApplicationInfo(pkg, 0).enabled
        }.getOrDefault(false)
    }

    private fun verifyCurrent() {
        val cur = current ?: return
        val disabled = runCatching {
            val pm = packageManager
            val state = pm.getApplicationEnabledSetting(cur.pkg)
            val ai = pm.getApplicationInfo(cur.pkg, 0)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
                !ai.enabled
        }.getOrDefault(false)
        finishItem(disabled, if (disabled) "" else "停用未生效")
    }
}
