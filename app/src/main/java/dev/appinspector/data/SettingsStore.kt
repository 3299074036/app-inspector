package dev.appinspector.data

import android.content.Context
import android.content.SharedPreferences
import dev.appinspector.export.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val showSystem: Boolean = false,
    val sortKey: SortKey = SortKey.NAME,
    val sortAsc: Boolean = true,
    /** 导出的字段 key */
    val exportFields: List<String> = listOf("name", "pkg", "ver", "vc"),
    val exportFormat: ExportFormat = ExportFormat.MD,
    /** SAF 目录树 URI（字符串形式），空表示未选择 */
    val extractTreeUri: String = "",
    val extractPathLabel: String = "",
    /** 命名表达式，如 {A}_{V}.apk */
    val namingExpr: String = "{A}_{V}.apk",
    val includeSplits: Boolean = true,
    /** 主题：system=跟随系统，light=浅色，dark=深色 */
    val themeMode: String = "system",
)

/**
 * SharedPreferences 实现的设置存储（手工构建：替代 DataStore）。
 */
class SettingsStore(context: Context) {

    object Keys {
        const val SHOW_SYSTEM = "show_system"
        const val SORT_KEY = "sort_key"
        const val SORT_ASC = "sort_asc"
        const val EXPORT_FIELDS = "export_fields"
        const val EXPORT_FORMAT = "export_format"
        const val EXTRACT_TREE_URI = "extract_tree_uri"
        const val EXTRACT_PATH_LABEL = "extract_path_label"
        const val NAMING_EXPR = "naming_expr"
        const val INCLUDE_SPLITS = "include_splits"
        const val THEME_MODE = "theme_mode"
        /** 用户经本应用停用过的包（用于 OTA 后恢复提醒） */
        const val TRACKED_DISABLED = "tracked_disabled"
        /** 用户经本应用删除过的包（用于 OTA 后恢复提醒） */
        const val TRACKED_UNINSTALLED = "tracked_uninstalled"
    }

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences("app_inspector_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings

    private fun read(): AppSettings = AppSettings(
        showSystem = prefs.getBoolean(Keys.SHOW_SYSTEM, false),
        sortKey = runCatching {
            SortKey.valueOf(prefs.getString(Keys.SORT_KEY, "NAME") ?: "NAME")
        }.getOrDefault(SortKey.NAME),
        sortAsc = prefs.getBoolean(Keys.SORT_ASC, true),
        exportFields = (prefs.getString(Keys.EXPORT_FIELDS, "name,pkg,ver,vc") ?: "")
            .split(",").filter { it.isNotBlank() },
        exportFormat = runCatching {
            ExportFormat.valueOf(prefs.getString(Keys.EXPORT_FORMAT, "MD") ?: "MD")
        }.getOrDefault(ExportFormat.MD),
        extractTreeUri = prefs.getString(Keys.EXTRACT_TREE_URI, "") ?: "",
        extractPathLabel = prefs.getString(Keys.EXTRACT_PATH_LABEL, "") ?: "",
        namingExpr = prefs.getString(Keys.NAMING_EXPR, "{A}_{V}.apk") ?: "{A}_{V}.apk",
        includeSplits = prefs.getBoolean(Keys.INCLUDE_SPLITS, true),
        themeMode = prefs.getString(Keys.THEME_MODE, "system") ?: "system",
    )

    fun update(transform: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(transform).apply()
        _settings.value = read()
    }

    // ---------- 用户操作追踪（OTA 恢复提醒用，不经过 StateFlow） ----------

    fun getTrackedDisabled(): Set<String> =
        prefs.getStringSet(Keys.TRACKED_DISABLED, emptySet()) ?: emptySet()

    fun getTrackedUninstalled(): Set<String> =
        prefs.getStringSet(Keys.TRACKED_UNINSTALLED, emptySet()) ?: emptySet()

    /** 记录用户停用；若曾记录为删除则移出删除集合 */
    fun trackDisabled(pkg: String) {
        prefs.edit()
            .putStringSet(Keys.TRACKED_DISABLED, getTrackedDisabled() + pkg)
            .putStringSet(Keys.TRACKED_UNINSTALLED, getTrackedUninstalled() - pkg)
            .apply()
    }

    /** 记录用户删除；若曾记录为停用则移出停用集合 */
    fun trackUninstalled(pkg: String) {
        prefs.edit()
            .putStringSet(Keys.TRACKED_UNINSTALLED, getTrackedUninstalled() + pkg)
            .putStringSet(Keys.TRACKED_DISABLED, getTrackedDisabled() - pkg)
            .apply()
    }

    /** 用户手动恢复启用：取消追踪 */
    fun untrack(pkg: String) {
        prefs.edit()
            .putStringSet(Keys.TRACKED_DISABLED, getTrackedDisabled() - pkg)
            .putStringSet(Keys.TRACKED_UNINSTALLED, getTrackedUninstalled() - pkg)
            .apply()
    }

    fun untrackAll(pkgs: Collection<String>) {
        if (pkgs.isEmpty()) return
        prefs.edit()
            .putStringSet(Keys.TRACKED_DISABLED, getTrackedDisabled() - pkgs.toSet())
            .putStringSet(Keys.TRACKED_UNINSTALLED, getTrackedUninstalled() - pkgs.toSet())
            .apply()
    }
}
