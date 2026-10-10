package dev.appinspector.data

import android.content.pm.ApplicationInfo

/** 列表中的一个应用条目 */
data class AppEntry(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    /** base.apk + 全部拆分包的长度之和 */
    val apkSize: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystem: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isEnabled: Boolean,
    val sourceDir: String?,
    val splitSourceDirs: List<String>,
    val applicationInfo: ApplicationInfo,
)

/** 排序维度 */
enum class SortKey(val title: String) {
    SIZE("按APK大小"),
    NAME("按应用名称"),
    PKG("按应用包名"),
    INSTALLED("按首次安装时间"),
    UPDATED("按最后更新时间"),
}

/** 列表筛选 */
enum class AppFilter(val title: String) {
    ALL("全部"),
    DISABLED("已停用"),
    DELETED("已删除"),
}
