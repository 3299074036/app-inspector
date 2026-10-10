package dev.appinspector.ui

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appinspector.data.AppEntry
import dev.appinspector.extract.buildApkFileName

private val previewEntry = AppEntry(
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
    applicationInfo = ApplicationInfo(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractSettingsScreen(
    vm: AppViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提取设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                Button(onClick = { dirLauncher.launch(null) }) { Text("更改") }
            }

            Spacer(Modifier.height(4.dp))
            Text("命名表达式", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = settings.namingExpr,
                onValueChange = { vm.setNaming(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "{A} 应用名称　{V} 版本名称\n{P} 应用包名　{C} 版本号",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "预览：${buildApkFileName(previewEntry, settings.namingExpr)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("包含拆分包", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "部分应用（如抖音）由多个 split APK 组成，开启后一并复制",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.includeSplits,
                    onCheckedChange = { vm.setIncludeSplits(it) },
                )
            }

        }
    }
}
