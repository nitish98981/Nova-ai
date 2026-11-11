package com.nova.ai.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.nova.ai.ui.theme.NovaColors
import com.nova.ai.voice.NovaState

/**
 * Phase 1 placeholder for NOVA's avatar: a living "core" made of rings.
 * The real anime avatar (2D first, 3D later) replaces this in Phase 4.
 */
@Composable
fun NovaCore(state: NovaState, level: Float, modifier: Modifier = Modifier) {
    val smoothLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(durationMillis = 120),
        label = "level"
    )
    val transition = rememberInfiniteTransition(label = "core")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (state) {
                    NovaState.Idle -> 16000
                    NovaState.Listening -> 5000
                    NovaState.Thinking -> 2200
                    NovaState.Speaking -> 3500
                },
                easing = LinearEasing
            )
        ),
        label = "spin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val accent = when (state) {
            NovaState.Idle -> NovaColors.Violet
            NovaState.Listening -> NovaColors.Cyan
            NovaState.Thinking -> NovaColors.Cyan
            NovaState.Speaking -> NovaColors.Lavender
        }
        val boost = if (state == NovaState.Listening) smoothLevel * 0.18f else 0f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )

        drawCircle(
            color = NovaColors.Violet.copy(alpha = 0.55f),
            radius = r * 0.72f * (pulse + boost),
            center = c,
            style = Stroke(width = 2.dp.toPx())
        )

        rotate(degrees = spin, pivot = c) {
            val d = r * 1.76f
            val tl = Offset(c.x - d / 2f, c.y - d / 2f)
            drawArc(
                color = accent,
                startAngle = 0f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = tl,
                size = Size(d, d),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = accent.copy(alpha = 0.6f),
                startAngle = 180f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = tl,
                size = Size(d, d),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        rotate(degrees = -spin * 0.6f, pivot = c) {
            val d = r * 1.3f
            drawArc(
                color = NovaColors.Lavender.copy(alpha = 0.8f),
                startAngle = 90f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(c.x - d / 2f, c.y - d / 2f),
                size = Size(d, d),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val s = r * 0.26f
        val triangle = Path().apply {
            moveTo(c.x - s, c.y - s * 0.7f)
            lineTo(c.x + s, c.y - s * 0.7f)
            lineTo(c.x, c.y + s * 0.9f)
            close()
        }
        drawPath(
            path = triangle,
            color = NovaColors.Lavender,
            style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round)
        )
    }
}
