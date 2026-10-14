package dev.appinspector.ui

import android.app.Application
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.pm.PackageManager
import android.os.Build
import dev.appinspector.data.AppEntry
import dev.appinspector.data.ApkEntry
import dev.appinspector.data.AppFilter
import dev.appinspector.data.SuggestSubFilter
import dev.appinspector.data.ProtectedApps
import dev.appinspector.accessibility.AutoDisableBus
import dev.appinspector.accessibility.AutoDisableService
import dev.appinspector.accessibility.AutoDisableState
import dev.appinspector.accessibility.AutoDisableTarget
import dev.appinspector.data.AppRepository
import dev.appinspector.data.AppSettings
import dev.appinspector.data.SettingsStore
import dev.appinspector.data.SortKey
import dev.appinspector.data.StorageHelper
import dev.appinspector.data.StorageSizes
import dev.appinspector.data.UadDb
import dev.appinspector.data.OpHistory
import dev.appinspector.data.OpType
import dev.appinspector.data.OpMethod
import dev.appinspector.data.UsageStatsHelper
import dev.appinspector.shizuku.ShizukuManager
import rikka.shizuku.Shizuku
import dev.appinspector.export.ALL_EXPORT_FIELDS
import dev.appinspector.export.DEFAULT_EXPORT_KEYS
import dev.appinspector.export.ExportFormat
import dev.appinspector.export.buildMarkdown
import dev.appinspector.export.buildTsv
import dev.appinspector.export.writeXlsx
import dev.appinspector.extract.extractApks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractProgress(val done: Int, val total: Int, val name: String)

/** 精简清单条目 */
data class DebloatItem(val packageName: String, val entry: AppEntry?)

/** 被系统恢复的应用 */
data class RestoredApp(val packageName: String, val appName: String, val wasDisabled: Boolean)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val store = SettingsStore(app)

    val settings: StateFlow<AppSettings> = store.settings

    var loading by mutableStateOf(true)
        private set
    internal var allApps: List<AppEntry> by mutableStateOf(emptyList())

    var search by mutableStateOf("")
    var searching by mutableStateOf(false)
    var filter by mutableStateOf(AppFilter.ALL)
    /** 建议精简页的子筛选（按 UAD 建议等级） */
    var suggestSubFilter by mutableStateOf(SuggestSubFilter.ALL)

    var selectMode by mutableStateOf(false)
    val selected = mutableStateMapOf<String, AppEntry>()

    var showExport by mutableStateOf(false)
    var showExtractSettings by mutableStateOf(false)
    var detailEntry by mutableStateOf<AppEntry?>(null)

    // Shizuku
    var shizukuStatus by mutableStateOf<ShizukuManager.Status>(ShizukuManager.Status.NotInstalled)
        private set
    var showShizukuDialog by mutableStateOf(false)
    var confirmDeleteEntry by mutableStateOf<AppEntry?>(null)
    /** 批量删除确认（多选模式 / 精简清单共用） */
    var confirmBatchDelete by mutableStateOf<List<AppEntry>?>(null)

    // 精简清单导入
    var showDebloatDialog by mutableStateOf(false)
    var debloatItems by mutableStateOf<List<DebloatItem>>(emptyList())
        private set

    // 导出 ADB 脚本
    var exportScriptRequest by mutableStateOf(false)
        private set

    // OTA 恢复提醒：被系统恢复的应用（wasDisabled=true 应为停用态，false 应为删除态）
    var restoredApps by mutableStateOf<List<RestoredApp>>(emptyList())
        private set

    // 使用统计权限
    var hasUsagePerm by mutableStateOf(false)
        private set

    /** 启动时申请使用权限的弹窗（仅当未授权时显示一次） */
    var showUsagePermRequest by mutableStateOf(false)

    /** 启动时检查：未授权则弹出申请 */
    fun checkUsagePermOnLaunch() {
        hasUsagePerm = UsageStatsHelper.hasPermission(getApplication())
        if (!hasUsagePerm) showUsagePermRequest = true
    }

    // ---------- 存储占用 ----------

    /** 详情弹窗的存储占用（按需加载） */
    var storageInfo by mutableStateOf<StorageSizes?>(null)
        private set
    var confirmClearData by mutableStateOf<AppEntry?>(null)

    fun loadStorageInfo(pkg: String) {
        storageInfo = null
        viewModelScope.launch(Dispatchers.IO) {
            val info = StorageHelper.querySizes(getApplication(), pkg)
            withContext(Dispatchers.Main) { storageInfo = info }
        }
    }

    fun clearStorageInfo() {
        storageInfo = null
    }

    /** 清除应用数据（pm clear --user 0，需 Shizuku），调用前请先经 confirmClearData 确认 */
    /** 记录操作历史 */
    private fun recordOp(entry: AppEntry, op: String, method: String, success: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                OpHistory.record(
                    app, entry.packageName, entry.appName,
                    op, method, success, entry.apkSize,
                )
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                historyVersion++
            }
        }
    }

    /** 操作历史版本号（自增以触发 UI 刷新） */
    var historyVersion by mutableStateOf(0)

    fun clearAppData(entry: AppEntry) {
        viewModelScope.launch {
            val res = ShizukuManager.execCommand(
                getApplication(), "pm", "clear", "--user", "0", entry.packageName
            )
            confirmClearData = null
            res.onSuccess {
                toastMsg = "已清除 ${entry.appName} 的数据"
                loadStorageInfo(entry.packageName)
                recordOp(entry, OpType.CLEAR_DATA, OpMethod.MANUAL, true)
            }.onFailure {
                toastMsg = "清除失败：${it.message}"
                recordOp(entry, OpType.CLEAR_DATA, OpMethod.MANUAL, false)
            }
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                refreshShizukuStatus()
                toastMsg = "已获得 Shizuku 授权"
            } else {
                toastMsg = "未获得 Shizuku 授权，无法执行停用/删除"
            }
        }

    var progress by mutableStateOf<ExtractProgress?>(null)
        private set
    /** 进度弹窗标题（提取/批量停用/批量删除共用） */
    var progressTitle by mutableStateOf("")
        private set
    var toastMsg by mutableStateOf<String?>(null)
        private set
    var pickDirRequest by mutableStateOf(false)
        private set
    private var pendingExtract: List<AppEntry>? = null

    // 导出弹窗的临时状态
    var exportFields by mutableStateOf(DEFAULT_EXPORT_KEYS)
    var exportFormat by mutableStateOf(ExportFormat.MD)
    var exportScopeAll by mutableStateOf(false)

    // 无障碍自动停用状态（必须声明在 init 之前：init 里会同步调用 refreshAccessibility()）
    var accessibilityEnabled by mutableStateOf(false)
        private set
    var autoDisableUi by mutableStateOf<AutoDisableState>(AutoDisableState.Idle)
        private set
    /** 自动停用配置详情弹窗 */
    var showAutoDisableDetail by mutableStateOf(false)
    /** 首页排序底部弹窗 */
    var showSortSheet by mutableStateOf(false)
    /**
     * 批量停用失败记下的包名（系统拒绝）。收拢到「保护应用停用」卡片，
     * 与 14 个保护应用一起走无障碍自动停用。通用：以后任何受限应用同理。
     */
    var batchFailedPkgs by mutableStateOf(setOf<String>())
        private set

    init {
        refresh()
        ShizukuManager.addPermissionListener(shizukuPermissionListener)
        refreshShizukuStatus()
        refreshAccessibility()
        // 镜像无障碍自动停用的状态总线；跑完后刷新列表
        viewModelScope.launch {
            AutoDisableBus.state.collect { s ->
                autoDisableUi = s
                if (s is AutoDisableState.Done) {
                    // 成功的从批量失败记录里清除
                    val okPkgs = s.results.filter { it.ok }.map { it.target.pkg }.toSet()
                    if (okPkgs.isNotEmpty()) batchFailedPkgs = batchFailedPkgs - okPkgs
                    // 记录操作历史（无障碍自动停用）
                    val byPkg = allApps.associateBy { it.packageName }
                    for (r in s.results) {
                        byPkg[r.target.pkg]?.let { e ->
                            recordOp(e, OpType.DISABLE, OpMethod.AUTO, r.ok)
                        }
                    }
                    refresh()
                }
            }
        }
    }

    override fun onCleared() {
        ShizukuManager.removePermissionListener(shizukuPermissionListener)
        super.onCleared()
    }

    /** 刷新 Shizuku 可用状态 */
    fun refreshShizukuStatus() {
        shizukuStatus = ShizukuManager.getStatus(getApplication())
    }

    fun requestShizukuPermission() = ShizukuManager.requestPermission()

    /** 停用/启用应用（需 Shizuku） */
    /**
     * 查询包的真实启用状态（用于验证 pm 命令是否真正生效，
     * ColorOS 对部分包会静默拒绝，命令返回成功但状态不变）。
     */
    private fun queryActualEnabled(packageName: String): Boolean {
        val pm = getApplication<Application>().packageManager
        return runCatching {
            val setting = pm.getApplicationEnabledSetting(packageName)
            val ai = pm.getApplicationInfo(packageName, 0)
            setting != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                setting != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                setting != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED &&
                ai.enabled
        }.getOrDefault(true)
    }

    /** 查询包是否真实已删除（pm uninstall --user 0 后验证用） */
    private fun queryActualUninstalled(packageName: String): Boolean {
        val pm = getApplication<Application>().packageManager
        return runCatching {
            pm.getPackageInfo(packageName, 0)
            false
        }.getOrDefault(true)
    }

    fun setAppEnabled(entry: AppEntry, enabled: Boolean) {
        viewModelScope.launch {
            val res = ShizukuManager.setEnabled(getApplication(), entry.packageName, enabled)
            res.onSuccess {
                // 验证真实状态，ColorOS 可能静默拒绝
                val actual = queryActualEnabled(entry.packageName)
                if (actual == enabled) {
                    toastMsg = if (enabled) "已启用 ${entry.appName}" else "已停用 ${entry.appName}"
                    if (enabled) store.untrack(entry.packageName)
                    else store.trackDisabled(entry.packageName)
                    if (detailEntry?.packageName == entry.packageName) {
                        detailEntry = entry.copy(isEnabled = enabled)
                    }
                    recordOp(entry, if (enabled) OpType.ENABLE else OpType.DISABLE, OpMethod.MANUAL, true)
                } else {
                    toastMsg = "操作未生效：${entry.appName}（系统拒绝了该操作）"
                    recordOp(entry, if (enabled) OpType.ENABLE else OpType.DISABLE, OpMethod.MANUAL, false)
                }
                refresh()
            }.onFailure {
                toastMsg = "操作失败：${it.message}"
                recordOp(entry, if (enabled) OpType.ENABLE else OpType.DISABLE, OpMethod.MANUAL, false)
            }
        }
    }

    /** 删除应用（pm uninstall --user 0，需 Shizuku），操作前请先弹窗确认 */
    fun uninstallApp(entry: AppEntry) {
        viewModelScope.launch {
            val res = ShizukuManager.uninstall(getApplication(), entry.packageName)
            res.onSuccess {
                // 验证真实状态
                val actual = queryActualUninstalled(entry.packageName)
                if (actual) {
                    toastMsg = "已删除 ${entry.appName}"
                    store.trackUninstalled(entry.packageName)
                    confirmDeleteEntry = null
                    detailEntry = null
                    recordOp(entry, OpType.DELETE, OpMethod.MANUAL, true)
                } else {
                    toastMsg = "操作未生效：${entry.appName}（系统拒绝了该操作）"
                    confirmDeleteEntry = null
                    recordOp(entry, OpType.DELETE, OpMethod.MANUAL, false)
                }
                refresh()
            }.onFailure {
                toastMsg = "删除失败：${it.message}"
                recordOp(entry, OpType.DELETE, OpMethod.MANUAL, false)
            }
        }
    }

    /** 恢复已删除的系统应用（pm install-existing --user 0，需 Shizuku） */
    fun restoreApp(entry: AppEntry) {
        viewModelScope.launch {
            val res = ShizukuManager.installExisting(getApplication(), entry.packageName)
            res.onSuccess {
                toastMsg = "已恢复 ${entry.appName}"
                store.untrack(entry.packageName)
                if (detailEntry?.packageName == entry.packageName) {
                    detailEntry = null
                }
                recordOp(entry, OpType.RESTORE, OpMethod.MANUAL, true)
                refresh()
            }.onFailure {
                toastMsg = "恢复失败：${it.message}"
                recordOp(entry, OpType.RESTORE, OpMethod.MANUAL, false)
            }
        }
    }

    /** 批量恢复已删除的系统应用（需 Shizuku） */
    fun batchRestore(entries: List<AppEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            progressTitle = "正在批量恢复"
            progress = ExtractProgress(0, entries.size, "")
            var ok = 0
            val failed = mutableListOf<String>()
            for ((i, e) in entries.withIndex()) {
                progress = ExtractProgress(i, entries.size, e.appName)
                val res = ShizukuManager.installExisting(ctx, e.packageName)
                if (res.isSuccess) {
                    ok++
                    store.untrack(e.packageName)
                    recordOp(e, OpType.RESTORE, OpMethod.BATCH, true)
                } else {
                    failed.add(e.appName)
                    recordOp(e, OpType.RESTORE, OpMethod.BATCH, false)
                }
            }
            progress = ExtractProgress(entries.size, entries.size, "")
            progress = null
            exitSelectMode()
            refresh()
            toastMsg = buildString {
                append("批量恢复完成：成功 $ok 个")
                if (failed.isNotEmpty()) {
                    append("，失败 ${failed.size} 个（")
                    append(failed.take(3).joinToString("、"))
                    if (failed.size > 3) append("…")
                    append("）")
                }
            }
        }
    }

    /**
     * 批量停用/启用（需 Shizuku）。逐个执行并显示进度，
     * 成功后记录追踪（用于 OTA 恢复提醒）。
     */
    fun batchSetEnabled(entries: List<AppEntry>, enabled: Boolean) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            progressTitle = if (enabled) "正在批量启用" else "正在批量停用"
            progress = ExtractProgress(0, entries.size, "")
            var ok = 0
            val failed = mutableListOf<String>()
            val failedPkgs = mutableListOf<String>()
            for ((i, e) in entries.withIndex()) {
                progress = ExtractProgress(i, entries.size, e.appName)
                val res = ShizukuManager.setEnabled(ctx, e.packageName, enabled)
                // 验证真实状态（ColorOS 可能静默拒绝）
                if (res.isSuccess && queryActualEnabled(e.packageName) == enabled) {
                    ok++
                    if (enabled) store.untrack(e.packageName)
                    else store.trackDisabled(e.packageName)
                    recordOp(e, if (enabled) OpType.ENABLE else OpType.DISABLE, OpMethod.BATCH, true)
                } else {
                    failed.add(e.appName)
                    recordOp(e, if (enabled) OpType.ENABLE else OpType.DISABLE, OpMethod.BATCH, false)
                    // 批量停用被拒 → 记下包名，收拢到「自动停用」卡片走无障碍通道
                    if (!enabled) failedPkgs.add(e.packageName)
                }
            }
            progress = ExtractProgress(entries.size, entries.size, "")
            progress = null
            exitSelectMode()
            debloatItems = emptyList()
            refresh()
            if (failedPkgs.isNotEmpty()) batchFailedPkgs = batchFailedPkgs + failedPkgs
            toastMsg = buildString {
                append(if (enabled) "批量启用完成" else "批量停用完成")
                append("：成功 $ok 个")
                if (failed.isNotEmpty()) {
                    append("，未生效 ${failed.size} 个（")
                    append(failed.take(3).joinToString("、"))
                    if (failed.size > 3) append("…")
                    append("，系统拒绝）")
                    if (failedPkgs.isNotEmpty()) append("，已记入自动停用待处理")
                }
            }
        }
    }

    // ================= 停用保护解锁 =================

    /** 解锁组件（CTS 测试包）是否已安装 */
    var unlockPkgInstalled by mutableStateOf(false)
        private set

    fun refreshUnlockPkg() {
        unlockPkgInstalled = runCatching {
            getApplication<Application>().packageManager
                .getPackageInfo(ProtectedApps.UNLOCK_PKG, 0)
            true
        }.getOrDefault(false)
    }

    /** 安装解锁组件：从 assets 释放 apk，经系统安装器安装 */
    fun installUnlockPkg(context: Context) {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                toastMsg = "请在系统设置中允许「应用检查器」安装未知应用"
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "unlock.apk")
                context.assets.open("unlock.apk").use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) { context.startActivity(intent) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toastMsg = "释放安装包失败：${t.message}" }
            }
        }
    }

    fun appNameOf(pkg: String): String =
        allApps.find { it.packageName == pkg }?.appName ?: pkg

    // ================= 无障碍自动停用（通用引擎） =================

    fun refreshAccessibility() {
        val ctx = getApplication<Application>()
        val flat = AutoDisableService.componentName(ctx).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        accessibilityEnabled = enabled?.split(':')?.contains(flat) == true
    }

    /** 跳转系统无障碍设置 */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure { toastMsg = "无法打开无障碍设置" }
    }

    /**
     * 无障碍自动停用（通用：任意包名列表）。
     * 引擎自动打开系统应用详情页并点击「停用」，全程无需手动操作。
     */
    fun startAutoDisable(pkgs: List<String>) {
        val known = allApps.filter {
            it.packageName in pkgs && it.isEnabled && !it.isUninstalled
        }.map { AutoDisableTarget(it.packageName, it.appName) }
        val knownPkgs = known.map { it.pkg }.toSet()
        val all = known + pkgs.filter { it !in knownPkgs }
            .map { AutoDisableTarget(it, it) }
        if (all.isEmpty()) {
            toastMsg = "所选应用均已停用，无需操作"
            return
        }
        if (all.any { it.pkg in ProtectedApps.PKGS } && !unlockPkgInstalled) {
            // 直接打开配置弹窗引导安装，而不是只弹 toast
            showAutoDisableDetail = true
            return
        }
        refreshAccessibility()
        if (!accessibilityEnabled) {
            // 直接打开配置弹窗引导开启，而不是只弹 toast
            showAutoDisableDetail = true
            return
        }
        AutoDisableService.start(all)
    }

    /** 停止正在进行的自动停用 */
    fun stopAutoDisable() {
        AutoDisableService.stop()
    }

    /** 重试自动停用中失败的项 */
    fun retryAutoDisableFailed() {
        val s = autoDisableUi as? AutoDisableState.Done ?: return
        val failed = s.results.filter { !it.ok }
        if (failed.isEmpty()) return
        startAutoDisable(failed.map { it.target.pkg })
    }

    /** 关闭自动停用结果面板 */
    fun dismissAutoDisableResult() {
        autoDisableUi = AutoDisableState.Idle
        AutoDisableBus.update(AutoDisableState.Idle)
    }

    /**
     * 待自动停用的应用：仅批量停用失败记下的包（14 个保护应用不再默认列出）。
     * 过滤掉已停用/已删除的。
     */
    val pendingAutoDisable: List<AutoDisableTarget>
        get() = batchFailedPkgs.mapNotNull { pkg ->
            allApps.find { it.packageName == pkg }
                ?.takeIf { it.isEnabled && !it.isUninstalled }
                ?.let { AutoDisableTarget(it.packageName, it.appName) }
        }

    /** 对待处理列表执行无障碍自动停用 */
    fun autoDisablePending() {
        val pkgs = pendingAutoDisable.map { it.pkg }
        if (pkgs.isEmpty()) {
            toastMsg = "暂无待处理应用"
            return
        }
        startAutoDisable(pkgs)
    }

    /** 批量删除（需 Shizuku），调用前请先经 confirmBatchDelete 确认 */
    fun batchUninstall(entries: List<AppEntry>, clearDebloatItems: Boolean = true) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            progressTitle = "正在批量删除"
            progress = ExtractProgress(0, entries.size, "")
            var ok = 0
            val failed = mutableListOf<String>()
            for ((i, e) in entries.withIndex()) {
                progress = ExtractProgress(i, entries.size, e.appName)
                val res = ShizukuManager.uninstall(ctx, e.packageName)
                // 验证真实状态
                if (res.isSuccess && queryActualUninstalled(e.packageName)) {
                    ok++
                    store.trackUninstalled(e.packageName)
                    recordOp(e, OpType.DELETE, OpMethod.BATCH, true)
                } else {
                    failed.add(e.appName)
                    recordOp(e, OpType.DELETE, OpMethod.BATCH, false)
                }
            }
            progress = ExtractProgress(entries.size, entries.size, "")
            progress = null
            confirmBatchDelete = null
            exitSelectMode()
            if (clearDebloatItems) debloatItems = emptyList()
            refresh()
            toastMsg = buildString {
                append("批量删除完成：成功 $ok 个")
                if (failed.isNotEmpty()) {
                    append("，未生效 ${failed.size} 个（")
                    append(failed.take(3).joinToString("、"))
                    if (failed.size > 3) append("…")
                    append("，系统拒绝）")
                }
            }
        }
    }

    /**
     * L3: 从备份串行恢复（先停用后删除，共用一套进度，避免双协程打架）。
     * 恢复完自增 backupVersion 以刷新备份页快照（U4）。
     */
    fun restoreFromBackup(toDisable: List<AppEntry>, toDelete: List<AppEntry>) {
        if (toDisable.isEmpty() && toDelete.isEmpty()) {
            showToast("无需恢复：备份中的应用状态与当前一致")
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            var okDisable = 0
            var okDelete = 0
            val failed = mutableListOf<String>()
            val failedPkgs = mutableListOf<String>()
            val total = toDisable.size + toDelete.size
            var done = 0
            if (toDisable.isNotEmpty()) {
                progressTitle = "正在恢复停用"
                for (e in toDisable) {
                    progress = ExtractProgress(done, total, e.appName)
                    val res = ShizukuManager.setEnabled(ctx, e.packageName, false)
                    if (res.isSuccess && !queryActualEnabled(e.packageName)) {
                        okDisable++
                        store.trackDisabled(e.packageName)
                        recordOp(e, OpType.DISABLE, OpMethod.BATCH, true)
                    } else {
                        failed.add(e.appName)
                        failedPkgs.add(e.packageName)
                        recordOp(e, OpType.DISABLE, OpMethod.BATCH, false)
                    }
                    done++
                }
            }
            if (toDelete.isNotEmpty()) {
                progressTitle = "正在恢复删除"
                for (e in toDelete) {
                    progress = ExtractProgress(done, total, e.appName)
                    val res = ShizukuManager.uninstall(ctx, e.packageName)
                    if (res.isSuccess && queryActualUninstalled(e.packageName)) {
                        okDelete++
                        store.trackUninstalled(e.packageName)
                        recordOp(e, OpType.DELETE, OpMethod.BATCH, true)
                    } else {
                        failed.add(e.appName)
                        recordOp(e, OpType.DELETE, OpMethod.BATCH, false)
                    }
                    done++
                }
            }
            progress = ExtractProgress(total, total, "")
            progress = null
            if (failedPkgs.isNotEmpty()) batchFailedPkgs = batchFailedPkgs + failedPkgs.toSet()
            refresh()
            backupManager.bumpVersion()
            showToast(buildString {
                append("恢复完成：停用 $okDisable 个，删除 $okDelete 个")
                if (failed.isNotEmpty()) append("，未生效 ${failed.size} 个")
            })
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                val ctx = getApplication<Application>()
                var apps = repo.loadApps()
                // 使用统计：有权限才查，一次查全量后填入
                hasUsagePerm = UsageStatsHelper.hasPermission(ctx)
                if (hasUsagePerm) {
                    val usage = withContext(Dispatchers.IO) { UsageStatsHelper.queryLastUsed(ctx) }
                    if (usage.isNotEmpty()) {
                        apps = apps.map { it.copy(lastUsed = usage[it.packageName]) }
                    }
                }
                allApps = apps
                checkRestored(apps)
                // 后台加载 UAD 建议包集合（用于"建议精简"Tab）
                launch(Dispatchers.IO) {
                    val pkgs = runCatching {
                        UadDb.get(ctx).keys
                    }.getOrDefault(emptySet())
                    suggestPkgs = pkgs
                }
            } catch (t: Throwable) {
                toastMsg = "加载失败：${t.message}"
            }
            loading = false
        }
    }

    /**
     * OTA 恢复检测：对比用户经本应用停用/删除过的包，
     * 若被系统恢复（重新启用 / 重新安装），加入提醒列表。
     */
    private fun checkRestored(apps: List<AppEntry>) {
        val byPkg = apps.associateBy { it.packageName }
        val restored = mutableListOf<RestoredApp>()
        for (pkg in store.getTrackedDisabled()) {
            val e = byPkg[pkg]
            if (e != null && e.isEnabled && !e.isUninstalled) {
                restored.add(RestoredApp(pkg, e.appName, wasDisabled = true))
            }
        }
        for (pkg in store.getTrackedUninstalled()) {
            val e = byPkg[pkg]
            if (e != null && !e.isUninstalled) {
                restored.add(RestoredApp(pkg, e.appName, wasDisabled = false))
            }
        }
        restoredApps = restored
    }

    /** 重新执行被恢复的操作（验证真实状态，如实报告） */
    fun reapplyRestored() {
        val list = restoredApps
        if (list.isEmpty()) return
        val byPkg = allApps.associateBy { it.packageName }
        val toDisable = list.filter { it.wasDisabled }.mapNotNull { byPkg[it.packageName] }
        val toDelete = list.filter { !it.wasDisabled }.mapNotNull { byPkg[it.packageName] }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val failed = mutableListOf<String>()
            var ok = 0
            if (toDisable.isNotEmpty()) {
                progressTitle = "正在重新停用"
                progress = ExtractProgress(0, toDisable.size, "")
                for ((i, e) in toDisable.withIndex()) {
                    progress = ExtractProgress(i, toDisable.size, e.appName)
                    val res = ShizukuManager.setEnabled(ctx, e.packageName, false)
                    // 验证真实状态
                    if (res.isSuccess && !queryActualEnabled(e.packageName)) {
                        ok++
                        recordOp(e, OpType.DISABLE, OpMethod.OTA, true)
                    } else {
                        failed.add(e.appName)
                        recordOp(e, OpType.DISABLE, OpMethod.OTA, false)
                    }
                }
                progress = null
            }
            if (toDelete.isNotEmpty()) {
                progressTitle = "正在重新删除"
                progress = ExtractProgress(0, toDelete.size, "")
                for ((i, e) in toDelete.withIndex()) {
                    progress = ExtractProgress(i, toDelete.size, e.appName)
                    val res = ShizukuManager.uninstall(ctx, e.packageName)
                    if (res.isSuccess && queryActualUninstalled(e.packageName)) {
                        ok++
                        recordOp(e, OpType.DELETE, OpMethod.OTA, true)
                    } else {
                        failed.add(e.appName)
                        recordOp(e, OpType.DELETE, OpMethod.OTA, false)
                    }
                }
                progress = null
            }
            refresh()
            toastMsg = buildString {
                append("重新执行完成：成功 $ok 个")
                if (failed.isNotEmpty()) {
                    append("，未生效 ${failed.size} 个（")
                    append(failed.take(3).joinToString("、"))
                    if (failed.size > 3) append("…")
                    append("，系统拒绝了这些操作）")
                }
            }
        }
    }

    /** 忽略恢复提醒：取消对这些包的追踪 */
    // ============ v6.7: 配置备份（文件操作已抽到 BackupManager） ============
    val backupManager = BackupManager(
        app = getApplication(),
        scope = viewModelScope,
        snapshotProvider = ::currentSnapshot,
        onToast = ::showToast,
    )

    /** 当前快照：已停用 / 已删除包名 */
    fun currentSnapshot(): Pair<List<String>, List<String>> {
        val disabled = allApps.filter { !it.isEnabled && !it.isUninstalled }.map { it.packageName }
        val deleted = allApps.filter { it.isUninstalled }.map { it.packageName }
        return disabled to deleted
    }

    /** 扫码分享：生成可分享的包名清单文本（对方扫码后粘贴到清单 Tab 导入） */
    fun sharePlanText(): String {
        val (disabled, deleted) = currentSnapshot()
        return buildString {
            if (disabled.isNotEmpty()) {
                appendLine("# 停用（${disabled.size}）")
                disabled.forEach { appendLine(it) }
            }
            if (deleted.isNotEmpty()) {
                appendLine("# 删除（${deleted.size}）")
                deleted.forEach { appendLine(it) }
            }
        }.trim()
    }

    /** v6.7: 按包名查应用条目（供备份恢复用） */
    fun findEntry(pkg: String): AppEntry? = allApps.find { it.packageName == pkg }

    fun ignoreRestored() {
        store.untrackAll(restoredApps.map { it.packageName })
        restoredApps = emptyList()
    }

    /** 全部已安装应用数量（导出范围用） */
    fun totalCount(): Int = allApps.size
    fun disabledCount(): Int = allApps.count { !it.isEnabled && !it.isUninstalled }
    fun deletedCount(): Int = allApps.count { it.isUninstalled }

    /** 各 Tab 的应用个数（受"显示系统应用"影响，不受搜索影响） */
    fun countFor(f: AppFilter): Int {
        val s = settings.value
        var list = allApps
        if (!s.showSystem) list = list.filter { !it.isSystem }
        return when (f) {
            AppFilter.ALL -> list.size
            AppFilter.DISABLED -> list.count { !it.isEnabled && !it.isUninstalled }
            AppFilter.DELETED -> list.count { it.isUninstalled }
            AppFilter.SUGGEST -> list.count { it.packageName in suggestPkgs }
        }
    }

    /** 当前筛选 + 搜索 + 排序后的列表 */
    /** 有 UAD 社区建议的包名集合（后台加载） */
    var suggestPkgs by mutableStateOf<Set<String>>(emptySet())
        private set

    fun currentList(): List<AppEntry> = listFor(filter)

    fun listFor(f: AppFilter): List<AppEntry> {
        val s = settings.value
        var list = allApps
        if (!s.showSystem) list = list.filter { !it.isSystem }
        if (f == AppFilter.DISABLED) list = list.filter { !it.isEnabled && !it.isUninstalled }
        if (f == AppFilter.DELETED) list = list.filter { it.isUninstalled }
        if (f == AppFilter.SUGGEST) list = list.filter { it.packageName in suggestPkgs }
        val q = search.trim()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.appName.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        val collator = Collator.getInstance(Locale.CHINA)
        val comp: Comparator<AppEntry> = when (s.sortKey) {
            SortKey.SIZE -> compareBy { it.apkSize }
            SortKey.NAME -> Comparator { a, b -> collator.compare(a.appName, b.appName) }
            SortKey.PKG -> compareBy { it.packageName }
            SortKey.INSTALLED -> compareBy { it.firstInstallTime }
            SortKey.UPDATED -> compareBy { it.lastUpdateTime }
            // null（未知）排最后；0（从未打开）按最久处理
            SortKey.LAST_USED -> compareBy { it.lastUsed ?: Long.MAX_VALUE }
        }
        return list.sortedWith(if (s.sortAsc) comp else comp.reversed())
    }

    fun setShowSystem(v: Boolean) =
        updateSetting { putBoolean(SettingsStore.Keys.SHOW_SYSTEM, v) }

    var showThemeDialog by mutableStateOf(false)

    fun setThemeMode(mode: String) =
        updateSetting { putString(SettingsStore.Keys.THEME_MODE, mode) }

    fun themeModeTitle(mode: String): String = when (mode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
    }

    fun setSort(key: SortKey, asc: Boolean) = updateSetting {
        putString(SettingsStore.Keys.SORT_KEY, key.name)
        putBoolean(SettingsStore.Keys.SORT_ASC, asc)
    }

    fun setNaming(expr: String) =
        updateSetting { putString(SettingsStore.Keys.NAMING_EXPR, expr) }

    fun setIncludeSplits(v: Boolean) =
        updateSetting { putBoolean(SettingsStore.Keys.INCLUDE_SPLITS, v) }

    private fun updateSetting(transform: SharedPreferences.Editor.() -> Unit) {
        viewModelScope.launch { store.update(transform) }
    }

    fun toggleSelect(e: AppEntry) {
        if (selected.remove(e.packageName) == null) selected[e.packageName] = e
    }

    /** 全选当前列表 */
    fun selectAll(entries: List<AppEntry>) {
        entries.forEach { selected[it.packageName] = it }
    }

    fun enterSelectMode(e: AppEntry? = null) {
        selectMode = true
        selected.clear()
        e?.let { selected[it.packageName] = it }
    }

    fun exitSelectMode() {
        selectMode = false
        selected.clear()
    }

    fun openAppSettings(context: Context, pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 导出：由 UI 的 CreateDocument launcher 拿到 uri 后调用 */
    fun doExport(context: Context, uri: Uri) {
        val fields = ALL_EXPORT_FIELDS.filter { it.key in exportFields }
        if (fields.isEmpty()) {
            toastMsg = "请至少选择一个字段"
            return
        }
        val entries = if (exportScopeAll) allApps else currentList()
        if (entries.isEmpty()) {
            toastMsg = "当前列表为空"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    when (exportFormat) {
                        ExportFormat.MD -> out.write(buildMarkdown(entries, fields).toByteArray(Charsets.UTF_8))
                        ExportFormat.TXT -> out.write(buildTsv(entries, fields).toByteArray(Charsets.UTF_8))
                        ExportFormat.XLSX -> writeXlsx(entries, fields, out)
                    }
                } ?: throw IllegalStateException("无法打开输出流")
                withContext(Dispatchers.Main) { toastMsg = "导出成功" }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toastMsg = "导出失败：${t.message}" }
            }
        }
    }

    fun exportFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "应用列表_$ts.${exportFormat.ext}"
    }

    // ---------- 精简清单导入 ----------

    /** 解析粘贴的包名清单（每行一个，# 开头为注释），匹配本地应用 */
    /** 从文本提取包名：支持纯包名、pm 命令、adb shell pm 命令 */
    private val pkgPattern = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+)\b""")
    fun extractPackageNames(text: String): List<String> {
        // 先按分号拆（pm 命令常用分号连接），再按行拆
        return text.split(";")
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { line ->
                // 纯包名行直接取；命令里用正则提取
                if (line.matches(Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+"""))) {
                    listOf(line)
                } else {
                    pkgPattern.findAll(line).map { it.groupValues[1] }.toList()
                }
            }
            .distinct()
            .toList()
    }

    fun parseDebloatList(text: String) {
        val pkgs = extractPackageNames(text)
        if (pkgs.isEmpty()) {
            toastMsg = "未解析到有效包名"
            return
        }
        val byPkg = allApps.associateBy { it.packageName }
        debloatItems = pkgs.map { DebloatItem(it, byPkg[it]) }
        toastMsg = "识别出 ${pkgs.size} 个包名"
    }

    fun clearDebloatList() {
        debloatItems = emptyList()
    }

    // ---------- 导出 ADB 脚本 ----------

    fun requestExportScript() {
        exportScriptRequest = true
    }

    fun consumeExportScriptRequest(): Boolean {
        val r = exportScriptRequest
        exportScriptRequest = false
        return r
    }

    fun exportScriptFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "debloat_$ts.sh"
    }

    /**
     * 导出精简脚本：已停用 -> pm disable-user，已删除 -> pm uninstall。
     * 生成的裸 pm 命令可直接 `adb shell < xxx.sh` 执行，
     * 也可在手机本地 shell（LADB / aShell）中逐行执行。
     */
    fun doExportScript(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val disabled = allApps.filter { !it.isEnabled && !it.isUninstalled }
                    .sortedBy { it.packageName }
                val uninstalled = allApps.filter { it.isUninstalled }
                    .sortedBy { it.packageName }
                if (disabled.isEmpty() && uninstalled.isEmpty()) {
                    withContext(Dispatchers.Main) { toastMsg = "没有已停用/已删除的应用可导出" }
                    return@launch
                }
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val sb = StringBuilder()
                sb.appendLine("#!/bin/sh")
                sb.appendLine("# 应用检查器导出的精简脚本")
                sb.appendLine("# 生成时间：$ts")
                sb.appendLine("#")
                sb.appendLine("# 用法：")
                sb.appendLine("#   电脑：adb shell < ${exportScriptFileName()}")
                sb.appendLine("#   手机：LADB / aShell 中逐行执行，或 sh ${exportScriptFileName()}")
                sb.appendLine()
                if (disabled.isNotEmpty()) {
                    sb.appendLine("# ---------- 停用（${disabled.size} 个） ----------")
                    for (e in disabled) {
                        sb.appendLine("# ${e.appName}")
                        sb.appendLine("pm disable-user --user 0 ${e.packageName}")
                    }
                    sb.appendLine()
                }
                if (uninstalled.isNotEmpty()) {
                    sb.appendLine("# ---------- 删除（${uninstalled.size} 个） ----------")
                    for (e in uninstalled) {
                        sb.appendLine("# ${e.appName}")
                        sb.appendLine("pm uninstall --user 0 ${e.packageName}")
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(sb.toString().toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法打开输出流")
                withContext(Dispatchers.Main) {
                    toastMsg = "导出成功"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toastMsg = "导出失败：${t.message}" }
            }
        }
    }

    // ---------- Shizuku 命令终端 ----------

    var showTerminal by mutableStateOf(false)
    /** v6.7: 操作历史 / 精简报告 / 配置备份 全屏页 */
    var showHistory by mutableStateOf(false)
    var showReport by mutableStateOf(false)
    var showBackup by mutableStateOf(false)
    /** 安装包管理全屏页 */
    var showApkManager by mutableStateOf(false)

    data class TerminalLine(
        val text: String,
        val isCommand: Boolean,
        val isError: Boolean = false,
        /** v6.6: 状态行（✓/✗），statusOk 区分成功/失败 */
        val isStatus: Boolean = false,
        val statusOk: Boolean = true,
    )

    var terminalLines by mutableStateOf<List<TerminalLine>>(emptyList())
        private set
    var terminalRunning by mutableStateOf(false)
        private set

    fun clearTerminal() {
        terminalLines = emptyList()
    }

    /**
     * 执行终端命令。支持用 `;` 分隔多条命令批量执行（逐条顺序执行）；
     * 仍不支持管道 `|` 与重定向 `>`（shell 身份限制）。
     */
    fun execTerminalCommand(command: String) {
        val cmds = command.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        if (cmds.isEmpty() || terminalRunning) return
        terminalRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            for ((i, cmd) in cmds.withIndex()) {
                // 多条时标注序号
                val prompt = if (cmds.size > 1) "$ [${i + 1}/${cmds.size}] $cmd" else "$ $cmd"
                withContext(Dispatchers.Main) {
                    terminalLines = terminalLines + TerminalLine(prompt, isCommand = true)
                }
                val res = ShizukuManager.execRaw(ctx, cmd)
                val ok = res.isSuccess
                val line = res.fold(
                    onSuccess = { (_, out) ->
                        val o = out.trim()
                        TerminalLine(if (o.isEmpty()) "Success" else o, isCommand = false)
                    },
                    onFailure = {
                        TerminalLine("错误：${it.message}", isCommand = false, isError = true)
                    },
                )
                // v6.6: 状态行（批量时带 [n/N] 序号）
                val tag = if (cmds.size > 1) "[${i + 1}/${cmds.size}]" else ""
                val statusLine = TerminalLine(
                    text = if (ok) "执行成功 $tag" else "执行失败 $tag",
                    isCommand = false,
                    isStatus = true,
                    statusOk = ok,
                )
                withContext(Dispatchers.Main) {
                    terminalLines = terminalLines + line + statusLine
                }
            }
            withContext(Dispatchers.Main) {
                terminalRunning = false
                // 执行完刷新列表（pm 操作可能改变应用状态；单条 pm 命令也刷新）
                val joined = cmds.joinToString(" ")
                if (cmds.size > 1 || joined.trimStart().startsWith("pm ")) refresh()
            }
        }
    }

    // ---------- 使用情况访问权限 ----------

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { toastMsg = "无法打开设置页" }
    }

    fun refreshUsagePerm() {
        hasUsagePerm = UsageStatsHelper.hasPermission(getApplication())
        if (hasUsagePerm) refresh()
    }

    /** 提取安装包 */
    fun requestExtract(entries: List<AppEntry>) {
        if (entries.isEmpty()) {
            toastMsg = "请先选择应用"
            return
        }
        if (settings.value.extractTreeUri.isBlank()) {
            pendingExtract = entries
            pickDirRequest = true
        } else {
            startExtract(entries)
        }
    }

    fun onDirPicked(context: Context, uri: Uri, label: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            store.update {
                putString(SettingsStore.Keys.EXTRACT_TREE_URI, uri.toString())
                putString(SettingsStore.Keys.EXTRACT_PATH_LABEL, label)
            }
            pendingExtract?.let { startExtract(it) }
            pendingExtract = null
            if (consumePendingApkScan()) scanApks()
        }
    }

    /** UI 消费一次目录选择请求 */
    fun consumePickDirRequest(): Boolean {
        val r = pickDirRequest
        pickDirRequest = false
        return r
    }

    // ---------- 安装包（APK）管理 ----------

    var apkList by mutableStateOf<List<ApkEntry>>(emptyList())
    var apkScanning by mutableStateOf(false)

    companion object {
        /** APK 扫描文件数上限，防大目录 OOM/ANR */
        private const val MAX_APK_SCAN = 2000
    }

    /** 请求扫描：无目录则走现有 SAF 选目录流程，选完自动扫 */
    fun requestApkScan() {
        if (settings.value.extractTreeUri.isBlank()) {
            pendingApkScan = true
            pickDirRequest = true
        } else {
            scanApks()
        }
    }

    private var pendingApkScan = false

    /** SAF 目录选完后，若有待处理的 APK 扫描则执行 */
    private fun consumePendingApkScan(): Boolean {
        val r = pendingApkScan
        pendingApkScan = false
        return r
    }

    /** 扫描安装包目录 */
    fun scanApks() {
        val ctx = getApplication<Application>()
        val uriStr = settings.value.extractTreeUri
        if (uriStr.isBlank()) {
            toastMsg = "请先选择安装包目录"
            return
        }
        if (apkScanning) return
        apkScanning = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val found = mutableListOf<ApkEntry>()
                    val root = androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(ctx, Uri.parse(uriStr))
                    if (root != null) {
                        collectApks(root, depth = 0, out = found)
                    }
                    val pm = ctx.packageManager
                    found.map { raw ->
                        var pkg: String? = null
                        var vName: String? = null
                        var vCode = 0L
                        // SAF uri 没有本地路径：复制到 cache 再解析；finally 保证临时文件删除
                        val tmp = File(ctx.cacheDir, "apkparse_${System.nanoTime()}.apk")
                        try {
                            runCatching {
                                ctx.contentResolver.openInputStream(raw.fileUri)?.use { ins ->
                                    FileOutputStream(tmp).use { outs -> ins.copyTo(outs) }
                                }
                            }
                            if (tmp.exists()) {
                                runCatching {
                                    val info = if (Build.VERSION.SDK_INT >= 33) {
                                        pm.getPackageArchiveInfo(
                                        tmp.absolutePath,
                                        PackageManager.PackageInfoFlags.of(0),
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        pm.getPackageArchiveInfo(tmp.absolutePath, 0)
                                    }
                                    pkg = info?.packageName
                                    vName = info?.versionName
                                    vCode = if (Build.VERSION.SDK_INT >= 28) {
                                        info?.longVersionCode ?: 0L
                                    } else {
                                        @Suppress("DEPRECATION")
                                        info?.versionCode?.toLong() ?: 0L
                                    }
                                }
                            }
                        } finally {
                            runCatching { tmp.delete() }
                        }
                        var installed = false
                        var outdated = false
                        if (pkg != null) {
                            runCatching {
                                val installedInfo = if (Build.VERSION.SDK_INT >= 33) {
                                    pm.getPackageInfo(pkg!!, PackageManager.PackageInfoFlags.of(0))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getPackageInfo(pkg!!, 0)
                                }
                                installed = true
                                val installedCode = if (Build.VERSION.SDK_INT >= 28) {
                                    installedInfo.longVersionCode
                                } else {
                                    @Suppress("DEPRECATION")
                                    installedInfo.versionCode.toLong()
                                }
                                outdated = vCode in 1..<installedCode
                            }
                        }
                        raw.copy(
                        packageName = pkg,
                        versionName = vName,
                        versionCode = vCode,
                        isInstalled = installed,
                        isOutdated = outdated,
                        )
                    }.sortedByDescending { it.fileSize }
                }
                apkList = result
                toastMsg = "扫描完成：${result.size} 个安装包"
            } catch (t: Throwable) {
                toastMsg = "扫描失败：${t.message}"
            } finally {
                apkScanning = false
            }
        }
    }

    /**
     * 递归收集 .apk（限 2 层深度、2000 个文件，防大目录卡死）。
     * @return true=达到上限被截断
     */
    private fun collectApks(
        dir: androidx.documentfile.provider.DocumentFile,
        depth: Int,
        out: MutableList<ApkEntry>,
    ): Boolean {
        if (depth > 2) return false
        if (out.size >= MAX_APK_SCAN) return true
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return false
        for (c in children) {
            if (out.size >= MAX_APK_SCAN) return true
            if (c.isDirectory) {
                if (collectApks(c, depth + 1, out)) return true
            } else if (c.name?.endsWith(".apk", ignoreCase = true) == true) {
                val uri = c.uri
                out.add(
                    ApkEntry(
                        fileName = c.name ?: uri.toString(),
                        fileSize = c.length(),
                        fileUri = uri,
                        packageName = null,
                        versionName = null,
                        versionCode = 0L,
                        isInstalled = false,
                        isOutdated = false,
                    ),
                )
            }
        }
        return false
    }

    /** 安装 APK：优先定向系统安装器（防第三方劫持），失败回退隐式 intent */
    fun installApk(context: Context, entry: ApkEntry) {
        fun buildIntent(pinned: Boolean) = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pinned) setPackage("com.android.packageinstaller")
        }
        try {
            context.startActivity(buildIntent(pinned = true))
        } catch (e: ActivityNotFoundException) {
            // 部分 ROM 无原生 packageinstaller，回退隐式 intent 让用户选择
            try {
                context.startActivity(buildIntent(pinned = false))
            } catch (t: Throwable) {
                toastMsg = "无法启动安装：${t.message}"
            }
        } catch (t: Throwable) {
            toastMsg = "无法启动安装：${t.message}"
        }
    }

    /** 删除安装包文件 */
    fun deleteApk(entry: ApkEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                androidx.documentfile.provider.DocumentFile
                    .fromSingleUri(getApplication(), entry.fileUri)?.delete() == true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (ok) {
                    apkList = apkList.filterNot { it.fileUri == entry.fileUri }
                    toastMsg = "已删除 ${entry.fileName}"
                } else {
                    toastMsg = "删除失败"
                }
            }
        }
    }

    /** 一键删除全部可删项（已安装且非低版本） */
    fun deleteAllDeletableApks() {
        val targets = apkList.filter { it.deletable }
        if (targets.isEmpty()) {
            toastMsg = "没有可删除的安装包"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val removedUris = mutableSetOf<Uri>()
            var ok = 0
            for (e in targets) {
                val deleted = runCatching {
                    androidx.documentfile.provider.DocumentFile
                        .fromSingleUri(getApplication(), e.fileUri)?.delete() == true
                }.getOrDefault(false)
                if (deleted) {
                    ok++
                    removedUris.add(e.fileUri)
                }
            }
            withContext(Dispatchers.Main) {
                apkList = apkList.filterNot { it.fileUri in removedUris }
                toastMsg = "已删除 $ok/${targets.size} 个安装包"
            }
        }
    }

    private fun startExtract(entries: List<AppEntry>) {
        val ctx = getApplication<Application>()
        val uriStr = settings.value.extractTreeUri
        if (uriStr.isBlank()) {
            pendingExtract = entries
            pickDirRequest = true
            return
        }
        viewModelScope.launch {
            progressTitle = "正在提取安装包"
            progress = ExtractProgress(0, entries.size, "")
            val res = extractApks(
                ctx,
                entries,
                Uri.parse(uriStr),
                settings.value.namingExpr,
                settings.value.includeSplits,
            ) { d, t, n -> progress = ExtractProgress(d, t, n) }
            progress = null
            exitSelectMode()
            toastMsg = if (res.failed.isEmpty()) {
                "提取完成：成功 ${res.ok} 个"
            } else {
                "提取完成：成功 ${res.ok} 个，失败 ${res.failed.size} 个"
            }
        }
    }

    fun showToast(msg: String) {
        toastMsg = msg
    }

    fun consumeToast(): String? {
        val m = toastMsg
        toastMsg = null
        return m
    }
}
