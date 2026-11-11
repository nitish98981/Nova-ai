package com.nova.ai.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.NovaState

@Composable
fun HomeScreen(
    state: NovaState,
    level: Float,
    heard: String,
    message: String,
    source: String,
    brainOnline: Boolean,
    onMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NOVA",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = NovaColors.Lavender
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenPermissions) {
                Text("Permissions", color = NovaColors.Muted)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NovaColors.Lavender)
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            NovaCore(state = state, level = level, modifier = Modifier.fillMaxWidth(0.85f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NovaColors.Panel)
                .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (heard.isNotBlank()) {
                    Text("Tum: $heard", color = NovaColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Text(message, color = NovaColors.Text, fontSize = 15.sp, lineHeight = 22.sp)
                if (source.isNotBlank()) {
                    Text(source, color = NovaColors.Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PhaseChip("Voice", "Ready", NovaColors.Good, Modifier.weight(1f))
            PhaseChip(
                "Brain",
                if (brainOnline) "Online" else "Offline",
                if (brainOnline) NovaColors.Good else NovaColors.Muted,
                Modifier.weight(1f)
            )
            PhaseChip("Control", "Phase 5", NovaColors.Muted, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(active = state == NovaState.Listening, onClick = onMic)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (state) {
                NovaState.Idle -> "Tap karke bolo"
                NovaState.Listening -> "Sun rahi hoon… dubara tap karo to ruk jaungi"
                NovaState.Thinking -> "Soch rahi hoon… tap karke naya sawaal pucho"
                NovaState.Speaking -> "Bol rahi hoon… tap karke beech mein roko"
            },
            color = NovaColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PhaseChip(name: String, phase: String, phaseColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, color = NovaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(phase, color = phaseColor, fontSize = 12.sp)
    }
}

@Composable
private fun MicButton(active: Boolean, onClick: () -> Unit) {
    val accent = if (active) NovaColors.Cyan else NovaColors.Violet
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
            .border(2.dp, accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MicGlyph(color = if (active) NovaColors.Cyan else NovaColors.Lavender)
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, 0f),
            size = Size(w * 0.28f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.22f),
            size = Size(w * 0.6f, h * 0.5f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.95f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
