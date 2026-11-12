package com.nova.ai.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nova.ai.data.SettingsStore
import com.nova.ai.tools.Vision
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.VoiceController
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Scan(val jpeg: ByteArray, val preview: Bitmap)

@Composable
fun ScannerScreen(store: SettingsStore, voice: VoiceController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var mode by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun analyse(uri: Uri) {
        if (busy) return
        busy = true
        result = ""
        error = ""
        scope.launch {
            try {
                val scan = withContext(Dispatchers.Default) { loadScan(ctx, uri) }
                if (scan == null) {
                    error = "Photo khul nahi paayi. Dobara try karo."
                } else {
                    preview = scan.preview.asImageBitmap()
                    val prompt = when (mode) {
                        0 -> Vision.PRODUCT
                        1 -> Vision.TEXT
                        else -> Vision.GENERAL
                    }
                    result = Vision.ask(store, prompt, scan.jpeg)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Kuch gadbad ho gayi"
            }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri?.let { analyse(it) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyse(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ToolHeader("Scanner", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Photo kheencho, NOVA Gemini se dekhkar batayegi. Photo Google ko jaati hai, isliye bank, ID ya private cheezon ki photo mat bhejna.",
                color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip("Product", mode == 0, { mode = 0 })
                ToolChip("Text padho", mode == 1, { mode = 1 })
                ToolChip("Ye kya hai?", mode == 2, { mode = 2 })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolButton("Camera", {
                    val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
                    val f = File(dir, "scan_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                    photoUri = uri
                    camera.launch(uri)
                }, Modifier.weight(1f), enabled = !busy)
                ToolButton("Gallery", { gallery.launch("image/*") }, Modifier.weight(1f), enabled = !busy)
            }

            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(16.dp))
                )
            }
            if (busy) Text("NOVA dekh rahi hai…", color = NovaColors.Lavender, fontSize = 14.sp)
            if (error.isNotBlank()) Text(error, color = NovaColors.Cyan, fontSize = 14.sp, lineHeight = 20.sp)
            if (result.isNotBlank()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NovaColors.Panel)
                        .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(result, color = NovaColors.Text, fontSize = 15.sp, lineHeight = 22.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolButton("Bolo", { voice.speak(spokenText(result)) })
                        ToolButton("Copy", { clipboard.setText(AnnotatedString(result)) })
                    }
                    if (mode == 0) {
                        Text(
                            "Daam sirf andaza hai, live price nahi. Kharidne se pehle dukaan ya site par check karo.",
                            color = NovaColors.Muted, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Decodes at most ~1600 px, fixes rotation from EXIF, and returns a JPEG for upload plus a preview bitmap. */
private fun loadScan(ctx: Context, uri: Uri): Scan? {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val orientation = try {
        cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val bmp = if (degrees != 0f) {
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
    } else raw
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Scan(out.toByteArray(), bmp)
}
