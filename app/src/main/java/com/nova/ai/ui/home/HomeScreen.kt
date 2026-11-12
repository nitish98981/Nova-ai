package com.nova.ai.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.ui.avatar.NovaAvatar
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.NovaState

/**
 * Home: menu (Customise) top-left, notifications top-right, NOVA in the middle (tap her to talk),
 * and four big buttons at the bottom: Chat, Image, Code, More.
 */
@Composable
fun HomeScreen(
    state: NovaState,
    level: Float,
    heard: String,
    message: String,
    source: String,
    emotion: String,
    showAvatar: Boolean,
    unread: Int,
    onMic: () -> Unit,
    onCustomise: () -> Unit,
    onNotifications: () -> Unit,
    onChat: () -> Unit,
    onImage: () -> Unit,
    onCode: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCustomise) {
                Icon(Icons.Default.Menu, contentDescription = "Customise", tint = NovaColors.Lavender)
            }
            Text(
                "NOVA",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = NovaColors.Lavender
            )
            Box {
                IconButton(onClick = onNotifications) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = NovaColors.Lavender)
                }
                if (unread > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NovaColors.Cyan)
                    )
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onMic),
            contentAlignment = Alignment.Center
        ) {
            val d = minOf(maxWidth * 0.95f, maxHeight)
            NovaCore(state = state, level = level, modifier = Modifier.size(d))
            if (showAvatar) {
                NovaAvatar(state = state, emotion = emotion, modifier = Modifier.height(d * 0.8f))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NovaColors.Panel)
                .border(1.dp, NovaColors.PanelEdge, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (heard.isNotBlank()) {
                    Text(
                        "Tum: $heard", color = NovaColors.Muted, fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    message, color = NovaColors.Text, fontSize = 14.sp, lineHeight = 20.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis
                )
                if (source.isNotBlank()) {
                    Text(
                        source, color = NovaColors.Muted, fontSize = 10.sp, lineHeight = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(active = state == NovaState.Listening, onClick = onMic)
        }
        Text(
            when (state) {
                NovaState.Idle -> "Tap karke bolo"
                NovaState.Listening -> "Sun rahi hoon… dubara tap karo to ruk jaungi"
                NovaState.Thinking -> "Soch rahi hoon… tap karke naya sawaal pucho"
                NovaState.Speaking -> "Bol rahi hoon… tap karke beech mein roko"
            },
            color = NovaColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile("Chat", onChat, Modifier.weight(1f))
            ToolTile("Image banao", onImage, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile("Code", onCode, Modifier.weight(1f))
            ToolTile("More", onMore, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ToolTile(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NovaColors.Violet.copy(alpha = 0.15f))
            .border(1.dp, NovaColors.Violet.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = NovaColors.Lavender, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MicButton(active: Boolean, onClick: () -> Unit) {
    val accent = if (active) NovaColors.Cyan else NovaColors.Violet
    Box(
        Modifier
            .size(60.dp)
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
    Canvas(Modifier.size(26.dp)) {
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
