package dev.appinspector.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** 读取全部已安装应用（IO 线程）。需要 QUERY_ALL_PACKAGES 权限。 */
    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }
        // pm uninstall --user 0 卸载的包：仅在 MATCH_UNINSTALLED_PACKAGES 下可见
        @Suppress("DEPRECATION")
        val allPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_META_DATA or
                        PackageManager.MATCH_UNINSTALLED_PACKAGES).toLong()
                )
            )
        } else {
            pm.getInstalledPackages(
                PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
        }
        val installedNames = packages.map { it.packageName }.toHashSet()

        val result = ArrayList<AppEntry>(allPackages.size)
        for (pi in allPackages) {
            val isUninstalled = pi.packageName !in installedNames
            val ai: ApplicationInfo = pi.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val versionCode = PackageInfoCompat.getLongVersionCode(pi)
            val apkFiles = buildList {
                ai.sourceDir?.let { add(it) }
                ai.splitSourceDirs?.let { addAll(it) }
            }
            val apkSize = apkFiles.sumOf { path ->
                runCatching { File(path).length() }.getOrDefault(0L)
            }
            val setting = runCatching {
                pm.getApplicationEnabledSetting(pi.packageName)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            val isEnabled = setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED &&
                ai.enabled
            val appName = runCatching { ai.loadLabel(pm).toString() }
                .getOrDefault(pi.packageName)
            result.add(
                AppEntry(
                    packageName = pi.packageName,
                    appName = appName,
                    versionName = pi.versionName,
                    versionCode = versionCode,
                    apkSize = apkSize,
                    firstInstallTime = pi.firstInstallTime,
                    lastUpdateTime = pi.lastUpdateTime,
                    isSystem = isSystem,
                    isUpdatedSystemApp = isUpdatedSystemApp,
                    isEnabled = isEnabled,
                    isUninstalled = isUninstalled,
                    sourceDir = ai.sourceDir,
                    splitSourceDirs = ai.splitSourceDirs?.toList().orEmpty(),
                    applicationInfo = ai,
                )
            )
        }
        result
    }
}
