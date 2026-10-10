package dev.appinspector.data

import android.content.pm.PackageManager
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 应用图标内存缓存，避免滚动时反复 loadIcon */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(256)

    suspend fun get(pm: PackageManager, entry: AppEntry): ImageBitmap =
        withContext(Dispatchers.IO) {
            cache[entry.packageName] ?: run {
                val drawable = entry.applicationInfo.loadIcon(pm)
                val bitmap = drawable.toBitmap(96, 96).asImageBitmap()
                cache.put(entry.packageName, bitmap)
                bitmap
            }
        }
}

@Composable
fun AppIcon(entry: AppEntry, modifier: Modifier = Modifier) {
    val pm = LocalContext.current.packageManager
    var bitmap by remember(entry.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.packageName) {
        bitmap = runCatching { IconCache.get(pm, entry) }.getOrNull()
    }
    val shape = RoundedCornerShape(12.dp)
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = entry.appName,
            modifier = modifier
                .size(48.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.appName.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
