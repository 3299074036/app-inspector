package dev.appinspector.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.pm.PackageManager
import dev.appinspector.data.AppEntry
import dev.appinspector.data.AppFilter
import dev.appinspector.data.AppRepository
import dev.appinspector.data.AppSettings
import dev.appinspector.data.SettingsStore
import dev.appinspector.data.SortKey
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
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractProgress(val done: Int, val total: Int, val name: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val store = SettingsStore(app)

    val settings: StateFlow<AppSettings> = store.settings

    var loading by mutableStateOf(true)
        private set
    private var allApps: List<AppEntry> = emptyList()

    var search by mutableStateOf("")
    var searching by mutableStateOf(false)
    var filter by mutableStateOf(AppFilter.ALL)

    var selectMode by mutableStateOf(false)
    val selected = mutableStateMapOf<String, AppEntry>()

    var showSort by mutableStateOf(false)
    var showExport by mutableStateOf(false)
    var detailEntry by mutableStateOf<AppEntry?>(null)

    // Shizuku
    var shizukuStatus by mutableStateOf<ShizukuManager.Status>(ShizukuManager.Status.NotInstalled)
        private set
    var showShizukuDialog by mutableStateOf(false)
    var confirmDeleteEntry by mutableStateOf<AppEntry?>(null)

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
    var toastMsg by mutableStateOf<String?>(null)
        private set
    var pickDirRequest by mutableStateOf(false)
        private set
    private var pendingExtract: List<AppEntry>? = null

    // 导出弹窗的临时状态
    var exportFields by mutableStateOf(DEFAULT_EXPORT_KEYS)
    var exportFormat by mutableStateOf(ExportFormat.MD)
    var exportScopeAll by mutableStateOf(false)

    init {
        refresh()
        ShizukuManager.addPermissionListener(shizukuPermissionListener)
        refreshShizukuStatus()
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
    fun setAppEnabled(entry: AppEntry, enabled: Boolean) {
        viewModelScope.launch {
            val res = ShizukuManager.setEnabled(getApplication(), entry.packageName, enabled)
            res.onSuccess {
                toastMsg = if (enabled) "已启用 ${entry.appName}" else "已停用 ${entry.appName}"
                // 乐观更新详情弹窗，随后 refresh() 会载入真实状态
                if (detailEntry?.packageName == entry.packageName) {
                    detailEntry = entry.copy(isEnabled = enabled)
                }
                refresh()
            }.onFailure {
                toastMsg = "操作失败：${it.message}"
            }
        }
    }

    /** 删除应用（pm uninstall --user 0，需 Shizuku），操作前请先弹窗确认 */
    fun uninstallApp(entry: AppEntry) {
        viewModelScope.launch {
            val res = ShizukuManager.uninstall(getApplication(), entry.packageName)
            res.onSuccess {
                toastMsg = "已删除 ${entry.appName}"
                confirmDeleteEntry = null
                detailEntry = null
                refresh()
            }.onFailure {
                toastMsg = "删除失败：${it.message}"
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                allApps = repo.loadApps()
            } catch (t: Throwable) {
                toastMsg = "加载失败：${t.message}"
            }
            loading = false
        }
    }

    /** 全部已安装应用数量（导出范围用） */
    fun totalCount(): Int = allApps.size

    /** 当前筛选 + 搜索 + 排序后的列表 */
    fun currentList(): List<AppEntry> {
        val s = settings.value
        var list = allApps
        if (!s.showSystem) list = list.filter { !it.isSystem }
        if (filter == AppFilter.DISABLED) list = list.filter { !it.isEnabled && !it.isUninstalled }
        if (filter == AppFilter.DELETED) list = list.filter { it.isUninstalled }
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
        }
        return list.sortedWith(if (s.sortAsc) comp else comp.reversed())
    }

    fun setShowSystem(v: Boolean) =
        updateSetting { putBoolean(SettingsStore.Keys.SHOW_SYSTEM, v) }

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
                withContext(Dispatchers.Main) { toastMsg = "已导出 ${entries.size} 条记录" }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toastMsg = "导出失败：${t.message}" }
            }
        }
    }

    fun exportFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "应用列表_$ts.${exportFormat.ext}"
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
        }
    }

    /** UI 消费一次目录选择请求 */
    fun consumePickDirRequest(): Boolean {
        val r = pickDirRequest
        pickDirRequest = false
        return r
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
