package dev.appinspector.data

import android.net.Uri

/** 安装包（APK 文件）条目：从用户授权的 SAF 目录扫描得到 */
data class ApkEntry(
    val fileName: String,
    val fileSize: Long,
    /** DocumentFile 的 uri，用于安装/删除 */
    val fileUri: Uri,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    /** 包名已安装 */
    val isInstalled: Boolean,
    /** 已安装但 apk 版本低于已安装版本 */
    val isOutdated: Boolean,
) {
    /** 可删：已安装且不是低版本（低版本留着可能有用，不进一键删除） */
    val deletable: Boolean get() = isInstalled && !isOutdated
}
