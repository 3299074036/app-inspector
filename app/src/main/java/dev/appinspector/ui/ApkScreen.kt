package dev.appinspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appinspector.data.ApkEntry
import dev.appinspector.ui.theme.AppType
import dev.appinspector.ui.theme.TileKind

/** 状态徽章：小圆角色块（无卡片） */
@Composable
private fun ApkBadge(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = AppType.label, color = fg)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkManagerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val list = vm.apkList
    val scanning = vm.apkScanning
    val settings by vm.settings.collectAsState()
    val hasDir = settings.extractTreeUri.isNotBlank()

    // 进页面自动扫一次（有目录且列表为空时）
    LaunchedEffect(hasDir) {
        if (hasDir && list.isEmpty() && !scanning) vm.scanApks()
    }

    val totalSize = list.sumOf { it.fileSize }
    val deletable = list.filter { it.deletable }
    // 删除确认弹窗状态
    var deleteTarget by remember { mutableStateOf<ApkEntry?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安装包管理", style = AppType.titleMd) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "返回")
                    }
                },
                actions = {
                    if (hasDir) {
                        IconButton(onClick = { vm.scanApks() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重新扫描")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (deletable.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = { showDeleteAllConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除全部可删项（${deletable.size} 个 · 释放 ${formatBytes(deletable.sumOf { it.fileSize })}）", style = AppType.titleMd)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasDir) {
                // 未选目录：提示选择
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", style = AppType.display)
                        Spacer(Modifier.height(12.dp))
                        Text("选择安装包所在目录", style = AppType.titleMd)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "将扫描该目录（含 2 层子目录）下的 .apk 文件",
                            style = AppType.bodySm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.requestApkScan() }) {
                            Text("选择目录", style = AppType.titleMd)
                        }
                    }
                }
            } else {
                // 顶部大数字
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${list.size}",
                            style = AppType.display,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "个安装包 · 共 ${formatBytes(totalSize)}",
                            style = AppType.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        if (deletable.isNotEmpty()) "${deletable.size} 个可删（已安装最新版）" else "暂无可删项",
                        style = AppType.bodySm,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                when {
                    scanning -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("正在扫描安装包…", style = AppType.bodyMd)
                            }
                        }
                    }
                    list.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("目录下没有找到 .apk 文件", style = AppType.bodyMd)
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(list, key = { it.fileUri.toString() }) { entry ->
                                ApkRow(
                                    entry = entry,
                                    onInstall = { vm.installApk(context, entry) },
                                    onDelete = { deleteTarget = entry },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 单个删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = DIALOG_SHAPE,
            title = { Text("删除安装包？") },
            text = { Text("${target.fileName}（${formatBytes(target.fileSize)}），删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteApk(target)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
    // 批量删除确认
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            shape = DIALOG_SHAPE,
            title = { Text("删除全部可删项？") },
            text = {
                Text(
                    "将删除 ${deletable.size} 个安装包，" +
                        "释放 ${formatBytes(deletable.sumOf { it.fileSize })}，删除后不可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllConfirm = false
                    vm.deleteAllDeletableApks()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ApkRow(
    entry: ApkEntry,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TileIcon("📦", TileKind.BLUE, 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.fileName,
                style = AppType.bodyMd,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildString {
                append(formatBytes(entry.fileSize))
                if (entry.versionName != null) append(" · v${entry.versionName}")
            }
            Text(
                meta,
                style = AppType.bodySm,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 状态徽章
        when {
            entry.isOutdated -> ApkBadge("低版本", cs.errorContainer, cs.onErrorContainer)
            entry.isInstalled -> ApkBadge("已安装", cs.secondaryContainer, cs.onSecondaryContainer)
            entry.packageName != null -> ApkBadge("未安装", cs.surfaceContainerHigh, cs.onSurfaceVariant)
        }
        // 操作按钮：第 1 次原版——有包名+未安装显示"安装"；deletable 显示"删除"
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!entry.isInstalled && entry.packageName != null) {
                TextButton(onClick = onInstall) { Text("安装", style = AppType.bodyMd) }
            }
            if (entry.deletable) {
                TextButton(onClick = onDelete) { Text("删除", style = AppType.bodyMd) }
            }
        }
    }
}
