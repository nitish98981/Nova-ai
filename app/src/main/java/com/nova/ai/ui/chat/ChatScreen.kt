package com.nova.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.common.HintText
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.ui.tools.ToolButton
import com.nova.ai.ui.tools.ToolHeader
import com.nova.ai.voice.ChatLine
import com.nova.ai.voice.VoiceController

/** Type to NOVA (same brain and voice as the mic) and see the whole conversation. */
@Composable
fun ChatScreen(voice: VoiceController, onBack: () -> Unit, onMic: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val liveText = if (voice.replying) voice.message else ""
    val count = voice.chatLog.size + (if (voice.replying) 1 else 0)

    LaunchedEffect(count, liveText.length) {
        if (count > 0) listState.scrollToItem(count - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ToolHeader("Chat", onBack)

        if (count == 0) {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                HintText("NOVA se likh kar baat karo. Jo bhi tum bolte ho ya likhte ho wo yahan dikhta hai. Ye baat sirf tab tak yaad rehti hai jab tak app khuli hai.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(voice.chatLog.toList()) { line -> Bubble(line) }
                if (voice.replying) {
                    item { Bubble(ChatLine(false, liveText.ifBlank { "NOVA soch rahi hai…" })) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("NOVA se kuch bhi pucho…", color = NovaColors.Muted) },
                maxLines = 4
            )
            ToolButton("Mic", onMic)
            ToolButton(
                "Bhejo",
                {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        input = ""
                        voice.submitText(t)
                    }
                },
                enabled = input.isNotBlank()
            )
        }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(0.85f)
                .clip(shape)
                .background(if (line.fromUser) NovaColors.Violet.copy(alpha = 0.25f) else NovaColors.Panel)
                .border(1.dp, if (line.fromUser) NovaColors.Violet else NovaColors.PanelEdge, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(line.text, color = NovaColors.Text, fontSize = 15.sp, lineHeight = 21.sp)
            if (line.note.isNotBlank()) {
                Text(line.note, color = NovaColors.Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
    }
}
