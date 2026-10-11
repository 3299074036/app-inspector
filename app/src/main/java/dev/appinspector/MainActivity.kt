package dev.appinspector

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.io.File
import java.util.Date
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appinspector.ui.AppViewModel
import dev.appinspector.ui.ExtractSettingsScreen
import dev.appinspector.ui.MainScreen
import dev.appinspector.ui.theme.AppInspectorTheme

sealed interface Screen {
    data object List : Screen
    data object ExtractSettings : Screen
}

/**
 * 诊断用：未捕获异常时把堆栈写到「下载」目录（app-inspector-crash.txt），
 * 用户用文件管理器即可打开查看。
 */
private fun installCrashLogger(context: Context) {
    val default = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        runCatching {
            val text = buildString {
                appendLine("时间: ${Date()}")
                appendLine("线程: ${t.name}")
                appendLine(e.stackTraceToString())
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "app-inspector-crash.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(text.toByteArray())
                    }
                }
            } else {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "app-inspector-crash.txt",
                ).writeText(text)
            }
        }
        default?.uncaughtException(t, e)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 与 MainScreen 共享 Activity 级 ViewModel，以读取主题设置
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            AppInspectorTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var screen by remember { mutableStateOf<Screen>(Screen.List) }
                    when (screen) {
                        Screen.List -> MainScreen(
                            vm = vm,
                            onOpenExtractSettings = { screen = Screen.ExtractSettings },
                        )

                        Screen.ExtractSettings -> ExtractSettingsScreen(
                            onBack = { screen = Screen.List },
                        )
                    }
                }
            }
        }
    }
}
