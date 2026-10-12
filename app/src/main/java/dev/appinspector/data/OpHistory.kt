package dev.appinspector.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 操作类型 */
object OpType {
    const val DISABLE = "disable"
    const val ENABLE = "enable"
    const val DELETE = "delete"
    const val RESTORE = "restore"
    const val CLEAR_DATA = "clear_data"
}

/** 操作方式 */
object OpMethod {
    const val MANUAL = "manual"   // 详情页单个操作
    const val BATCH = "batch"     // 多选批量
    const val AUTO = "auto"       // 无障碍自动停用
    const val OTA = "ota"         // OTA 恢复后重执行
}

/** 单条操作记录 */
data class OpRecord(
    val timeMs: Long,
    val pkg: String,
    val appName: String,
    val op: String,
    val method: String,
    val success: Boolean,
    /** 操作时应用体积（字节，用于报告统计释放空间） */
    val sizeBytes: Long = 0,
)

/**
 * 操作历史：记录每次停用/启用/删除/恢复/清数据的结果。
 * 存为 app 私有目录 op_history.json，最多保留 500 条。
 */
object OpHistory {
    private const val FILE = "op_history.json"
    private const val MAX = 500

    @Volatile
    private var cache: MutableList<OpRecord>? = null

    private fun file(context: Context): File = File(context.filesDir, FILE)

    @Synchronized
    fun load(context: Context): MutableList<OpRecord> {
        cache?.let { return it }
        val list = mutableListOf<OpRecord>()
        runCatching {
            val f = file(context)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        OpRecord(
                            timeMs = o.optLong("t"),
                            pkg = o.optString("pkg"),
                            appName = o.optString("name"),
                            op = o.optString("op"),
                            method = o.optString("method", OpMethod.MANUAL),
                            success = o.optBoolean("ok", true),
                            sizeBytes = o.optLong("size"),
                        )
                    )
                }
            }
        }
        // 按时间倒序
        list.sortByDescending { it.timeMs }
        cache = list
        return list
    }

    @Synchronized
    fun record(
        context: Context,
        pkg: String,
        appName: String,
        op: String,
        method: String,
        success: Boolean,
        sizeBytes: Long = 0,
    ) {
        val list = load(context)
        list.add(
            0, OpRecord(
                timeMs = System.currentTimeMillis(),
                pkg = pkg,
                appName = appName,
                op = op,
                method = method,
                success = success,
                sizeBytes = sizeBytes,
            )
        )
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(context, list)
    }

    @Synchronized
    fun clear(context: Context) {
        cache = mutableListOf()
        runCatching { file(context).delete() }
    }

    private fun save(context: Context, list: List<OpRecord>) {
        runCatching {
            val arr = JSONArray()
            for (r in list) {
                arr.put(
                    JSONObject()
                        .put("t", r.timeMs)
                        .put("pkg", r.pkg)
                        .put("name", r.appName)
                        .put("op", r.op)
                        .put("method", r.method)
                        .put("ok", r.success)
                        .put("size", r.sizeBytes)
                )
            }
            file(context).writeText(arr.toString())
        }
    }

    /** 操作类型中文名 */
    fun opTitle(op: String): String = when (op) {
        OpType.DISABLE -> "停用"
        OpType.ENABLE -> "启用"
        OpType.DELETE -> "删除"
        OpType.RESTORE -> "恢复"
        OpType.CLEAR_DATA -> "清数据"
        else -> op
    }

    /** 操作方式中文名 */
    fun methodTitle(method: String): String = when (method) {
        OpMethod.MANUAL -> "手动"
        OpMethod.BATCH -> "批量"
        OpMethod.AUTO -> "自动停用"
        OpMethod.OTA -> "OTA 恢复"
        else -> method
    }
}
