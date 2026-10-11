package dev.appinspector.data

import android.content.Context
import org.json.JSONArray

/** UAD 社区精简建议 */
data class UadInfo(
    val packageName: String,
    /** Recommended / Advanced / Expert / Unsafe */
    val removal: String,
    val description: String,
) {
    val titleCn: String
        get() = when (removal) {
            "Recommended" -> "可删除"
            "Advanced" -> "谨慎删除"
            "Expert" -> "不建议删除"
            "Unsafe" -> "请勿删除"
            else -> removal
        }

    /** 标签副标题：说明含义 */
    val subtitleCn: String
        get() = when (removal) {
            "Recommended" -> "卸载无影响"
            "Advanced" -> "可能影响部分功能"
            "Expert" -> "可能导致系统异常"
            "Unsafe" -> "会导致系统故障"
            else -> ""
        }

    /** 是否建议动手（Recommended/Advanced） */
    val actionable: Boolean get() = removal == "Recommended" || removal == "Advanced"
}

/**
 * UAD（Universal Android Debloater）社区精简知识库。
 * 数据来自 assets/uad_lists.json，懒加载为内存映射。
 */
object UadDb {
    @Volatile
    private var cache: Map<String, UadInfo>? = null

    fun get(context: Context): Map<String, UadInfo> {
        cache?.let { return it }
        val map = mutableMapOf<String, UadInfo>()
        runCatching {
            context.assets.open("uad_lists.json").bufferedReader().use { r ->
                val arr = JSONArray(r.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "")
                    if (id.isEmpty()) continue
                    map[id] = UadInfo(
                        packageName = id,
                        removal = o.optString("removal", ""),
                        description = o.optString("desc", ""),
                    )
                }
            }
        }
        cache = map
        return map
    }

    fun query(context: Context, pkg: String): UadInfo? = get(context)[pkg]
}
