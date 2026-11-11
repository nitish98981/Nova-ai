package com.nova.ai.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nova.ai.ui.theme.NovaColors

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    // Re-check statuses when the user returns from system settings.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    @Suppress("UNUSED_EXPRESSION")
    refresh // read so the screen recomposes after every refresh

    val micGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val notifNeeded = Build.VERSION.SDK_INT >= 33
    val notifGranted = !notifNeeded || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Wapas", tint = NovaColors.Lavender)
            }
            Text("Permissions", color = NovaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "NOVA kuch bhi tumhari ijazat ke bina nahi karegi. Har permission tum kabhi bhi band kar sakte ho.",
                color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
            )

            PermissionRow(
                title = "Microphone",
                detail = "Awaaz sunne ke liye (Phase 2).",
                status = if (micGranted) "Allowed" else "Not allowed",
                good = micGranted,
                actionLabel = if (micGranted) null else "Allow",
                onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            PermissionRow(
                title = "Notifications",
                detail = "Reminders aur alarms ke liye.",
                status = when {
                    !notifNeeded -> "Is Android version par zaroori nahi"
                    notifGranted -> "Allowed"
                    else -> "Not allowed"
                },
                good = notifGranted,
                actionLabel = if (notifGranted) null else "Allow",
                onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )

            PermissionRow(
                title = "Accessibility (phone control)",
                detail = "Apps kholna aur screen par tap karna. Service Phase 5 mein aayegi, tab tak list mein NOVA nahi dikhegi.",
                status = "Phase 5",
                good = false,
                actionLabel = "Open settings",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    status: String,
    good: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NovaColors.Panel)
            .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = NovaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            Text(status, color = if (good) NovaColors.Good else NovaColors.Lavender, fontSize = 13.sp)
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = NovaColors.Cyan) }
        }
    }
}
