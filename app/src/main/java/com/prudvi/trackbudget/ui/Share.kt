package com.prudvi.trackbudget.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.prudvi.trackbudget.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Stable
data class ShareImageSize(val width: Int, val height: Int)

@Stable
class ShareCaptureState internal constructor(
    val graphicsLayer: GraphicsLayer,
    private val context: Context,
    private val scope: CoroutineScope,
    private val fileNamePrefix: String,
    private val targetSize: ShareImageSize?,
    private val onFileCreated: () -> Unit,
) {
    fun share() {
        scope.launch {
            val file = runCatching {
                captureGraphicsLayerToPng(context, graphicsLayer, fileNamePrefix, targetSize)
            }.getOrNull() ?: return@launch
            onFileCreated()
            runCatching { sharePng(context, file) }
        }
    }
}

@Composable
fun rememberShareCaptureState(
    fileNamePrefix: String,
    targetSize: ShareImageSize? = null,
    onFileCreated: () -> Unit = {},
): ShareCaptureState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    return remember(context, scope, graphicsLayer, fileNamePrefix, targetSize, onFileCreated) {
        ShareCaptureState(
            graphicsLayer = graphicsLayer,
            context = context,
            scope = scope,
            fileNamePrefix = fileNamePrefix.sanitizeFilePrefix(),
            targetSize = targetSize,
            onFileCreated = onFileCreated,
        )
    }
}

fun Modifier.captureForShare(state: ShareCaptureState): Modifier = drawWithContent {
    state.graphicsLayer.record { this@drawWithContent.drawContent() }
    drawLayer(state.graphicsLayer)
}

suspend fun captureGraphicsLayerToPng(
    context: Context,
    graphicsLayer: GraphicsLayer,
    fileNamePrefix: String,
    targetSize: ShareImageSize? = null,
): File {
    val source = graphicsLayer.toImageBitmap().asAndroidBitmap()
    return withContext(Dispatchers.IO) {
        val bitmap = targetSize?.let { size ->
            if (source.width == size.width && source.height == size.height) source
            else Bitmap.createScaledBitmap(source, size.width, size.height, true)
        } ?: source
        val directory = File(context.cacheDir, "receipts-share").apply { mkdirs() }
        val file = File(directory, "${fileNamePrefix.sanitizeFilePrefix()}-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encode failed" }
        }
        file
    }
}

fun sharePng(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        clipData = ClipData.newUri(context.contentResolver, "Receipt", uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share receipt").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun String.sanitizeFilePrefix(): String = lowercase()
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "receipt" }
