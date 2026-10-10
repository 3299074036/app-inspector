package dev.appinspector.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appinspector.data.AppEntry
import dev.appinspector.data.AppFilter
import dev.appinspector.data.AppIcon
import dev.appinspector.data.SortKey
import dev.appinspector.shizuku.ShizukuManager
import dev.appinspector.export.ALL_EXPORT_FIELDS
import dev.appinspector.export.ExportFormat
import dev.appinspector.export.formatDate
import dev.appinspector.export.formatSize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    vm: AppViewModel = viewModel(),
    onOpenExtractSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) vm.doExport(context, uri)
    }
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val label = runCatching {
                DocumentFile.fromTreeUri(context, uri)?.name ?: uri.toString()
            }.getOrDefault(uri.toString())
            vm.onDirPicked(context, uri, label)
        }
    }
    LaunchedEffect(vm.pickDirRequest) {
        if (vm.consumePickDirRequest()) dirLauncher.launch(null)
    }
    LaunchedEffect(vm.toastMsg) {
        vm.consumeToast()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (vm.selectMode) {
                TopAppBar(
                    title = { Text("已选 ${vm.selected.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { vm.exitSelectMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "取消多选")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("应用检查器") },
                    actions = {
                        IconButton(onClick = {
                            vm.searching = !vm.searching
                            if (!vm.searching) vm.search = ""
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("显示系统应用") },
                                trailingIcon = {
                                    Switch(
                                        checked = settings.showSystem,
                                        onCheckedChange = {
                                            vm.setShowSystem(it)
                                            menuOpen = false
                                        },
                                    )
                                },
                                onClick = {
                                    vm.setShowSystem(!settings.showSystem)
                                    menuOpen = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Shizuku（停用/删除）") },
                                trailingIcon = {
                                    ShizukuDot(vm.shizukuStatus)
                                },
                                onClick = {
                                    menuOpen = false
                                    vm.refreshShizukuStatus()
                                    vm.showShizukuDialog = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("排序") },
                                onClick = { menuOpen = false; vm.showSort = true },
                            )
                            DropdownMenuItem(
                                text = { Text("导出列表") },
                                onClick = { menuOpen = false; vm.showExport = true },
                            )
                            DropdownMenuItem(
                                text = { Text("批量提取安装包") },
                                onClick = { menuOpen = false; vm.enterSelectMode() },
                            )
                            DropdownMenuItem(
                                text = { Text("提取设置") },
                                onClick = { menuOpen = false; onOpenExtractSettings() },
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (vm.selectMode) {
                BottomAppBar {
                    Text(
                        "已选 ${vm.selected.size} 项",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { vm.exitSelectMode() },
                        enabled = vm.progress == null,
                    ) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { vm.requestExtract(vm.selected.values.toList()) },
                        enabled = vm.selected.isNotEmpty() && vm.progress == null,
                    ) { Text("提取安装包") }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (vm.searching && !vm.selectMode) {
                OutlinedTextField(
                    value = vm.search,
                    onValueChange = { vm.search = it },
                    placeholder = { Text("搜索应用名称 / 包名") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.search.isNotEmpty()) {
                            IconButton(onClick = { vm.search = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppFilter.entries.forEach { f ->
                    FilterChip(
                        selected = vm.filter == f,
                        onClick = { vm.filter = f },
                        label = { Text(f.title) },
                    )
                }
                Spacer(Modifier.weight(1f))
                val count = vm.currentList().size
                Text(
                    "共 $count 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                vm.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    val list = vm.currentList()
                    if (list.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("没有符合条件的应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(list, key = { it.packageName }) { entry ->
                                AppRow(
                                    entry = entry,
                                    selectMode = vm.selectMode,
                                    checked = vm.selected.containsKey(entry.packageName),
                                    onClick = {
                                        if (vm.selectMode) vm.toggleSelect(entry)
                                        else vm.detailEntry = entry
                                    },
                                    onLongClick = {
                                        if (!vm.selectMode) vm.enterSelectMode(entry)
                                    },
                                    onExtract = { vm.requestExtract(listOf(entry)) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (vm.showShizukuDialog) {
        ShizukuDialog(vm = vm, onDismiss = { vm.showShizukuDialog = false })
    }
    if (vm.showSort) {
        SortDialog(vm = vm, onDismiss = { vm.showSort = false })
    }
    if (vm.showExport) {
        ExportDialog(
            vm = vm,
            onLaunchExport = { fileName -> exportLauncher.launch(fileName) },
            onDismiss = { vm.showExport = false },
        )
    }
    vm.detailEntry?.let { entry ->
        AppDetailDialog(
            entry = entry,
            shizukuReady = vm.shizukuStatus == ShizukuManager.Status.Ready,
            onToggleEnabled = { vm.setAppEnabled(entry, !entry.isEnabled) },
            onDelete = { vm.confirmDeleteEntry = entry },
            onOpenShizuku = { vm.showShizukuDialog = true },
            onExtract = { vm.requestExtract(listOf(entry)); vm.detailEntry = null },
            onOpenSystemSettings = { vm.openAppSettings(context, entry.packageName) },
            onCopied = { vm.showToast("已复制") },
            onDismiss = { vm.detailEntry = null },
        )
    }
    vm.confirmDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { vm.confirmDeleteEntry = null },
            title = { Text("删除应用") },
            text = {
                Text(
                    "确定要删除「${entry.appName}」(${entry.packageName})吗？\n\n" +
                        "对应命令：pm uninstall --user 0 ${entry.packageName}\n" +
                        "系统应用的安装包会保留在系统分区，可恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.uninstallApp(entry) },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmDeleteEntry = null }) { Text("取消") }
            },
        )
    }
    vm.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在提取安装包") },
            text = {
                Column {
                    Text(
                        if (p.total > 0) "${p.name}（${p.done}/${p.total}）" else "准备中…",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    entry: AppEntry,
    selectMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExtract: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.appName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isSystem) {
                    Spacer(Modifier.width(6.dp))
                    TinyBadge(
                        "系统",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (!entry.isEnabled) {
                    Spacer(Modifier.width(6.dp))
                    TinyBadge(
                        "已停用",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (entry.isUninstalled) {
                    Spacer(Modifier.width(6.dp))
                    TinyBadge(
                        "已删除",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    "${entry.packageName} · ${entry.versionName ?: "?"}(${entry.versionCode})",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "${formatSize(entry.apkSize)} · 更新 ${formatDate(entry.lastUpdateTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            if (selectMode) {
                Checkbox(checked = checked, onCheckedChange = null)
            } else {
                AppIcon(entry)
            }
        },
        trailingContent = {
            if (!selectMode) {
                TextButton(onClick = onExtract) { Text("提取") }
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    )
}

@Composable
private fun TinyBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(color = container, shape = RoundedCornerShape(4.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun ShizukuDot(status: ShizukuManager.Status) {
    val color = if (status == ShizukuManager.Status.Ready) {
        Color(0xFF4CAF50)
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val status = vm.shizukuStatus
    val (title, body) = when (status) {
        ShizukuManager.Status.NotInstalled -> "Shizuku 未安装" to
            "停用/删除功能需要借助 Shizuku（免 Root 获得 ADB 级权限）。\n\n请先安装 Shizuku App（GitHub RikkaApps/Shizuku 或 F-Droid），然后回来继续。"
        ShizukuManager.Status.NotRunning -> "Shizuku 未运行" to
            "按以下步骤激活 Shizuku（Android 11+ 无需电脑）：\n\n" +
                "1. 设置 → 关于手机 → 连点 7 次「版本号」打开开发者选项\n" +
                "2. 开发者选项中打开「无线调试」\n" +
                "3. 打开 Shizuku App，按引导配对后点「启动」\n\n" +
                "注意：每次重启手机后需重新进 Shizuku 点「启动」。\n" +
                "ColorOS 如启动失败，请关闭「权限监控」。"
        ShizukuManager.Status.NoPermission -> "需要 Shizuku 授权" to
            "允许「应用检查器」使用 Shizuku，才能执行停用/启用/删除操作（效果等同 adb shell pm 命令）。"
        ShizukuManager.Status.Ready -> "Shizuku 已连接" to
            "现在可以在应用详情页直接停用/启用/删除应用，效果等同 adb shell pm 命令。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Row {
                when (status) {
                    ShizukuManager.Status.NotInstalled -> {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/RikkaApps/Shizuku/releases"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("去下载") }
                    }
                    ShizukuManager.Status.NotRunning -> {
                        TextButton(onClick = { vm.refreshShizukuStatus() }) { Text("刷新状态") }
                    }
                    ShizukuManager.Status.NoPermission -> {
                        TextButton(onClick = { vm.requestShizukuPermission() }) { Text("去授权") }
                    }
                    ShizukuManager.Status.Ready -> {}
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var key by remember { mutableStateOf(settings.sortKey) }
    var asc by remember { mutableStateOf(settings.sortAsc) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序") },
        text = {
            Column {
                SortKey.entries.forEach { k ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = key == k, onClick = { key = k })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == k, onClick = { key = k })
                        Text(k.title, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = asc,
                        onClick = { asc = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("升序") }
                    SegmentedButton(
                        selected = !asc,
                        onClick = { asc = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("降序") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.setSort(key, asc); onDismiss() }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ExportDialog(
    vm: AppViewModel,
    onLaunchExport: (fileName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember { mutableStateOf(vm.exportFields.toSet()) }
    var format by remember { mutableStateOf(vm.exportFormat) }
    var scopeAll by remember { mutableStateOf(false) }
    val currentCount = vm.currentList().size
    val totalCount = vm.totalCount()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出应用信息") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("字段", style = MaterialTheme.typography.titleSmall)
                ALL_EXPORT_FIELDS.forEach { f ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = f.key in checked,
                                onClick = {
                                    checked = if (f.key in checked) checked - f.key else checked + f.key
                                },
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = f.key in checked,
                            onCheckedChange = null,
                        )
                        Text(f.title, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("格式", style = MaterialTheme.typography.titleSmall)
                ExportFormat.entries.forEach { ef ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = format == ef, onClick = { format = ef })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = format == ef, onClick = { format = ef })
                        Text(ef.title, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("范围", style = MaterialTheme.typography.titleSmall)
                listOf(false to "当前列表（$currentCount 个）", true to "全部应用（$totalCount 个）")
                    .forEach { (all, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = scopeAll == all, onClick = { scopeAll = all })
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = scopeAll == all, onClick = { scopeAll = all })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.exportFields = ALL_EXPORT_FIELDS
                    .filter { it.key in checked }
                    .map { it.key }
                vm.exportFormat = format
                vm.exportScopeAll = scopeAll
                onDismiss()
                onLaunchExport(vm.exportFileName())
            }) { Text("导出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AppDetailDialog(
    entry: AppEntry,
    shizukuReady: Boolean,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onOpenShizuku: () -> Unit,
    onExtract: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onCopied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        onCopied()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.appName) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailRow("应用包名", entry.packageName, onCopy = ::copy)
                DetailRow("版本名称", entry.versionName ?: "未知", onCopy = ::copy)
                DetailRow("版本号", entry.versionCode.toString(), onCopy = ::copy)
                DetailRow("APK 大小", formatSize(entry.apkSize), onCopy = ::copy)
                DetailRow("首次安装", formatDate(entry.firstInstallTime), onCopy = ::copy)
                DetailRow("最后更新", formatDate(entry.lastUpdateTime), onCopy = ::copy)
                DetailRow("系统应用", if (entry.isSystem) "是" + if (entry.isUpdatedSystemApp) "（已更新）" else "" else "否", onCopy = ::copy)
                DetailRow(
                    "状态",
                    when {
                        entry.isUninstalled -> "已删除"
                        entry.isEnabled -> "已启用"
                        else -> "已停用"
                    },
                    onCopy = ::copy,
                )
                DetailRow("安装包路径", entry.sourceDir ?: "未知", onCopy = ::copy)
                if (entry.splitSourceDirs.isNotEmpty()) {
                    DetailRow("拆分包数量", entry.splitSourceDirs.size.toString(), onCopy = ::copy)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                // Shizuku 快捷操作：停用/启用/删除
                if (shizukuReady) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!entry.isUninstalled) {
                            FilledTonalButton(
                                onClick = onToggleEnabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (entry.isEnabled) "停用" else "启用")
                            }
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "删除",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        "经 Shizuku 执行，等同 adb shell pm 命令",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = onOpenShizuku) {
                        Text("停用 / 删除需要 Shizuku，点击去设置 →")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExtract) { Text("提取安装包") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenSystemSettings) { Text("系统设置") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: (String) -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(
                onClick = { onCopy(value) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制$label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
