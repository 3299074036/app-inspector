package dev.appinspector.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.appinspector.accessibility.AutoDisableState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.appinspector.ui.theme.AppType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import dev.appinspector.ui.theme.TileKind
import dev.appinspector.ui.theme.brandGradient
import dev.appinspector.ui.theme.tileColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.appinspector.data.AppEntry
import dev.appinspector.data.AppFilter
import dev.appinspector.data.SuggestSubFilter
import dev.appinspector.data.AppIcon
import dev.appinspector.data.SortKey
import dev.appinspector.data.StorageHelper
import dev.appinspector.data.UadDb
import dev.appinspector.data.OpHistory
import dev.appinspector.data.OpType
import dev.appinspector.data.OpMethod
import dev.appinspector.data.UsageStatsHelper
import dev.appinspector.shizuku.ShizukuManager
import dev.appinspector.export.ALL_EXPORT_FIELDS
import dev.appinspector.export.ExportFormat
import dev.appinspector.export.formatDate
import dev.appinspector.export.formatSize
/** v6.7: 配置备份全屏页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = vm.backupManager.version
    val (disabledNow, deletedNow) = remember(version) { vm.currentSnapshot() }
    val backups by androidx.compose.runtime.produceState<List<ConfigBackup>>(initialValue = emptyList(), version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.backupManager.listBackups()
        }
    }
    val lastBackup = backups.firstOrNull()
    var restoreTarget by remember { mutableStateOf<ConfigBackup?>(null) }
    var deleteTarget by remember { mutableStateOf<ConfigBackup?>(null) }
    val timeFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    // 扫码分享
    var showShare by remember { mutableStateOf(false) }
    val pkgCount = disabledNow.size + deletedNow.size
    val shareText = remember(pkgCount) { vm.sharePlanText() }

    // 文件导入
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) vm.backupManager.importBackup(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置备份", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 当前快照
            ContentCard {
                Text("💾 当前快照", style = AppType.titleMd)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        disabledNow.size to "已停用",
                        deletedNow.size to "已删除",
                    ).forEach { (n, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(n.toString(), style = AppType.titleLg, color = MaterialTheme.colorScheme.primary)
                                Text(label, style = AppType.bodySm, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    if (lastBackup != null) "上次备份：${timeFmt.format(java.util.Date(lastBackup.timeMs))}"
                    else "尚未备份",
                    style = AppType.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            // 操作按钮
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GradientButton(
                    text = "＋ 立即备份",
                    onClick = { vm.backupManager.createBackup() },
                    modifier = Modifier.fillMaxWidth(),
                )
                // 扫码分享：把当前精简方案生成二维码
                OutlinedButton(
                    onClick = { showShare = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("📤 分享给朋友（扫码）")
                }
                if (showShare) {
                    ShareQrDialog(
                        text = shareText,
                        pkgCount = pkgCount,
                        onDismiss = { showShare = false },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 备份记录
            ContentCard {
                Text("📁 备份记录", style = AppType.titleMd)
                Spacer(Modifier.height(4.dp))
                if (backups.isEmpty()) {
                    Text(
                        "暂无备份记录",
                        style = AppType.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    backups.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { restoreTarget = b }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TileIcon("💾", TileKind.BLUE, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "备份 ${timeFmt.format(java.util.Date(b.timeMs))}",
                                    style = AppType.bodyMd,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "停用 ${b.disabled.size} · 删除 ${b.deleted.size}",
                                    style = AppType.bodySm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleteTarget = b }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除备份",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (b != backups.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch("application/json") }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TileIcon("📥", TileKind.GREEN, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text("从文件导入", style = AppType.bodyLg)
                        Text("选择 JSON 备份文件", style = AppType.bodySm, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", color = MaterialTheme.colorScheme.outline, style = AppType.bodyLg.copy(fontSize = 18.sp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 恢复预览 dialog
    restoreTarget?.let { b ->
        val findEntry: (String) -> AppEntry? = remember(version) { { pkg -> vm.findEntry(pkg) } }
        val toDisable = remember(b) { b.disabled.mapNotNull { findEntry(it) }.filter { it.isEnabled && !it.isUninstalled } }
        val toDelete = remember(b) { b.deleted.mapNotNull { findEntry(it) }.filter { !it.isUninstalled } }
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            shape = DIALOG_SHAPE,
            title = { Text("从备份恢复？") },
            text = {
                Column {
                    Text("备份时间：${timeFmt.format(java.util.Date(b.timeMs))}")
                    Spacer(Modifier.height(6.dp))
                    Text("将执行：停用 ${toDisable.size} 个，删除 ${toDelete.size} 个。")
                    if (toDisable.isNotEmpty() || toDelete.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            (toDisable + toDelete).take(5).joinToString("、") { it.appName } +
                                if (toDisable.size + toDelete.size > 5) " 等" else "",
                            style = AppType.bodySm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("需要 Shizuku 授权，操作会记入操作历史。", style = AppType.bodySm, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = null
                    vm.restoreFromBackup(toDisable, toDelete)
                }) { Text("开始恢复") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("取消") }
            },
        )
    }

    // 删除备份确认
    deleteTarget?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = DIALOG_SHAPE,
            title = { Text("删除这条备份？") },
            text = { Text("备份时间：${timeFmt.format(java.util.Date(b.timeMs))}，删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.backupManager.deleteBackup(b.file)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 二维码生成状态 */
private sealed interface QrResult {
    data object Loading : QrResult
    data class Ready(val bitmap: Bitmap) : QrResult
    data object Failed : QrResult
}

/** 扫码分享弹窗：把精简方案生成二维码，对方扫码后粘贴到清单 Tab 导入 */
@Composable
private fun ShareQrDialog(text: String, pkgCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 后台线程生成，避免主线程逐像素绘制卡顿
    val qr by produceState<QrResult>(initialValue = QrResult.Loading, text) {
        value = try {
            QrResult.Ready(withContext(Dispatchers.Default) { generateQrBitmap(text, 640) })
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            QrResult.Failed
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DIALOG_SHAPE,
        title = { Text("分享给朋友") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (pkgCount > 150) {
                    Text(
                        "清单过长（$pkgCount 个包），二维码可能扫不出，建议用复制文本",
                        style = AppType.bodySm,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                when (val q = qr) {
                    is QrResult.Ready -> {
                        Image(
                            bitmap = q.bitmap.asImageBitmap(),
                            contentDescription = "精简方案二维码",
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                        )
                    }
                    QrResult.Loading -> {
                        Box(
                            modifier = Modifier.size(240.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    QrResult.Failed -> {
                        Text("二维码生成失败，包名过多", style = AppType.bodyMd)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "对方用任意扫码工具扫描，得到包名清单后粘贴到「清单」Tab 导入即可",
                    style = AppType.bodySm,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("精简方案", text))
                android.widget.Toast.makeText(context, "清单已复制", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("复制文本") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** ZXing 生成二维码 Bitmap */
private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val writer = QRCodeWriter()
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
    )
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    // 批量 setPixels，比逐像素 setPixel 快一个数量级
    val pixels = IntArray(size * size)
    val black = AndroidColor.BLACK
    val white = AndroidColor.WHITE
    var i = 0
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[i++] = if (matrix[x, y]) black else white
        }
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp
}
