package com.nova.ai.ui.customise

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nova.ai.control.NovaAccessibilityService
import com.nova.ai.data.SettingsStore
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.SectionLabel
import com.nova.ai.ui.common.WideButton
import com.nova.ai.ui.theme.NovaColors

@Composable
fun AccessibilityScreen(store: SettingsStore, onBack: () -> Unit, onPermissions: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    @Suppress("UNUSED_EXPRESSION")
    refresh
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val component = ComponentName(context, NovaAccessibilityService::class.java)
    val a11yOn = enabled.contains(component.flattenToString(), ignoreCase = true) ||
        enabled.contains(component.flattenToShortString(), ignoreCase = true)

    ScreenFrame("Accessibility", onBack) {
        SectionLabel("Phone control")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NOVA ko apps kholne, search, alarm aur timer ki ijazat do",
                color = NovaColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f)
            )
            Switch(checked = store.phoneControl, onCheckedChange = { store.phoneControl = it })
        }
        HintText("Ye switch band ho to NOVA phone par koi kaam nahi karti.")

        SectionLabel("Accessibility service: " + if (a11yOn) "Chalu ✓" else "Band")
        HintText("Back, scroll, tap aur screen padhne ke liye ye chahiye. Android ki list mein NOVA AI dhundo aur chalu karo. Payment, bank aur password wali screens par NOVA kaam nahi karti.")
        WideButton("Android Accessibility settings kholo", {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        WideButton("Mic aur notification permission", onPermissions)
    }
}
