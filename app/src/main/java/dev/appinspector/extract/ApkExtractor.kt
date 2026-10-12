package dev.appinspector.extract

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.appinspector.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 命名表达式占位符：
 * {A} 应用名称  {V} 版本名称  {P} 应用包名  {C} 版本号
 */
fun buildApkFileName(entry: AppEntry, expr: String): String {
    val illegal = Regex("[\\\\/:*?\"<>|]")
    val safeName = entry.appName
        .replace(illegal, "_")
        .trim()
        .ifBlank { entry.packageName }
    val safeVersion = (entry.versionName ?: "unknown").replace(illegal, "_")
    val name = expr
        .replace("{A}", safeName)
        .replace("{V}", safeVersion)
        .replace("{P}", entry.packageName)
        .replace("{C}", entry.versionCode.toString())
        .trim()
        .ifBlank { safeName }
    return if (name.endsWith(".apk", ignoreCase = true)) name else "$name.apk"
}

data class ExtractResult(val ok: Int, val failed: List<String>)

/**
 * 把应用的安装包（base.apk + 可选拆分包）复制到用户选择的 SAF 目录。
 * 复制的是系统原包，签名保持不变。
 */
suspend fun extractApks(
    context: Context,
    entries: List<AppEntry>,
    treeUri: Uri,
    namingExpr: String,
    includeSplits: Boolean,
    onProgress: (done: Int, total: Int, name: String) -> Unit,
): ExtractResult = withContext(Dispatchers.IO) {
    val root = DocumentFile.fromTreeUri(context, treeUri)
        ?: return@withContext ExtractResult(0, entries.map { "${it.appName}（无法打开目标目录）" })
    var ok = 0
    val failed = mutableListOf<String>()
    entries.forEachIndexed { index, e ->
        onProgress(index, entries.size, e.appName)
        try {
            val baseName = buildApkFileName(e, namingExpr).removeSuffix(".apk")
            val sources = buildList {
                e.sourceDir?.let { add(it to "$baseName.apk") }
                if (includeSplits) {
                    e.splitSourceDirs.forEachIndexed { i, s ->
                        add(s to "$baseName.split_$i.apk")
                    }
                }
            }
            if (sources.isEmpty()) throw IllegalStateException("找不到安装包路径")
            for ((src, name) in sources) {
                val srcFile = File(src)
                if (!srcFile.exists()) throw IllegalStateException("源文件不存在")
                root.findFile(name)?.delete()
                val dest = root.createFile("application/vnd.android.package-archive", name)
                    ?: throw IllegalStateException("无法在目标目录创建文件")
                context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                    FileInputStream(srcFile).use { inp -> inp.copyTo(out) }
                } ?: throw IllegalStateException("无法写入目标文件")
            }
            ok++
        } catch (t: Throwable) {
            failed.add("${e.appName}（${t.message ?: "未知错误"}）")
        }
    }
    onProgress(entries.size, entries.size, "")
    ExtractResult(ok, failed)
}
