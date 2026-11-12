package com.nova.ai.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.brain.ChatTurn
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.VoiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val CODE_PROMPT = """
Tum NOVA ho, ek friendly coding mentor. User Android phone par Termux, GitHub aur Kotlin/Compose/Python jaisi cheezein seekh aur bana raha hai.
Niyam:
- Samjhane ke liye saral Hinglish use karo (user Devanagari mein likhe to Devanagari mein).
- Code hamesha ``` fence mein likho, pehli line mein language ka naam (jaise ```kotlin).
- Error ho to pehle ek line mein kaaran, phir fix, phir poora sahi code ya command.
- Chhote steps mein bolo. Bina zaroorat lamba mat likho. Agar pakka nahi pata to saaf bolo.
- Emoji aur heading mat lagao.
"""

@Composable
fun CodeAssistantScreen(voice: VoiceController, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<ChatTurn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()

    fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || busy) return
        input = ""
        turns += ChatTurn(true, q)
        busy = true
        streaming = ""
        status = ""
        job = scope.launch {
            try {
                val context = turns.takeLast(8).toList()
                val (text, label) = voice.brain.answerLong(CODE_PROMPT.trimIndent(), context, q) { streaming += it }
                turns += ChatTurn(false, text)
                status = label
            } catch (e: CancellationException) {
                if (streaming.isNotBlank()) turns += ChatTurn(false, streaming)
                status = "Roka gaya"
            } catch (e: Exception) {
                if (streaming.isNotBlank()) turns += ChatTurn(false, streaming)
                status = e.message ?: "Kuch gadbad ho gayi"
            }
            streaming = ""
            busy = false
        }
    }

    val itemCount = turns.size + (if (busy) 1 else 0)
    LaunchedEffect(itemCount, streaming.length) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ToolHeader("Coding assistant", onBack)

        if (turns.isEmpty() && !busy) {
            Column(
                Modifier.weight(1f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Code ya error ka sawaal pucho. Jawab zyada lamba aata hai, isliye NOVA ise bolti nahi, likh kar dikhati hai.",
                    color = NovaColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
                for (s in listOf(
                    "Kotlin mein function aur lambda ka fark samjhao",
                    "Termux mein git push par authentication failed aa raha hai",
                    "GitHub Actions ka build fail ho gaya, kaise dekhun kya error hai?"
                )) {
                    ToolChip(s, false, { send(s) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(turns.size) { i -> Bubble(turns[i].text, turns[i].fromUser) }
                if (busy) {
                    item { Bubble(streaming.ifEmpty { "NOVA soch rahi hai…" }, false) }
                }
            }
        }

        if (status.isNotBlank()) {
            Text(status, color = NovaColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
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
                placeholder = { Text("Code ka sawaal likho…", color = NovaColors.Muted) },
                maxLines = 4
            )
            if (busy) {
                ToolButton("Roko", { job?.cancel() })
            } else {
                ToolButton("Bhejo", { send(input) }, enabled = input.isNotBlank())
            }
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    if (fromUser) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text,
                color = NovaColors.Text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .clip(shape)
                    .background(NovaColors.Violet.copy(alpha = 0.25f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    } else {
        RichAnswer(text)
    }
}

/** Plain text with ``` code blocks shown in a monospace box with a Copy button. */
@Composable
private fun RichAnswer(text: String) {
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { i, part ->
            if (i % 2 == 0) {
                if (part.isNotBlank()) {
                    Text(part.trim(), color = NovaColors.Text, fontSize = 15.sp, lineHeight = 22.sp)
                }
            } else {
                val nl = part.indexOf('\n')
                val first = if (nl >= 0) part.substring(0, nl).trim() else ""
                val hasLang = nl >= 0 && first.length <= 20 && !first.contains(' ')
                val lang = if (hasLang) first else ""
                val code = (if (hasLang) part.substring(nl + 1) else part).trimEnd()
                CodeBlock(lang, code)
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NovaColors.Void)
            .border(1.dp, NovaColors.PanelEdge, shape)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(lang.ifEmpty { "code" }, color = NovaColors.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Text("Copy", color = NovaColors.Cyan, fontSize = 12.sp)
            }
        }
        Text(
            code,
            color = NovaColors.Lavender,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
    }
}
