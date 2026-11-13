package com.nova.ai.ui.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.nova.ai.data.SettingsStore
import com.nova.ai.tools.BarcodeInfo
import com.nova.ai.tools.ScanResult
import com.nova.ai.tools.Vision
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.VoiceController
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the camera straight away. Barcodes and QR codes are read automatically on the phone;
 * for anything else press "Pehchano" and Gemini looks at a photo.
 */
@Composable
fun ScannerScreen(store: SettingsStore, voice: VoiceController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    if (granted) {
        ScannerCamera(store, voice, onBack)
    } else {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            ToolHeader("Scanner", onBack)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Scanner ko camera chahiye. Permission dene par camera seedha khul jaayega.",
                    color = NovaColors.Muted, fontSize = 14.sp, lineHeight = 20.sp
                )
                ToolButton("Camera permission do", { request.launch(Manifest.permission.CAMERA) })
            }
        }
    }
}

@Composable
private fun ScannerCamera(store: SettingsStore, voice: VoiceController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var mode by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<ScanResult?>(null) }
    var aiText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    val scanningNow = rememberUpdatedState(scanning)
    val busyNow = rememberUpdatedState(busy)

    val camera = remember { LifecycleCameraController(ctx) }
    val scanner = remember { BarcodeScanning.getClient() }

    fun handle(code: Barcode) {
        busy = true
        error = ""
        aiText = ""
        scope.launch {
            val r = try {
                BarcodeInfo.describe(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanResult("Code mil gaya", listOf(code.rawValue.orEmpty()), "Code mil gaya.", code.rawValue.orEmpty())
            }
            found = r
            busy = false
            voice.speak(r.spoken)
        }
    }

    fun identify() {
        if (busy) return
        busy = true
        error = ""
        aiText = ""
        found = null
        scanning = false
        camera.takePicture(
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bitmap = try {
                        image.toBitmap()
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (bitmap == null) {
                        error = "Photo nahi mil paayi. Dobara try karo."
                        busy = false
                        return
                    }
                    scope.launch {
                        try {
                            val jpeg = withContext(Dispatchers.Default) { toJpeg(bitmap, rotation) }
                            val prompt = when (mode) {
                                0 -> Vision.PRODUCT
                                1 -> Vision.TEXT
                                else -> Vision.GENERAL
                            }
                            val text = Vision.ask(store, prompt, jpeg)
                            aiText = text
                            voice.speak(spokenText(text))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Kuch gadbad ho gayi"
                        }
                        busy = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    error = "Photo nahi kheench paayi: ${exception.message}"
                    busy = false
                }
            }
        )
    }

    fun reset() {
        found = null
        aiText = ""
        error = ""
        scanning = true
    }

    fun open(intent: Intent, missing: String) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            error = missing
        }
    }

    DisposableEffect(owner) {
        camera.bindToLifecycle(owner)
        onDispose {
            camera.unbind()
            scanner.close()
        }
    }
    LaunchedEffect(camera) {
        val executor = ContextCompat.getMainExecutor(ctx)
        camera.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                val codes = result.getValue(scanner)
                if (scanningNow.value && !busyNow.value && !codes.isNullOrEmpty()) {
                    scanning = false
                    handle(codes[0])
                }
            }
        )
    }
    LaunchedEffect(torch) { camera.enableTorch(torch) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PreviewView(c).apply {
                    this.controller = camera
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(Modifier.align(Alignment.Center).offset(y = (-90).dp).size(230.dp)) {
            drawRoundRect(
                color = if (scanning) NovaColors.Cyan else NovaColors.Good,
                cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Wapas", tint = Color.White)
            }
            Text("Scanner", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ToolButton(if (torch) "Torch band" else "Torch", { torch = !torch })
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(NovaColors.Void.copy(alpha = 0.94f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .navigationBarsPadding()
                .padding(16.dp)
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val f = found
            when {
                f != null -> {
                    Text(f.title, color = NovaColors.Lavender, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    f.lines.forEach { Text(it, color = NovaColors.Text, fontSize = 14.sp, lineHeight = 20.sp) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Bolo", { voice.speak(f.spoken) })
                        ToolButton("Copy", { clipboard.setText(AnnotatedString(f.raw)) })
                        f.url?.let { u ->
                            ToolButton("Link kholo", { open(Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Link kholne wala app nahi mila") })
                        }
                        f.upi?.let { u ->
                            ToolButton("Payment app chuno", {
                                open(
                                    Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Payment app chuno"),
                                    "Koi UPI payment app nahi mila"
                                )
                            })
                            ToolButton("PhonePe mein kholo", {
                                open(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("com.phonepe.app"),
                                    "PhonePe is phone mein nahi mila"
                                )
                            })
                        }
                        f.phone?.let { n ->
                            ToolButton("Call screen", { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")), "Dialer nahi mila") })
                        }
                        f.geo?.let { g ->
                            ToolButton("Map kholo", { open(Intent(Intent.ACTION_VIEW, Uri.parse(g)), "Map app nahi mila") })
                        }
                        if (f.needsPhoto) ToolButton("Photo se pehchano", { identify() })
                        ToolButton("Dobara scan", { reset() })
                    }
                }
                aiText.isNotBlank() -> {
                    Text(aiText, color = NovaColors.Text, fontSize = 14.sp, lineHeight = 21.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Bolo", { voice.speak(spokenText(aiText)) })
                        ToolButton("Copy", { clipboard.setText(AnnotatedString(aiText)) })
                        ToolButton("Dobara scan", { reset() })
                    }
                    if (mode == 0) {
                        Text(
                            "Daam sirf andaza hai, live price nahi. Kharidne se pehle dukaan ya site par check karo.",
                            color = NovaColors.Muted, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                }
                busy -> Text("NOVA dekh rahi hai…", color = NovaColors.Lavender, fontSize = 14.sp)
                else -> Text(
                    "Camera ko barcode ya QR ke saamne rakho, NOVA apne-aap padh legi. Product ya text ke liye neeche chuno aur Pehchano dabao.",
                    color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
            if (error.isNotBlank()) Text(error, color = NovaColors.Cyan, fontSize = 13.sp, lineHeight = 19.sp)

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip("Product", mode == 0, { mode = 0 })
                ToolChip("Text padho", mode == 1, { mode = 1 })
                ToolChip("Ye kya hai?", mode == 2, { mode = 2 })
            }
            ToolButton("Pehchano", { identify() }, Modifier.fillMaxWidth(), enabled = !busy)
            Text(
                "Barcode/QR phone par hi padha jaata hai. Product ke barcode ka number free Open Food Facts database ko jaata hai. \"Pehchano\" dabane par photo Google Gemini ko jaati hai, isliye bank, ID ya private cheezon ki photo mat bhejna.",
                color = NovaColors.Muted, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

/** Rotates the camera frame upright, shrinks it to ~1600 px and returns a JPEG for upload. */
private fun toJpeg(src: Bitmap, rotation: Int): ByteArray {
    var bmp = src
    if (rotation != 0) {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
    val longest = maxOf(bmp.width, bmp.height)
    if (longest > 1600) {
        val f = 1600f / longest
        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}
