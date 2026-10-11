package dev.appinspector.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * 应用使用统计（最近一次打开时间），用于辅助判断系统应用是否常用。
 * 需要用户在系统设置中授予「使用情况访问权限」。
 */
object UsageStatsHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 返回包名 -> 最近使用时间戳（毫秒）的映射。
     * lastTimeUsed 为 0 表示统计周期内从未打开。
     * 查询过去 365 天，失败返回空映射。
     */
    fun queryLastUsed(context: Context): Map<String, Long> {
        if (!hasPermission(context)) return emptyMap()
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 365L * 24 * 60 * 60 * 1000
            usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, start, end)
                ?.associate { it.packageName to it.lastTimeUsed }
                ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    /** 格式化为「3天前 / 2个月前 / 从未打开」 */
    fun formatLastUsed(ts: Long?): String {
        if (ts == null) return "未知"
        if (ts <= 0) return "一年内从未打开"
        val days = (System.currentTimeMillis() - ts) / (24 * 60 * 60 * 1000)
        return when {
            days < 1 -> "今天"
            days < 30 -> "${days}天前"
            days < 365 -> "${days / 30}个月前"
            else -> "一年前"
        }
    }
}
