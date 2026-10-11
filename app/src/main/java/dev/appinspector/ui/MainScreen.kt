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
import dev.appinspector.data.UsageStatsHelper
import dev.appinspector.shizuku.ShizukuManager
import dev.appinspector.export.ALL_EXPORT_FIELDS
import dev.appinspector.export.ExportFormat
import dev.appinspector.export.formatDate
import dev.appinspector.export.formatSize

/** 底部导航 Tab */
private enum class MainTab(val title: String) {
    APPS("应用"),
    LISTS("清单"),
    SETTINGS("设置"),
}

private val DIALOG_SHAPE = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    vm: AppViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) vm.doExport(context, uri)
    }
    val scriptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-sh")
    ) { uri: Uri? ->
        if (uri != null) vm.doExportScript(context, uri)
    }
    LaunchedEffect(vm.exportScriptRequest) {
        if (vm.consumeExportScriptRequest()) scriptLauncher.launch(vm.exportScriptFileName())
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshUsagePerm()
                vm.refreshShizukuStatus()
                vm.refreshUnlockPkg()
                vm.refreshAccessibility()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tab by remember { mutableStateOf(MainTab.APPS) }

    // 启动时检查使用权限，未授权则弹窗申请
    LaunchedEffect(Unit) {
        vm.checkUsagePermOnLaunch()
    }

    // 终端全屏页
    if (vm.showTerminal) {
        TerminalScreen(vm = vm, onBack = { vm.showTerminal = false })
    } else {
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
                        actions = {
                            TextButton(onClick = { vm.selectAll(vm.currentList()) }) {
                                Text("全选")
                            }
                        },
                    )
                } else {
                    // v5.1: 大标题栏（对标手机管家），功能按钮保留在同一行右侧
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (tab) {
                                MainTab.APPS -> "应用检查器"
                                MainTab.LISTS -> "精简清单"
                                MainTab.SETTINGS -> "设置"
                            },
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (tab == MainTab.APPS) {
                            // 显示系统应用开关（紧凑版）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "显示系统应用",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(2.dp))
                                Switch(
                                    checked = settings.showSystem,
                                    onCheckedChange = { vm.setShowSystem(it) },
                                )
                            }
                            IconButton(onClick = { vm.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (vm.selectMode) {
                    // 悬浮胶囊操作条：根据选中项状态动态显示可用操作
                    val sel = vm.selected.values.toList()
                    val toEnable = sel.filter { !it.isEnabled && !it.isUninstalled }
                    val toDisable = sel.filter { it.isEnabled && !it.isUninstalled }
                    val toDelete = sel.filter { !it.isUninstalled }
                    val toRestore = sel.filter { it.isUninstalled && it.isSystem }
                    val toExtract = sel.filter { !it.isUninstalled }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 8.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val shizukuReady =
                                    vm.shizukuStatus == ShizukuManager.Status.Ready
                                val canBatch =
                                    vm.selected.isNotEmpty() && vm.progress == null
                                if (shizukuReady) {
                                    if (toEnable.isNotEmpty()) {
                                        TextButton(
                                            onClick = { vm.batchSetEnabled(toEnable, true) },
                                            enabled = canBatch,
                                        ) {
                                            Text(
                                                "启用 ${toEnable.size}",
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                            )
                                        }
                                    }
                                    if (toDisable.isNotEmpty()) {
                                        TextButton(
                                            onClick = { vm.batchSetEnabled(toDisable, false) },
                                            enabled = canBatch,
                                        ) {
                                            Text(
                                                "停用 ${toDisable.size}",
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                            )
                                        }
                                    }
                                    if (toDelete.isNotEmpty()) {
                                        TextButton(
                                            onClick = { vm.confirmBatchDelete = toDelete },
                                            enabled = canBatch,
                                        ) {
                                            Text(
                                                "删除 ${toDelete.size}",
                                                color = Color(0xFFFF8A80),
                                            )
                                        }
                                    }
                                    if (toRestore.isNotEmpty()) {
                                        TextButton(
                                            onClick = { vm.batchRestore(toRestore) },
                                            enabled = canBatch,
                                        ) {
                                            Text(
                                                "恢复 ${toRestore.size}",
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                            )
                                        }
                                    }
                                }
                                if (toExtract.isNotEmpty()) {
                                    TextButton(
                                        onClick = { vm.requestExtract(toExtract) },
                                        enabled = canBatch,
                                    ) {
                                        Text(
                                            "提取",
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // v6.5: 半透明毛玻璃胶囊导航（列表从下方透出），颜色跟随深浅主题
                    val isDarkNav = when (settings.themeMode) {
                        "light" -> false
                        "dark" -> true
                        else -> isSystemInDarkTheme()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 56.dp)
                            .padding(top = 6.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDarkNav) Color(0xFF23262B).copy(alpha = 0.72f)
                            else Color.White.copy(alpha = 0.72f),
                            shadowElevation = 8.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 8.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PillNavItem(
                                    selected = tab == MainTab.APPS,
                                    onClick = { tab = MainTab.APPS },
                                    icon = Icons.Outlined.Apps,
                                    label = "应用",
                                    isDark = isDarkNav,
                                )
                                PillNavItem(
                                    selected = tab == MainTab.LISTS,
                                    onClick = { tab = MainTab.LISTS },
                                    icon = Icons.Outlined.PlaylistAddCheck,
                                    label = "清单",
                                    isDark = isDarkNav,
                                )
                                PillNavItem(
                                    selected = tab == MainTab.SETTINGS,
                                    onClick = { tab = MainTab.SETTINGS },
                                    icon = Icons.Outlined.Settings,
                                    label = "设置",
                                    isDark = isDarkNav,
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (tab == MainTab.APPS && !vm.selectMode) {
                    FloatingActionButton(
                        onClick = {
                            if (vm.shizukuStatus == ShizukuManager.Status.Ready) {
                                vm.showTerminal = true
                            } else {
                                vm.refreshShizukuStatus()
                                vm.showShizukuDialog = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Filled.Terminal, contentDescription = "Shizuku 终端")
                    }
                }
            },
        ) { padding ->
            when (tab) {
                MainTab.APPS -> AppListTab(vm = vm, modifier = Modifier.padding(padding))
                MainTab.LISTS -> DebloatTab(
                    vm = vm,
                    onExportScript = { vm.requestExportScript() },
                    modifier = Modifier.padding(padding),
                )
                MainTab.SETTINGS -> SettingsTab(
                    vm = vm,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    // ---------- 弹窗 ----------
    if (vm.showUsagePermRequest) {
        AlertDialog(
            onDismissRequest = { vm.showUsagePermRequest = false },
            shape = DIALOG_SHAPE,
            title = { Text("授予使用情况访问权限") },
            text = {
                Text(
                    "用于显示应用的最近使用时间、按使用时间排序，以及查看存储占用。\n\n" +
                        "点击「去授权」将跳转到系统设置页面，请找到「应用检查器」并允许访问使用情况。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.showUsagePermRequest = false
                    vm.openUsageAccessSettings(context)
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showUsagePermRequest = false }) { Text("暂不") }
            },
        )
    }
    if (vm.showShizukuDialog) {
        ShizukuDialog(vm = vm, onDismiss = { vm.showShizukuDialog = false })
    }
    if (vm.showAutoDisableDetail) {
        AutoDisableDetailDialog(vm = vm, onDismiss = { vm.showAutoDisableDetail = false })
    }
    if (vm.showSortSheet) {
        SortBottomSheet(vm = vm, onDismiss = { vm.showSortSheet = false })
    }
    if (vm.showThemeDialog) {
        val modes = listOf("system", "light", "dark")
        AlertDialog(
            onDismissRequest = { vm.showThemeDialog = false },
            shape = DIALOG_SHAPE,
            title = { DialogHeader("🌙", TileKind.PURPLE, "深色模式") },
            text = {
                Column {
                    modes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.themeMode == mode,
                                    onClick = {
                                        vm.setThemeMode(mode)
                                        vm.showThemeDialog = false
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                RadioButton(
                                    selected = settings.themeMode == mode,
                                    onClick = {
                                        vm.setThemeMode(mode)
                                        vm.showThemeDialog = false
                                    },
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(vm.themeModeTitle(mode), fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.showThemeDialog = false }) { Text("取消") }
            },
        )
    }
    if (vm.showExport) {
        ExportDialog(
            vm = vm,
            onLaunchExport = { fileName -> exportLauncher.launch(fileName) },
            onDismiss = { vm.showExport = false },
        )
    }
    if (vm.showExtractSettings) {
        ExtractSettingsDialog(
            vm = vm,
            onDismiss = { vm.showExtractSettings = false },
        )
    }
    vm.detailEntry?.let { entry ->
        DetailSheet(
            vm = vm,
            entry = entry,
            shizukuReady = vm.shizukuStatus == ShizukuManager.Status.Ready,
            onToggleEnabled = { vm.setAppEnabled(entry, !entry.isEnabled) },
            onDelete = { vm.confirmDeleteEntry = entry },
            onRestore = { vm.restoreApp(entry) },
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
            shape = DIALOG_SHAPE,
            title = { Text("删除应用") },
            text = {
                Text(
                    "确定要删除「${entry.appName}」(${entry.packageName})吗？\n\n" +
                        "对应命令：pm uninstall --user 0 ${entry.packageName}\n" +
                        "系统应用的安装包会保留在系统分区，可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.uninstallApp(entry) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmDeleteEntry = null }) { Text("取消") }
            },
        )
    }
    vm.confirmClearData?.let { entry ->
        AlertDialog(
            onDismissRequest = { vm.confirmClearData = null },
            shape = DIALOG_SHAPE,
            title = { Text("清除应用数据") },
            text = {
                Text(
                    "确定要清除「${entry.appName}」的所有数据吗？\n\n" +
                        "对应命令：pm clear --user 0 ${entry.packageName}\n" +
                        "这将删除该应用的全部用户数据（含缓存），效果等同恢复出厂设置，不可撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearAppData(entry) }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmClearData = null }) { Text("取消") }
            },
        )
    }
    vm.confirmBatchDelete?.let { entries ->
        AlertDialog(
            onDismissRequest = { vm.confirmBatchDelete = null },
            shape = DIALOG_SHAPE,
            title = { Text("批量删除") },
            text = {
                Text(
                    "确定要删除选中的 ${entries.size} 个应用吗？\n\n" +
                        entries.take(5).joinToString("\n") { "· ${it.appName}" } +
                        (if (entries.size > 5) "\n…等 ${entries.size} 个" else "") +
                        "\n\n对应命令：pm uninstall --user 0 <包名>\n" +
                        "系统应用的安装包会保留在系统分区，可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.batchUninstall(entries) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirmBatchDelete = null }) { Text("取消") }
            },
        )
    }
    vm.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            shape = DIALOG_SHAPE,
            title = { Text(vm.progressTitle.ifBlank { "正在处理" }) },
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

// ================= 应用列表 Tab =================

@Composable
private fun AppListTab(vm: AppViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val pagerState = rememberPagerState { AppFilter.entries.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // pager 滑动 -> 更新 filter
    LaunchedEffect(pagerState.currentPage) {
        val f = AppFilter.entries[pagerState.currentPage]
        if (vm.filter != f) vm.filter = f
    }
    // filter 变化（点击 Tab） -> 滚动 pager
    LaunchedEffect(vm.filter) {
        val idx = AppFilter.entries.indexOf(vm.filter)
        if (pagerState.currentPage != idx) pagerState.scrollToPage(idx)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索框（填充式无边框）+ 排序按钮
        val isDefaultSort = settings.sortKey == SortKey.NAME && settings.sortAsc
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.TextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                placeholder = { Text("搜索应用名称 / 包名", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (vm.search.isNotEmpty()) {
                        IconButton(onClick = { vm.search = "" }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "清空", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .shadow(3.dp, RoundedCornerShape(20.dp)),
            )
            // 排序按钮
            FilledTonalButton(
                onClick = { vm.showSortSheet = true },
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = ButtonDefaults.filledTonalButtonElevation(2.dp),
            ) {
                Icon(Icons.Filled.Sort, contentDescription = "排序", modifier = Modifier.size(20.dp))
            }
        }
        // 当前排序小字（SortKey.title 自带"按"字）
        if (!isDefaultSort) {
            Text(
                "${settings.sortKey.title} · ${if (settings.sortAsc) "升序" else "降序"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        // 4 个文字 Tab（均匀分布，带个数）
        FilterTabRow(
            options = AppFilter.entries.map { it.title },
            counts = AppFilter.entries.map { vm.countFor(it) },
            selectedIndex = pagerState.currentPage,
            onSelect = {
                scope.launch { pagerState.animateScrollToPage(it) }
            },
        )

        // 建议精简页：按 UAD 建议等级子筛选（全部 / 可删除 / 谨慎删除 / 不建议删除 / 请勿删除）
        if (vm.filter == AppFilter.SUGGEST) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestSubFilter.entries.forEach { sub ->
                    FilterChip(
                        selected = vm.suggestSubFilter == sub,
                        onClick = { vm.suggestSubFilter = sub },
                        label = { Text(sub.title) },
                    )
                }
            }
        }

        // OTA 恢复提醒
        if (vm.restoredApps.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "检测到 ${vm.restoredApps.size} 个应用被系统恢复（可能因 OTA 更新）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shizukuReady =
                            vm.shizukuStatus == ShizukuManager.Status.Ready
                        TextButton(
                            onClick = {
                                if (shizukuReady) vm.reapplyRestored()
                                else vm.showShizukuDialog = true
                            },
                        ) { Text("重新执行") }
                        TextButton(onClick = { vm.ignoreRestored() }) {
                            Text("忽略")
                        }
                    }
                }
            }
        }

        when {
            vm.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                // 左右滑动切换 Tab
                // key(settings.showSystem)：开关切换时强制重组页面，解决 pager 缓存不刷新
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val f = AppFilter.entries[page]
                    androidx.compose.runtime.key(settings.showSystem, f) {
                        AppListPage(vm = vm, filter = f)
                    }
                }
            }
        }
    }
}

/** 单页应用列表（pager 内） */
@Composable
private fun AppListPage(vm: AppViewModel, filter: AppFilter) {
    val context = LocalContext.current
    // 订阅 settings，排序/显示系统应用变化时自动重组刷新
    val settings by vm.settings.collectAsState()
    val baseList = remember(settings, filter, vm.search) { vm.listFor(filter) }
    // 建议精简页叠加子筛选（按 UAD 建议等级）
    val sub = vm.suggestSubFilter
    val list = remember(baseList, sub, filter) {
        if (filter != AppFilter.SUGGEST || sub == SuggestSubFilter.ALL) {
            baseList
        } else {
            baseList.filter { entry ->
                val removal = UadDb.query(context, entry.packageName)?.removal
                when (sub) {
                    SuggestSubFilter.DELETABLE -> removal == "Recommended"
                    SuggestSubFilter.CAREFUL -> removal == "Advanced"
                    SuggestSubFilter.NOT_RECOMMENDED -> removal == "Expert"
                    SuggestSubFilter.DO_NOT -> removal == "Unsafe"
                    SuggestSubFilter.ALL -> true
                }
            }
        }
    }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when {
                    filter == AppFilter.SUGGEST && sub != SuggestSubFilter.ALL ->
                        "「${sub.title}」分类下没有应用"
                    filter == AppFilter.SUGGEST -> "没有命中社区建议的应用"
                    else -> "没有符合条件的应用"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp,
            ),
            // v6.1: 扁平列表，间距由 item 内边距保证，此处不再额外 spacedBy
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 建议精简页顶部提示：跟随子筛选显示该等级的具体影响
            if (filter == AppFilter.SUGGEST && !vm.selectMode) {
                item {
                    SuggestHint(sub = sub, count = list.size)
                }
            }
            items(list, key = { it.packageName }) { entry ->
                if (filter == AppFilter.SUGGEST) {
                    SuggestAppCard(
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
                    )
                } else {
                    AppCard(
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
                    )
                }
            }
        }
    }
}

/** 卡片化应用行 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
/** v6.1: 首页去卡片底纹，改扁平列表 */
private fun AppCard(
    entry: AppEntry,
    selectMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        AppIcon(entry, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.packageName} · ${formatSize(entry.apkSize)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (entry.isSystem) {
                    TinyBadge(
                        "系统",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (!entry.isEnabled) {
                    TinyBadge(
                        "已停用",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (entry.isUninstalled) {
                    TinyBadge(
                        "已删除",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

/** 文字 Tab（均匀分布，B 站式下划线指示器，带个数） */
@Composable
private fun FilterTabRow(
    options: List<String>,
    counts: List<Int>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$label ${counts.getOrNull(i) ?: 0}",
                    fontSize = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else null,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}

/** 建议精简页顶部提示：按子筛选显示该等级的具体影响 */
@Composable
private fun SuggestHint(sub: SuggestSubFilter, count: Int) {
    if (sub == SuggestSubFilter.ALL) {
        // 全部档：只保留一句手势提示（tab 上已有计数）
        Text(
            "长按多选可批量停用 / 删除 / 恢复 / 提取",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    } else {
        val (text, bg, fg) = when (sub) {
            SuggestSubFilter.DELETABLE ->
                Triple("✅ 卸载无影响，可放心删除", Color(0xFFE3F2FD), Color(0xFF1565C0))
            SuggestSubFilter.CAREFUL ->
                Triple("⚠️ 可能影响部分功能，建议先停用观察", Color(0xFFFFF8E1), Color(0xFFF57F00))
            SuggestSubFilter.NOT_RECOMMENDED ->
                Triple("⛔ 可能导致系统异常，非必要不动", Color(0xFFFFEBEE), Color(0xFFC62828))
            SuggestSubFilter.DO_NOT ->
                Triple("🚫 会导致系统故障，请勿操作", Color(0xFFFCE4EC), Color(0xFFB71C1C))
            else -> Triple("", Color.Transparent, Color.Transparent)
        }
        Surface(
            color = bg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                "$text（共 $count 个）",
                style = MaterialTheme.typography.bodySmall,
                color = fg,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** 建议精简行的卡片：显示 UAD 建议等级徽标，手势与其他 Tab 统一（点击详情/长按多选） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestAppCard(
    entry: AppEntry,
    selectMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val uadInfo = remember(entry.packageName) {
        UadDb.query(context, entry.packageName)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (checked) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectMode) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            AppIcon(entry, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entry.packageName} · ${formatSize(entry.apkSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uadInfo != null) {
                        val (bc, cc) = uadBadgeColors(uadInfo.removal)
                        TinyBadge(uadInfo.titleCn, bc, cc)
                    }
                    if (!entry.isEnabled) {
                        TinyBadge(
                            "已停用",
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (entry.isUninstalled) {
                        TinyBadge(
                            "已删除",
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TinyBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(color = container, shape = RoundedCornerShape(20.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

/** UAD 建议等级徽标颜色：可删除=蓝，谨慎删除=灰绿，不建议删除=浅红，请勿删除=深红 */
@Composable
private fun uadBadgeColors(removal: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (removal) {
        "Recommended" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        "Advanced" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        "Expert" -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        "Unsafe" -> MaterialTheme.colorScheme.error to
            MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
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

// ================= 精简清单 Tab =================

/** 分组标题 */
/** v5.1: 悬浮胶囊导航项（竖排，对齐设计图） */
@Composable
private fun PillNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val fg = if (selected) {
        if (isDark) Color.White else Color(0xFF111111)
    } else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = fg)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = fg,
            )
        }
    }
}

/** v5: 品牌蓝渐变主按钮 */
@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brush = if (enabled) brandGradient()
    else Brush.horizontalGradient(
        listOf(Color(0xFFE2E5E9), Color(0xFFE2E5E9)),
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush, RoundedCornerShape(15.dp))
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

/** v5: 彩色圆角图标块（emoji 版） */
@Composable
private fun TileIcon(
    emoji: String,
    kind: TileKind,
    size: androidx.compose.ui.unit.Dp = 38.dp,
) {
    val (bg, _) = tileColors(kind)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** 内容卡片 */
@Composable
private fun ContentCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

/** v6: 清单页工作流步骤条（导入→解析→停用→完成） */
@Composable
private fun WorkflowSteps(
    importDone: Boolean,
    parseDone: Boolean,
    disableDone: Boolean,
    modifier: Modifier = Modifier,
) {
    val labels = listOf("导入", "解析", "停用", "完成")
    val doneList = listOf(importDone, parseDone, disableDone, disableDone)
    val blue = Color(0xFF1B6EF3)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
    ) {
        // 连接线：4 等分列，圆点中心在 1/8、3/8、5/8、7/8 处
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(0.5f))
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (doneList[i + 1]) blue else Color(0xFFE4E7EB),
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
            Spacer(Modifier.weight(0.5f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val done = doneList[i]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    if (done) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(brandGradient(), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✓",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(2.dp, Color(0xFFE4E7EB)),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${i + 1}",
                                    color = Color(0xFFB6BBC2),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (done) blue else Color(0xFF8A8F98),
                        fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** v6: 解析结果四格统计 */
@Composable
private fun ParseStatGrid(
    onPhone: Int,
    canDisable: Int,
    disabled: Int,
    notFound: Int,
) {
    val items = listOf(
        Triple("$onPhone", "在手机上", null),
        Triple("$canDisable", "可停用", null),
        Triple("$disabled", "已停用", Color(0xFF16A34A)),
        Triple("$notFound", "未找到", null),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAF8), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
    ) {
        items.forEachIndexed { i, (num, label, numColor) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    num,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = numColor ?: MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    label,
                    fontSize = 11.sp,
                    color = Color(0xFF8A8F98),
                )
            }
            if (i < items.size - 1) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(Color(0xFFECEEF1)),
                )
            }
        }
    }
}

/** v6: 红框删除按钮（描边 + 浅红底） */
@Composable
private fun DangerOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val red = Color(0xFFDC2626)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFFBFB),
            disabledContainerColor = Color(0xFFF5F5F5),
            contentColor = red,
            disabledContentColor = Color(0xFFB6BBC2),
        ),
        border = BorderStroke(1.5.dp, if (enabled) Color(0xFFFECACA) else Color(0xFFE4E7EB)),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** v6: 弹窗图标头（44px 彩色底 + 18px 加粗标题） */
@Composable
private fun DialogHeader(
    emoji: String,
    kind: TileKind,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TileIcon(emoji, kind, 44.dp)
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** v6.5: 弹窗分区标题（16sp 加粗 + 左侧蓝条） */
@Composable
private fun DialogSectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(Color(0xFF1B6EF3), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
        )
    }
}

/** v6: 小渐变主按钮（用于弹窗确认） */
@Composable
private fun MiniGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .background(brandGradient(), RoundedCornerShape(12.dp))
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DebloatTab(
    vm: AppViewModel,
    onExportScript: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val items = vm.debloatItems
    val shizukuReady = vm.shizukuStatus == ShizukuManager.Status.Ready
    // v6: 工作流进度推导
    val importDone = text.isNotBlank() || items.isNotEmpty()
    val parseDone = items.isNotEmpty()
    val canDisablePreview = items.mapNotNull { it.entry }
        .filter { it.isEnabled && !it.isUninstalled }
    val disableDone = parseDone && canDisablePreview.isEmpty()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // v6: 工作流步骤条
        WorkflowSteps(
            importDone = importDone,
            parseDone = parseDone,
            disableDone = disableDone,
        )

        // OTA 恢复提醒（有才显示）
        if (vm.restoredApps.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "有 ${vm.restoredApps.size} 个应用被系统恢复",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "可能因 OTA 系统更新导致",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (shizukuReady) vm.reapplyRestored()
                                else vm.showShizukuDialog = true
                            },
                        ) { Text("重新执行") }
                        TextButton(onClick = { vm.ignoreRestored() }) { Text("忽略") }
                    }
                }
            }
        }

        // 导入：核心 workflow 置顶
        ContentCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TileIcon("📋", TileKind.BLUE)
                Column(Modifier.weight(1f)) {
                    Text(
                        "从文本导入",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "支持包名 / pm 命令 / adb 命令，自动提取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("粘贴包名或 pm 命令…") },
                shape = RoundedCornerShape(12.dp),
            )
            // 实时识别数
            val detectedCount = remember(text) { vm.extractPackageNames(text).size }
            if (text.isNotBlank() && detectedCount > 0) {
                Spacer(Modifier.height(6.dp))
                TinyBadge(
                    "✓ 识别出 $detectedCount 个包名",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = "解析清单",
                onClick = { vm.parseDebloatList(text) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 解析结果：折叠卡片
        if (items.isNotEmpty()) {
            var expanded by remember(items) { mutableStateOf(false) }
            val onPhone = items.count { it.entry != null }
            val canDisable = items.mapNotNull { it.entry }
                .filter { it.isEnabled && !it.isUninstalled }
            val canDelete = items.mapNotNull { it.entry }
                .filter { !it.isUninstalled }
            val disabledCount = items.count { it.entry?.let { e -> !e.isEnabled && !e.isUninstalled } == true }
            val notFound = items.size - onPhone
            ContentCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 标题行：点击展开/收起
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TileIcon("📊", TileKind.GREEN)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "解析结果 ${items.size} 个",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // v6: 四格统计
                    ParseStatGrid(
                        onPhone = onPhone,
                        canDisable = canDisable.size,
                        disabled = disabledCount,
                        notFound = notFound,
                    )
                    // 操作按钮：始终可见
                    if (shizukuReady) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            GradientButton(
                                text = "停用全部(${canDisable.size})",
                                onClick = { vm.batchSetEnabled(canDisable, false) },
                                enabled = canDisable.isNotEmpty() && vm.progress == null,
                                modifier = Modifier.weight(1f),
                            )
                            DangerOutlineButton(
                                text = "删除全部(${canDelete.size})",
                                onClick = { vm.confirmBatchDelete = canDelete },
                                enabled = canDelete.isNotEmpty() && vm.progress == null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        TextButton(onClick = { vm.showShizukuDialog = true }) {
                            Text("批量操作需要 Shizuku，点击去设置 →")
                        }
                    }
                    // 明细：展开才显示
                    if (expanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        Column(
                            modifier = Modifier.heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items.forEach { item ->
                                val e = item.entry
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            e?.appName ?: item.packageName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            item.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val label = when {
                                        e == null -> "未找到"
                                        e.isUninstalled -> "已删除"
                                        !e.isEnabled -> "已停用"
                                        else -> "已安装"
                                    }
                                    val (bc, cc) = when {
                                        e == null -> MaterialTheme.colorScheme.surfaceVariant to
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        e.isUninstalled -> MaterialTheme.colorScheme.tertiaryContainer to
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        !e.isEnabled -> MaterialTheme.colorScheme.errorContainer to
                                            MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer to
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                    TinyBadge(label, bc, cc)
                                }
                            }
                        }
                    } else {
                        TextButton(onClick = { expanded = true }) {
                            Text("展开查看明细 →")
                        }
                    }
                }
            }
        }

        // 自动停用：折叠/展开
        AutoDisableSection(vm = vm)

        // 导出 + OTA：合并紧凑行
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExportScript)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TileIcon("📤", TileKind.GREEN, 34.dp)
                    Text(
                        "导出 ADB 脚本",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", color = MaterialTheme.colorScheme.outline, fontSize = 18.sp)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TileIcon("🔄", TileKind.ORANGE, 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "OTA 恢复检测",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (vm.restoredApps.isEmpty()) "当前无被系统恢复的应用"
                            else "有 ${vm.restoredApps.size} 个被恢复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 自动停用区域：无任务时折叠成一行，有任务/运行中/完成时展开 */
@Composable
private fun AutoDisableSection(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    // 自动停用运行时保持屏幕常亮
    DisposableEffect(vm.autoDisableUi is AutoDisableState.Running) {
        val win = activity?.window
        if (vm.autoDisableUi is AutoDisableState.Running) {
            win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val pending = vm.pendingAutoDisable
    val ui = vm.autoDisableUi
    val hasContent = pending.isNotEmpty() ||
        ui is AutoDisableState.Running ||
        ui is AutoDisableState.Done

    if (!hasContent) {
        // 折叠态：一行（左侧蓝条与解析结果视觉归组）
        val ready = vm.unlockPkgInstalled && vm.accessibilityEnabled
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = { vm.showAutoDisableDetail = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 左侧蓝条
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp),
                        ),
                )
                TileIcon("♿", TileKind.ORANGE, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "无障碍自动停用",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (ready) "● 就绪 · 暂无待处理应用"
                        else "○ 未配置 · 点击设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", color = MaterialTheme.colorScheme.outline, fontSize = 18.sp)
            }
        }
    } else {
        // 展开态：橙色高亮卡
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.tertiary
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TileIcon("♿", TileKind.ORANGE, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "无障碍自动停用",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (pending.isNotEmpty() && ui !is AutoDisableState.Running && ui !is AutoDisableState.Done) {
                            Text(
                                "${pending.size} 个待处理（pm 命令被拒绝）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (ui) {
                    is AutoDisableState.Running -> {
                        Text(
                            "正在停用 ${ui.done}/${ui.total}：${ui.currentName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (ui.total > 0) ui.done.toFloat() / ui.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请保持屏幕亮起，不要手动操作",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.stopAutoDisable() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("停止") }
                    }
                    is AutoDisableState.Done -> {
                        val failed = ui.results.filter { !it.ok }
                        val okCount = ui.results.size - failed.size
                        Text(
                            "停用完成：成功 $okCount 个" +
                                if (failed.isNotEmpty()) "，失败 ${failed.size} 个" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (failed.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            failed.take(5).forEach { r ->
                                Text(
                                    "${vm.appNameOf(r.target.pkg)}：${r.reason.ifEmpty { "失败" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (failed.isNotEmpty()) {
                                TextButton(onClick = { vm.retryAutoDisableFailed() }) {
                                    Text("重试失败项")
                                }
                            }
                            TextButton(onClick = { vm.dismissAutoDisableResult() }) {
                                Text("关闭")
                            }
                        }
                    }
                    else -> {
                        Text(
                            "将逐个打开系统详情页并点击「停用」，全程自动。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        pending.take(5).forEach { t ->
                            Text(
                                "· ${t.name}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (pending.size > 5) {
                            Text(
                                "…等 ${pending.size} 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        GradientButton(
                            text = "开始自动停用（${pending.size}）",
                            onClick = { vm.autoDisablePending() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 自动停用配置详情弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoDisableDetailDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DIALOG_SHAPE,
        title = { DialogHeader("♿", TileKind.ORANGE, "无障碍自动停用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "批量停用被系统拒绝的应用会自动记录在这里。无障碍服务逐个打开系统详情页并点击「停用」，全程无需手动操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                // v6: 状态行卡片化
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        "解锁组件",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (vm.unlockPkgInstalled) "已安装" else "未安装",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vm.unlockPkgInstalled) Color(0xFF16A34A) else Color(0xFFD97706),
                    )
                    if (!vm.unlockPkgInstalled) {
                        Spacer(Modifier.width(10.dp))
                        MiniGradientButton(
                            text = "安装",
                            onClick = { vm.installUnlockPkg(context) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        "无障碍权限",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (vm.accessibilityEnabled) "已开启" else "未开启",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vm.accessibilityEnabled) Color(0xFF16A34A) else Color(0xFFD97706),
                    )
                    if (!vm.accessibilityEnabled) {
                        Spacer(Modifier.width(10.dp))
                        MiniGradientButton(
                            text = "去开启",
                            onClick = { vm.openAccessibilitySettings() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 首页排序底部弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "排序",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "设置里保留同样入口，两边同步",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SortKey.entries.forEach { key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.setSort(key, settings.sortAsc)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        key.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (settings.sortKey == key) FontWeight.SemiBold else null,
                        color = if (settings.sortKey == key) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.sortKey == key) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { vm.setSort(settings.sortKey, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (settings.sortAsc) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) { Text("升序 ↑") }
                FilledTonalButton(
                    onClick = { vm.setSort(settings.sortKey, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (!settings.sortAsc) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) { Text("降序 ↓") }
            }
        }
    }
}

// ================= 设置 Tab =================

@Composable
private fun SettingsTab(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (e: Exception) {
            null
        } ?: "0.0.2"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        // v6: 蓝渐变应用头
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brandGradient())
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📱", fontSize = 26.sp)
                    }
                }
                Column {
                    Text(
                        "应用检查器",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "v$versionName · dev.appinspector",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        GroupHeader("常用")
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Terminal,
                tileKind = TileKind.BLUE,
                title = "Shizuku",
                subtitle = when (vm.shizukuStatus) {
                    ShizukuManager.Status.Ready -> "已连接 · 可停用/删除/终端"
                    ShizukuManager.Status.NotInstalled -> "未安装"
                    ShizukuManager.Status.NotRunning -> "未运行"
                    ShizukuManager.Status.NoPermission -> "未授权"
                    else -> "检测中"
                },
                trailing = { ShizukuDot(vm.shizukuStatus) },
                onClick = {
                    vm.refreshShizukuStatus()
                    vm.showShizukuDialog = true
                },
            )
        }

        GroupHeader("显示")
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Share,
                tileKind = TileKind.GREEN,
                title = "导出列表",
                subtitle = "CSV / Markdown / JSON",
                onClick = { vm.showExport = true },
                showDivider = true,
            )
            SettingsRow(
                icon = Icons.Outlined.Settings,
                tileKind = TileKind.ORANGE,
                title = "提取设置",
                subtitle = "保存目录 · 文件命名",
                onClick = { vm.showExtractSettings = true },
            )
        }

        GroupHeader("外观")
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.DarkMode,
                tileKind = TileKind.PURPLE,
                title = "深色模式",
                subtitle = vm.themeModeTitle(settings.themeMode),
                onClick = { vm.showThemeDialog = true },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 设置分组卡片 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = false,
    onClick: () -> Unit,
    tileKind: TileKind = TileKind.BLUE,
) {
    val (tileBg, tileTint) = tileColors(tileKind)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tileBg,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tileTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

// ================= Shizuku 终端（全屏） =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalScreen(vm: AppViewModel, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.terminalLines.size) {
        if (vm.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(vm.terminalLines.size - 1)
        }
    }
    val connected = vm.shizukuStatus == ShizukuManager.Status.Ready
    var showGuide by remember { mutableStateOf(false) }
    // v6.6: 快捷命令
    val quickCmds = listOf("pm list packages", "dumpsys battery", "pm list packages -d")
    fun submit() {
        if (input.isNotBlank()) {
            vm.execTerminalCommand(input)
            input = ""
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku 终端", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "返回")
                    }
                },
                actions = {
                    TinyBadge(
                        if (connected) "● 已连接" else "● 未连接",
                        if (connected) Color(0xFFD3F8D3) else MaterialTheme.colorScheme.errorContainer,
                        if (connected) Color(0xFF0A5C0A) else MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = { showGuide = true }) { Text("\uD83D\uDCD6 指南") }
                    TextButton(onClick = { vm.clearTerminal() }) { Text("清屏") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // v6.6: 精简为一行说明
            Text(
                "以 shell 身份执行命令，多条用 ; 分隔批量执行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            // v6.6: 快捷命令 chips（横向滑动，不换行）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickCmds.forEach { cmd ->
                    Surface(
                        onClick = { input = cmd },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Text(
                            cmd,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            // v6.6: 终端控制台
            Surface(
                color = Color(0xFF0D1117),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                if (vm.terminalLines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3FB950),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "输入命令开始，或点上方快捷命令",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF8B949E),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(vm.terminalLines) { line ->
                            when {
                                line.isStatus -> {
                                    // v6.6: 状态行 ✓/✗
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (line.statusOk) "✓" else "✗",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (line.statusOk) Color(0xFF3FB950)
                                            else Color(0xFFFF7B72),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            line.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (line.statusOk) Color(0xFF3FB950)
                                            else Color(0xFFFF7B72),
                                        )
                                    }
                                }
                                line.isCommand -> {
                                    // v6.6: 命令行，$ 单独绿色
                                    Row {
                                        Text(
                                            "$ ",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF3FB950),
                                        )
                                        Text(
                                            line.text.removePrefix("$ ").removePrefix("$"),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFF79C0FF),
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        line.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (line.isError) Color(0xFFFF7B72)
                                        else Color(0xFFC9D1D9),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // v6.6: 输入栏（$ 前缀 + 圆形渐变发送键），imePadding 避开键盘
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Text(
                            "$",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3FB950),
                            fontSize = 15.sp,
                        )
                    },
                    placeholder = {
                        Text(
                            "输入 shell 命令…",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { submit() },
                    ),
                )
                Spacer(Modifier.width(10.dp))
                // v6.6: 圆形渐变发送按钮
                Surface(
                    onClick = { submit() },
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(50.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brandGradient(),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "➤",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    // v6.6: 命令指南底部弹窗
    if (showGuide) {
        CommandGuideSheet(
            onDismiss = { showGuide = false },
            onFill = { cmd ->
                input = cmd
                showGuide = false
            },
        )
    }
}


/** v6.6: 命令指南数据 */
private data class GuideCmd(val code: String, val desc: String)
private data class GuideCat(val name: String, val cmds: List<GuideCmd>)

private val guideCats = listOf(
    GuideCat(
        "应用管理", listOf(
            GuideCmd("pm list packages", "列出所有已安装应用的包名；加 -d 只看已停用的，加 -s 只看系统的"),
            GuideCmd("pm disable-user --user 0 <包名>", "停用应用（把 <包名> 换成实际包名）。部分系统保护应用会被静默拒绝"),
            GuideCmd("pm enable <包名>", "重新启用被停用的应用"),
            GuideCmd("pm uninstall --user 0 <包名>", "卸载应用（系统应用仅对当前用户卸载，可恢复）。谨慎操作"),
            GuideCmd("pm clear <包名>", "清除应用数据（恢复出厂状态）。会删除该应用数据"),
            GuideCmd("pm install-existing --user 0 <包名>", "恢复之前卸载的系统应用"),
        )
    ),
    GuideCat(
        "系统信息", listOf(
            GuideCmd("dumpsys meminfo <包名>", "查看指定应用的内存占用详情"),
            GuideCmd("dumpsys package <包名>", "查看应用的详细信息（权限、版本、安装路径等）"),
            GuideCmd("dumpsys display", "查看屏幕显示相关信息"),
            GuideCmd("getprop ro.build.version.release", "查看 Android 系统版本号"),
        )
    ),
    GuideCat(
        "电池显示", listOf(
            GuideCmd("dumpsys battery", "查看电池状态（电量、温度、充电状态等）"),
            GuideCmd("cmd statusbar expand-notifications", "展开通知栏"),
            GuideCmd("cmd statusbar collapse", "收起通知栏/状态栏面板"),
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandGuideSheet(onDismiss: () -> Unit, onFill: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var catIdx by remember { mutableStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                "📖 命令指南",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                "点「填入」把命令送到输入框，改好包名再执行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp),
            )
            // 分类 Tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                guideCats.forEachIndexed { i, cat ->
                    val selected = i == catIdx
                    Surface(
                        onClick = { catIdx = i },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            cat.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            // 命令列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(guideCats[catIdx].cmds) { cmd ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    cmd.code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    onClick = { onFill(cmd.code) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.background(
                                        brandGradient(),
                                        RoundedCornerShape(14.dp),
                                    ),
                                ) {
                                    Text(
                                        "填入",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                            Text(
                                cmd.desc,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            // 底部提示
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF3E2),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                Text(
                    "⚠️ shell 身份限制：不支持管道 |、重定向 >；组件级操作会被系统拒绝",
                    fontSize = 11.5.sp,
                    color = Color(0xFF9A6700),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

// ================= 应用详情 BottomSheet =================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DetailSheet(
    vm: AppViewModel,
    entry: AppEntry,
    shizukuReady: Boolean,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // 头部：大图标 + 名称 + 徽标（名称/包名长按复制）
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.appName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { copy(entry.appName) },
                        ),
                    )
                    Text(
                        entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { copy(entry.packageName) },
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (entry.isSystem) {
                    TinyBadge(
                        "系统应用",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TinyBadge(
                    when {
                        entry.isUninstalled -> "已删除"
                        entry.isEnabled -> "已启用"
                        else -> "已停用"
                    },
                    when {
                        entry.isUninstalled -> MaterialTheme.colorScheme.tertiaryContainer
                        entry.isEnabled -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    when {
                        entry.isUninstalled -> MaterialTheme.colorScheme.onTertiaryContainer
                        entry.isEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    },
                )
            }

            SectionTitle("基本信息")
            DetailGrid(
                listOf(
                    "版本名称" to (entry.versionName ?: "未知"),
                    "版本号" to entry.versionCode.toString(),
                    "APK 大小" to formatSize(entry.apkSize),
                    "拆分包" to if (entry.splitSourceDirs.isNotEmpty())
                        "${entry.splitSourceDirs.size} 个" else "无",
                    "首次安装" to formatDate(entry.firstInstallTime),
                    "最后更新" to formatDate(entry.lastUpdateTime),
                )
            )
            // 安装包路径单独一行，长按复制
            DetailRow(
                "安装包路径",
                entry.sourceDir ?: "未知",
                onCopy = ::copy,
                copyable = true,
            )

            SectionTitle("使用与存储")
            UsageRow(vm = vm, entry = entry)
            Spacer(Modifier.height(8.dp))
            StorageSection(vm = vm, entry = entry)

            UadSection(entry = entry)
            Spacer(Modifier.height(8.dp))
        }

        // 底部常驻操作区（状态感知）
        Column(Modifier.padding(horizontal = 20.dp)) {
            HorizontalDivider()
            if (shizukuReady) {
                when {
                    // 已删除的系统应用：可恢复安装
                    entry.isUninstalled && entry.isSystem -> {
                        Text(
                            "安装包保留在系统分区，可一键恢复，无需重新下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Button(
                            onClick = onRestore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("恢复安装") }
                    }
                    // 已删除的用户应用：彻底删除，无操作
                    entry.isUninstalled -> {
                        Text(
                            "该应用已彻底删除，无法恢复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    // 正常状态：停用/启用 + 删除
                    else -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onToggleEnabled,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(if (entry.isEnabled) "停用" else "启用")
                            }
                            Button(
                                onClick = onDelete,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) { Text("删除") }
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = onOpenShizuku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) { Text("停用 / 删除需要 Shizuku，点击去设置 →") }
            }
            // 已删除的应用不再显示提取/系统设置
            if (!entry.isUninstalled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = onExtract) { Text("提取安装包") }
                    TextButton(onClick = onOpenSystemSettings) { Text("系统设置") }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun UsageRow(vm: AppViewModel, entry: AppEntry) {
    // 未授权时直接隐藏（权限在启动时申请）
    if (!vm.hasUsagePerm) return
    Column {
        Text(
            "最近使用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            UsageStatsHelper.formatLastUsed(entry.lastUsed),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StorageSection(vm: AppViewModel, entry: AppEntry) {
    val info = vm.storageInfo
    val shizukuReady = vm.shizukuStatus == ShizukuManager.Status.Ready
    LaunchedEffect(entry.packageName) {
        vm.loadStorageInfo(entry.packageName)
    }
    DisposableEffect(entry.packageName) {
        onDispose { vm.clearStorageInfo() }
    }
    // 未授权时直接隐藏（权限在启动时申请）
    if (!vm.hasUsagePerm) return
    Column {
        Text(
            "存储占用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            info == null -> {
                Text(
                    "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    "应用 ${StorageHelper.formatBytes(info.appBytes)} · " +
                        "数据 ${StorageHelper.formatBytes(info.dataBytes)} · " +
                        "缓存 ${StorageHelper.formatBytes(info.cacheBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (shizukuReady && info.dataBytes > 0) {
                    TextButton(
                        onClick = { vm.confirmClearData = entry },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            "清除数据（pm clear）",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UadSection(entry: AppEntry) {
    val context = LocalContext.current
    val info = remember(entry.packageName) {
        UadDb.query(context, entry.packageName)
    } ?: return
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "社区建议",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            val (bc, cc) = uadBadgeColors(info.removal)
            TinyBadge(info.titleCn, bc, cc)
            if (info.subtitleCn.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    info.subtitleCn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded && info.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "数据来源：Universal Android Debloater 社区清单，仅供参考",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 双列信息网格：一行显示两个字段 */
@Composable
private fun DetailGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 奇数个时补齐占位，保持对齐
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 详情行：copyable=true 时长按复制（仅应用名/包名/安装包路径）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
    copyable: Boolean = false,
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis,
            modifier = if (copyable) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(value) },
                )
            } else Modifier,
        )
    }
}

// ================= 保留弹窗 =================

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
        shape = DIALOG_SHAPE,
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
        shape = DIALOG_SHAPE,
        title = { DialogHeader("📤", TileKind.GREEN, "导出应用信息") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DialogSectionTitle("字段")
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
                            .padding(start = 18.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = f.key in checked,
                            onCheckedChange = null,
                        )
                        Text(f.title, modifier = Modifier.padding(start = 12.dp), fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                DialogSectionTitle("格式")
                ExportFormat.entries.forEach { ef ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = format == ef, onClick = { format = ef })
                            .padding(start = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            RadioButton(selected = format == ef, onClick = { format = ef })
                        }
                        Text(ef.title, modifier = Modifier.padding(start = 12.dp), fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                DialogSectionTitle("范围")
                listOf(false to "当前列表（$currentCount 个）", true to "全部应用（$totalCount 个）")
                    .forEach { (all, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = scopeAll == all, onClick = { scopeAll = all })
                                .padding(start = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                RadioButton(selected = scopeAll == all, onClick = { scopeAll = all })
                            }
                            Text(label, modifier = Modifier.padding(start = 12.dp), fontSize = 15.sp)
                        }
                    }
            }
        },
        confirmButton = {
            MiniGradientButton(
                text = "导出",
                onClick = {
                    vm.exportFields = ALL_EXPORT_FIELDS
                        .filter { it.key in checked }
                        .map { it.key }
                    vm.exportFormat = format
                    vm.exportScopeAll = scopeAll
                    onDismiss()
                    onLaunchExport(vm.exportFileName())
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** v6.1: 提取设置改弹窗（原独立页面） */
@Composable
private fun ExtractSettingsDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val label = runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
                    ?: uri.toString()
            }.getOrDefault(uri.toString())
            vm.onDirPicked(context, uri, label)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DIALOG_SHAPE,
        title = { DialogHeader("⚙️", TileKind.ORANGE, "提取设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("存放路径", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        settings.extractPathLabel.ifBlank { "未选择（首次提取时选择）" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    MiniGradientButton(
                        text = "更改",
                        onClick = { dirLauncher.launch(null) },
                    )
                }
                Text("命名表达式", style = MaterialTheme.typography.titleSmall)
                androidx.compose.material3.OutlinedTextField(
                    value = settings.namingExpr,
                    onValueChange = { vm.setNaming(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Text(
                    "{A} 应用名称　{V} 版本名称\n{P} 应用包名　{C} 版本号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "预览：${dev.appinspector.extract.buildApkFileName(previewEntry, settings.namingExpr)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("包含拆分包", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "部分应用（如抖音）由多个 split APK 组成，开启后一并复制",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = settings.includeSplits,
                        onCheckedChange = { vm.setIncludeSplits(it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

private val previewEntry = dev.appinspector.data.AppEntry(
    packageName = "com.tencent.mm",
    appName = "微信",
    versionName = "8.0.60",
    versionCode = 90120,
    apkSize = 0,
    firstInstallTime = 0,
    lastUpdateTime = 0,
    isSystem = false,
    isUpdatedSystemApp = false,
    isEnabled = true,
    sourceDir = null,
    splitSourceDirs = emptyList(),
    applicationInfo = android.content.pm.ApplicationInfo(),
)
