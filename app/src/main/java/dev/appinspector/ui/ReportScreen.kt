package dev.appinspector.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import dev.appinspector.accessibility.AutoDisableState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
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
/** v6.7: 精简报告全屏页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = vm.historyVersion
    val records by androidx.compose.runtime.produceState<List<dev.appinspector.data.OpRecord>>(initialValue = emptyList(), version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            OpHistory.load(context).toList()
        }
    }
    val okRecords = remember(records) { records.filter { it.success } }
    val disabledPkgs = remember(okRecords) { okRecords.filter { it.op == OpType.DISABLE }.map { it.pkg }.toSet() }
    val deletedPkgs = remember(okRecords) { okRecords.filter { it.op == OpType.DELETE }.map { it.pkg }.toSet() }
    val restoredPkgs = remember(okRecords) { okRecords.filter { it.op == OpType.RESTORE }.map { it.pkg }.toSet() }
    val freedBytes = remember(okRecords) {
        okRecords.filter { it.op == OpType.DELETE }
            .distinctBy { it.pkg }
            .sumOf { it.sizeBytes }
    }
    val compose = remember(okRecords) {
        listOf(
            Triple("停用", okRecords.count { it.op == OpType.DISABLE }, Color(0xFFF5B453)),
            Triple("删除", okRecords.count { it.op == OpType.DELETE }, Color(0xFFFF7B72)),
            Triple("恢复", okRecords.count { it.op == OpType.RESTORE }, Color(0xFF79C0FF)),
            Triple("清数据", okRecords.count { it.op == OpType.CLEAR_DATA }, Color(0xFFB794F6)),
        ).filter { it.second > 0 }
    }
    val maxCompose = remember(compose) { compose.maxOfOrNull { it.second } ?: 1 }
    val top3 = remember(okRecords) {
        okRecords.filter { it.op == OpType.DELETE && it.sizeBytes > 0 }
            .distinctBy { it.pkg }
            .sortedByDescending { it.sizeBytes }
            .take(3)
    }
    val clipboard = LocalClipboardManager.current
    val reportText = remember(disabledPkgs, deletedPkgs, restoredPkgs, freedBytes, top3) {
        buildString {
            appendLine("📊 应用检查器 · 精简报告")
            appendLine("生成时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("累计释放空间：${formatBytes(freedBytes)}")
            appendLine("已停用：${disabledPkgs.size} 个")
            appendLine("已删除：${deletedPkgs.size} 个")
            appendLine("已恢复：${restoredPkgs.size} 个")
            if (top3.isNotEmpty()) {
                appendLine("释放空间 TOP：")
                top3.forEachIndexed { i, r ->
                    appendLine("${i + 1}. ${r.appName.ifBlank { r.pkg }}（${formatBytes(r.sizeBytes)}）")
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("精简报告", fontWeight = FontWeight.ExtraBold) },
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
            // 渐变横幅：累计释放空间
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF3B82F6), Color(0xFF1B6EF3)),
                        ),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Text(
                        formatBytes(freedBytes),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Text(
                        "累计释放空间（含应用数据清理）",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 三格统计
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    Triple(disabledPkgs.size, "已停用", Color(0xFFD97706)),
                    Triple(deletedPkgs.size, "已删除", Color(0xFFDC2626)),
                    Triple(restoredPkgs.size, "已恢复", Color(0xFF1B6EF3)),
                ).forEach { (n, label, color) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(n.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 操作构成
            if (compose.isNotEmpty()) {
                ContentCard {
                    Text("📊 操作构成", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    compose.forEach { (label, count, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 5.dp),
                        ) {
                            Text(label, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp),
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(count.toFloat() / maxCompose)
                                        .height(8.dp)
                                        .background(color, RoundedCornerShape(4.dp)),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(count.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // TOP 3
            if (top3.isNotEmpty()) {
                ContentCard {
                    Text("🏆 释放空间 TOP 3", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    top3.forEach { r ->
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.appName.ifBlank { r.pkg }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${r.pkg} · ${OpHistory.opTitle(r.op)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Text(formatBytes(r.sizeBytes), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // 底部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(reportText))
                        vm.showToast("报告已复制")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text("📋 复制报告") }
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(send, "分享精简报告"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("📤 分享") }
            }
        }
    }
}
