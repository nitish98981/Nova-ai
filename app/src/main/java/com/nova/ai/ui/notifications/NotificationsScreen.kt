package com.nova.ai.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.common.ScreenFrame
import com.nova.ai.ui.common.WideButton
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.VoiceController

/** What NOVA did recently and what went wrong (failed AI, voice fallback, phone-control results). */
@Composable
fun NotificationsScreen(voice: VoiceController, onBack: () -> Unit) {
    LaunchedEffect(Unit) { voice.markRead() }

    ScreenFrame("Notifications", onBack) {
        if (voice.activity.isEmpty()) {
            HintText("Abhi kuch nahi hai. Jab NOVA koi phone ka kaam karegi ya koi AI/awaaz ki dikkat aayegi, yahan dikhega.")
        } else {
            voice.activity.forEach { item ->
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(NovaColors.Panel)
                        .border(1.dp, NovaColors.PanelEdge, shape)
                        .padding(14.dp)
                ) {
                    Text(item.time, color = NovaColors.Muted, fontSize = 11.sp)
                    Text(item.text, color = NovaColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            WideButton("Sab saaf karo", { voice.clearActivity() })
        }
    }
}
