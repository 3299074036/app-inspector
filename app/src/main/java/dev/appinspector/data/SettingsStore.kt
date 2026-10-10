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
    )

    fun update(transform: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(transform).apply()
        _settings.value = read()
    }
}
