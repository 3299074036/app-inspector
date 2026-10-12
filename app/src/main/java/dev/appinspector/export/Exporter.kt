package dev.appinspector.export

import dev.appinspector.data.AppEntry
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val title: String, val mime: String, val ext: String) {
    MD("Markdown (.md)", "text/markdown", "md"),
    TXT("纯文本 (.txt)", "text/plain", "txt"),
    XLSX("Excel (.xlsx)", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
}

data class ExportField(val key: String, val title: String)

val ALL_EXPORT_FIELDS = listOf(
    ExportField("name", "应用名称"),
    ExportField("pkg", "应用包名"),
    ExportField("ver", "版本名称"),
    ExportField("vc", "版本号"),
    ExportField("size", "APK 大小"),
    ExportField("upd", "最后更新时间"),
    ExportField("sys", "系统应用"),
    ExportField("state", "状态"),
)

/** 默认勾选的字段（用户需求的四个） */
val DEFAULT_EXPORT_KEYS = listOf("name", "pkg", "ver", "vc")

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 ->
        String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

fun formatDate(ts: Long): String =
    if (ts <= 0) "" else dateFmt.format(Date(ts))

fun fieldValue(e: AppEntry, key: String): String = when (key) {
    "name" -> e.appName
    "pkg" -> e.packageName
    "ver" -> e.versionName ?: ""
    "vc" -> e.versionCode.toString()
    "size" -> formatSize(e.apkSize)
    "upd" -> formatDate(e.lastUpdateTime)
    "sys" -> if (e.isSystem) "是" else "否"
    "state" -> if (e.isEnabled) "已启用" else "已停用"
    else -> ""
}

/** Markdown 表格 */
fun buildMarkdown(entries: List<AppEntry>, fields: List<ExportField>): String {
    val sb = StringBuilder()
    sb.append("| ").append(fields.joinToString(" | ") { it.title }).append(" |\n")
    sb.append(fields.joinToString("", "|", "|\n") { "---" })
    for (e in entries) {
        sb.append("| ")
            .append(fields.joinToString(" | ") {
                fieldValue(e, it.key).replace("|", "\\|").replace("\n", " ")
            })
            .append(" |\n")
    }
    return sb.toString()
}

/** TSV 纯文本 */
fun buildTsv(entries: List<AppEntry>, fields: List<ExportField>): String {
    val sb = StringBuilder()
    sb.append(fields.joinToString("\t") { it.title }).append("\n")
    for (e in entries) {
        sb.append(fields.joinToString("\t") {
            fieldValue(e, it.key).replace("\t", " ").replace("\n", " ").csvSafe()
        }).append("\n")
    }
    return sb.toString()
}

/** CSV 公式注入防护：= + - @ 开头加单引号 */
private fun String.csvSafe(): String =
    if (isNotEmpty() && this[0] in "=+-@") "'$this" else this

private fun String.xmlEsc(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun colName(i: Int): String {
    var n = i
    val sb = StringBuilder()
    do {
        sb.append(('A' + n % 26))
        n = n / 26 - 1
    } while (n >= 0)
    return sb.reverse().toString()
}

private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="应用列表" sheetId="1" r:id="rId1"/></sheets></workbook>"""

private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""

/**
 * 最小 xlsx writer：不引入 Apache POI，单元格使用 inlineStr，
 * Excel / WPS 均可正常打开。
 */
fun writeXlsx(entries: List<AppEntry>, fields: List<ExportField>, out: OutputStream) {
    ZipOutputStream(out).use { zip ->
        fun part(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        part("[Content_Types].xml", CONTENT_TYPES)
        part("_rels/.rels", RELS)
        part("xl/workbook.xml", WORKBOOK)
        part("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        fun row(r: Int, values: List<String>) {
            sb.append("<row r=\"").append(r).append("\">")
            values.forEachIndexed { i, v ->
                sb.append("<c r=\"").append(colName(i)).append(r)
                    .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(v.xmlEsc())
                    .append("</t></is></c>")
            }
            sb.append("</row>")
        }
        row(1, fields.map { it.title })
        entries.forEachIndexed { idx, e ->
            row(idx + 2, fields.map { fieldValue(e, it.key) })
        }
        sb.append("</sheetData></worksheet>")
        part("xl/worksheets/sheet1.xml", sb.toString())
    }
}
