package dev.appinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.appinspector.ui.ExtractSettingsScreen
import dev.appinspector.ui.MainScreen
import dev.appinspector.ui.theme.AppInspectorTheme

sealed interface Screen {
    data object List : Screen
    data object ExtractSettings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppInspectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var screen by remember { mutableStateOf<Screen>(Screen.List) }
                    when (screen) {
                        Screen.List -> MainScreen(
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
