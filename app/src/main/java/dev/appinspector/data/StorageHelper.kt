package dev.appinspector.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import java.util.UUID

/** 应用存储占用 */
data class StorageSizes(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

/**
 * 查询应用存储占用（需"使用情况访问权限"）。
 */
object StorageHelper {

    fun querySizes(context: Context, pkg: String): StorageSizes? {
        if (!UsageStatsHelper.hasPermission(context)) return null
        return runCatching {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid: UUID = sm.getUuidForPath(context.filesDir)
            val stats = ssm.queryStatsForPackage(uuid, pkg, android.os.Process.myUserHandle())
            StorageSizes(
                appBytes = stats.appBytes,
                dataBytes = stats.dataBytes,
                cacheBytes = stats.cacheBytes,
            )
        }.getOrNull()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${bytes} B" else String.format("%.1f %s", v, units[i])
    }
}
