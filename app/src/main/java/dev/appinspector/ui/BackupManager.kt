package dev.appinspector.ui

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 配置备份记录 */
data class ConfigBackup(
    val file: File,
    val timeMs: Long,
    val disabled: List<String>,
    val deleted: List<String>,
)

/**
 * 配置备份管理器（从 AppViewModel 抽出）。
 * 负责备份文件的创建 / 列表 / 删除 / 导入，所有 IO 走 IO 线程，
 * 通过 version 自增通知 UI 刷新。
 */
class BackupManager(
    private val app: Application,
    private val scope: CoroutineScope,
    /** 当前快照：已停用 / 已删除包名（由 ViewModel 提供） */
    private val snapshotProvider: () -> Pair<List<String>, List<String>>,
    private val onToast: (String) -> Unit,
) {
    /** 备份版本号（自增触发 UI 刷新） */
    var version by mutableStateOf(0)
        private set

    fun bumpVersion() {
        version++
    }

    private fun backupDir(): File {
        val d = File(app.filesDir, "backups")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** 创建备份；同时在 Download 目录存一份（卸载 App 不丢失）。IO 线程执行，完成后 toast。 */
    fun createBackup() {
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val now = System.currentTimeMillis()
                val (disabled, deleted) = snapshotProvider()
                val o = JSONObject()
                    .put("time", now)
                    .put("disabled", JSONArray(disabled))
                    .put("deleted", JSONArray(deleted))
                val json = o.toString()
                File(backupDir(), "backup_${now}.json").writeText(json)
                // 公开目录再存一份：MediaStore Download，无需权限，卸载不丢
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "app-inspector-backup_${now}.json")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AppInspector")
                        }
                    }
                    val uri = app.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    )
                    if (uri != null) {
                        app.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }
                }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (ok) {
                    version++
                    onToast("备份成功")
                } else {
                    onToast("备份失败")
                }
            }
        }
    }

    /** 备份列表（按时间倒序） */
    fun listBackups(): List<ConfigBackup> {
        return runCatching {
            backupDir().listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
                ?.mapNotNull { f ->
                    runCatching {
                        val o = JSONObject(f.readText())
                        val ja = o.optJSONArray("disabled")
                        val jb = o.optJSONArray("deleted")
                        ConfigBackup(
                            file = f,
                            timeMs = o.optLong("time"),
                            disabled = (0 until (ja?.length() ?: 0)).map { ja.getString(it) },
                            deleted = (0 until (jb?.length() ?: 0)).map { jb.getString(it) },
                        )
                    }.getOrNull()
                }?.sortedByDescending { it.timeMs } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** 删除备份（含 Download 公开目录下的同名副本） */
    fun deleteBackup(f: File) {
        scope.launch(Dispatchers.IO) {
            runCatching { f.delete() }
            // 同时清理 Download/AppInspector 下的同名副本
            runCatching {
                val name = f.nameWithoutExtension.replace("backup_", "app-inspector-backup_") + ".json"
                val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                app.contentResolver.query(
                    uri, arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name),
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        app.contentResolver.delete(
                            ContentUris.withAppendedId(uri, id), null, null
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                version++
            }
        }
    }

    /** 从 JSON 文件导入备份（复制到备份目录），结果通过 toast 反馈 */
    fun importBackup(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            var tooLarge = false
            val result = runCatching {
                // 大小上限 1MB，防 OOM
                val size = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst() && idx >= 0) c.getLong(idx) else -1L
                } ?: -1L
                if (size > 1024 * 1024) {
                    tooLarge = true
                    return@runCatching null
                }
                val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@runCatching null
                // 校验格式
                val o = JSONObject(text)
                if (!o.has("disabled") || !o.has("deleted")) return@runCatching null
                val f = File(backupDir(), "backup_${System.currentTimeMillis()}.json")
                f.writeText(o.put("time", System.currentTimeMillis()).toString())
                f
            }.getOrNull()
            withContext(Dispatchers.Main) {
                when {
                    tooLarge -> onToast("文件过大，无法导入")
                    result != null -> {
                        version++
                        onToast("已导入备份")
                    }
                    else -> onToast("导入失败：文件格式不正确")
                }
            }
        }
    }
}
