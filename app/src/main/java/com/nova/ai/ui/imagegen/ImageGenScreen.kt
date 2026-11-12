package com.nova.ai.ui.imagegen

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.brain.ImageGen
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.common.CenteredRow
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.SectionLabel
import com.nova.ai.ui.common.TextInput
import com.nova.ai.ui.common.WideButton
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.ui.tools.ToolChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageGenScreen(store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<ImageGen.Result?>(null) }

    fun generate() {
        val p = prompt.trim()
        if (p.isEmpty() || busy) return
        busy = true
        error = ""
        info = ""
        scope.launch {
            try {
                val r = ImageGen.generate(store.geminiKey, store.imageModel, p)
                val bmp = withContext(Dispatchers.Default) { decode(r.bytes) }
                    ?: throw IllegalStateException("Image kholi nahi ja saki")
                result = r
                bitmap = bmp
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Kuch gadbad ho gayi"
            }
            busy = false
        }
    }

    fun save() {
        val r = result ?: return
        try {
            val ext = if (r.mime.contains("jpeg") || r.mime.contains("jpg")) "jpg" else "png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "nova_${System.currentTimeMillis()}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, r.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NOVA")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                info = "Gallery mein save nahi ho paayi"
            } else {
                context.contentResolver.openOutputStream(uri)?.use { it.write(r.bytes) }
                info = "Gallery mein save ho gayi (Pictures/NOVA) ✓"
            }
        } catch (e: Exception) {
            info = "Save nahi hui: ${e.message}"
        }
    }

    ScreenFrame("Image banao", onBack) {
        SectionLabel("Kaisi image chahiye?")
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("jaise: neon city mein anime ladki, purple aasmaan", color = NovaColors.Muted) },
            minLines = 3,
            maxLines = 6
        )
        for (s in listOf("Neon raat mein anime ladki, kaale baal, baingani aankhein", "Sunrise par shaant pahad aur jheel")) {
            ToolChip(s, false, { prompt = s }, Modifier.fillMaxWidth())
        }
        WideButton(if (busy) "Bana rahi hoon… (kuch second lagte hain)" else "Image banao", { generate() }, enabled = !busy && prompt.isNotBlank())

        if (error.isNotBlank()) {
            Text(error, color = NovaColors.Cyan, fontSize = 13.sp, lineHeight = 19.sp)
        }
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Banayi gayi image",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            CenteredRow {
                WideButton("Gallery mein save", { save() }, Modifier.weight(1f))
            }
            if (info.isNotBlank()) HintText(info)
        }

        TextInput("Image model", store.imageModel) { store.imageModel = it }
        HintText("Ye Gemini ke image model se banti hai, isliye Gemini API key chahiye. Gemini ke image model aksar free tier mein nahi hote, to billing wali key lag sakti hai; error aaye to yahin dikhega. Har image par Google ka invisible watermark hota hai. Tumhara likha prompt Google ko bheja jaata hai.")
    }
}

private fun decode(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
