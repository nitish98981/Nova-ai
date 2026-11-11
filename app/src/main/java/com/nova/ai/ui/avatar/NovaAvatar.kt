package com.nova.ai.ui.avatar

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.nova.ai.voice.NovaState
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Face settings for one emotion. Values are animated smoothly between emotions. */
private data class Expr(
    val smile: Float,      // -1 sad ... 1 big smile
    val eyeOpen: Float,    // 0 closed ... 1.2 wide
    val browRaise: Float,  // 0..1
    val browTilt: Float,   // +1 worried/sad, -1 stern
    val browAsym: Float,   // extra raise of the right brow (curious / playful)
    val blush: Float,      // 0..1
    val lookX: Float,      // -1 left ... 1 right
    val lookY: Float,      // -1 up ... 1 down
    val mouthOpen: Float   // resting open mouth (surprise)
)

private fun exprFor(emotion: String): Expr = when (emotion) {
    "happy" -> Expr(0.9f, 0.8f, 0.2f, 0f, 0f, 0.35f, 0f, 0f, 0.15f)
    "playful" -> Expr(0.8f, 0.95f, 0.3f, -0.3f, 0.6f, 0.3f, 0.3f, 0f, 0f)
    "love" -> Expr(0.9f, 0.9f, 0.3f, 0.2f, 0f, 0.8f, 0f, 0f, 0f)
    "thinking" -> Expr(0.1f, 0.95f, 0.5f, 0f, 0.8f, 0.1f, 0.6f, -0.7f, 0f)
    "surprised" -> Expr(0f, 1.2f, 1f, 0f, 0f, 0.1f, 0f, 0f, 0.8f)
    "sad" -> Expr(-0.8f, 0.8f, 0.2f, 1f, 0f, 0.05f, 0f, 0.4f, 0f)
    "shy" -> Expr(0.35f, 0.9f, 0.1f, 0.4f, 0f, 0.9f, -0.6f, 0.6f, 0f)
    else -> Expr(0.35f, 1f, 0f, 0f, 0f, 0.15f, 0f, 0f, 0f)
}

@Composable
private fun animated(target: Expr): Expr {
    val spec = tween<Float>(durationMillis = 300)
    val smile by animateFloatAsState(target.smile, spec, label = "smile")
    val eyeOpen by animateFloatAsState(target.eyeOpen, spec, label = "eyeOpen")
    val browRaise by animateFloatAsState(target.browRaise, spec, label = "browRaise")
    val browTilt by animateFloatAsState(target.browTilt, spec, label = "browTilt")
    val browAsym by animateFloatAsState(target.browAsym, spec, label = "browAsym")
    val blush by animateFloatAsState(target.blush, spec, label = "blush")
    val lookX by animateFloatAsState(target.lookX, spec, label = "lookX")
    val lookY by animateFloatAsState(target.lookY, spec, label = "lookY")
    val mouthOpen by animateFloatAsState(target.mouthOpen, spec, label = "mouthOpen")
    return Expr(smile, eyeOpen, browRaise, browTilt, browAsym, blush, lookX, lookY, mouthOpen)
}

private val Hair = Color(0xFF120D26)
private val HairLight = Color(0xFF2A1F52)
private val HairRim = Color(0xFF7C5CF0)
private val Skin = Color(0xFFFFE2D4)
private val SkinShade = Color(0xFFF0C4B4)
private val Cloth = Color(0xFF1A1230)
private val Lash = Color(0xFF1B1030)
private val Pupil = Color(0xFF1B0B3A)
private val BlushPink = Color(0xFFFF8FB1)
private val MouthIn = Color(0xFF7A1F3D)
private val Tongue = Color(0xFFFF7A9A)
private val MouthLine = Color(0xFFB4506A)
private val Lavender = Color(0xFFC4B5FD)

/**
 * Phase 4 avatar: a stylised anime face drawn with Compose Canvas.
 * Blinks, breathes, changes expression with the AI's emotion tag and moves its mouth while speaking.
 * Lip-sync is approximate (mouth moves while NOVA talks, not synced word by word).
 */
@Composable
fun NovaAvatar(state: NovaState, emotion: String, modifier: Modifier = Modifier) {
    val effective = when (state) {
        NovaState.Thinking -> "thinking"
        NovaState.Listening -> "neutral"
        else -> emotion
    }
    val e = animated(exprFor(effective))

    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blink.animateTo(0f, tween(70))
            blink.animateTo(1f, tween(110))
        }
    }

    val t = rememberInfiniteTransition(label = "avatar")
    val m1 by t.animateFloat(
        initialValue = 0.1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse),
        label = "m1"
    )
    val m2 by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(263, easing = LinearEasing), RepeatMode.Reverse),
        label = "m2"
    )
    val breathe by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val speakOpen = if (state == NovaState.Speaking) 0.2f + 0.5f * m1 * (0.4f + 0.6f * m2) else 0f

    Canvas(modifier.aspectRatio(0.8f)) {
        translate(top = breathe * size.height * 0.004f) {
            drawNova(
                e = e,
                blink = blink.value,
                speakOpen = speakOpen,
                emotion = effective,
                thinkingPhase = m2,
                pulse = breathe
            )
        }
    }
}

private fun heartPath(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s)
    cubicTo(cx - 2f * s, cy - 0.2f * s, cx - 0.8f * s, cy - 1.6f * s, cx, cy - 0.6f * s)
    cubicTo(cx + 0.8f * s, cy - 1.6f * s, cx + 2f * s, cy - 0.2f * s, cx, cy + s)
    close()
}

private fun DrawScope.drawNova(
    e: Expr,
    blink: Float,
    speakOpen: Float,
    emotion: String,
    thinkingPhase: Float,
    pulse: Float
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val thin = 2.dp.toPx()
    val thick = 3.5.dp.toPx()

    // ---- back hair
    val back = Path().apply {
        moveTo(0.50f * w, 0.04f * h)
        cubicTo(0.06f * w, 0.04f * h, 0.02f * w, 0.60f * h, 0.12f * w, 0.98f * h)
        lineTo(0.88f * w, 0.98f * h)
        cubicTo(0.98f * w, 0.60f * h, 0.94f * w, 0.04f * h, 0.50f * w, 0.04f * h)
        close()
    }
    drawPath(back, Brush.verticalGradient(listOf(Hair, HairLight), startY = 0f, endY = h))
    drawPath(back, HairRim.copy(alpha = 0.55f), style = Stroke(thin))

    // ---- neck
    drawRoundRect(
        color = SkinShade,
        topLeft = Offset(0.44f * w, 0.62f * h),
        size = Size(0.12f * w, 0.20f * h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.03f * w, 0.03f * w)
    )

    // ---- shoulders / hoodie with NOVA triangle
    val body = Path().apply {
        moveTo(0.16f * w, h)
        cubicTo(0.18f * w, 0.82f * h, 0.34f * w, 0.77f * h, 0.50f * w, 0.77f * h)
        cubicTo(0.66f * w, 0.77f * h, 0.82f * w, 0.82f * h, 0.84f * w, h)
        close()
    }
    drawPath(body, Cloth)
    drawPath(body, HairRim.copy(alpha = 0.7f), style = Stroke(thin))
    val ts = 0.035f * w
    val logo = Path().apply {
        moveTo(cx - ts, 0.91f * h - ts * 0.7f)
        lineTo(cx + ts, 0.91f * h - ts * 0.7f)
        lineTo(cx, 0.91f * h + ts * 0.9f)
        close()
    }
    drawPath(logo, Lavender, style = Stroke(thin, cap = StrokeCap.Round))

    // ---- face
    val face = Path().apply {
        moveTo(0.20f * w, 0.42f * h)
        cubicTo(0.20f * w, 0.18f * h, 0.80f * w, 0.18f * h, 0.80f * w, 0.42f * h)
        cubicTo(0.80f * w, 0.60f * h, 0.60f * w, 0.72f * h, 0.50f * w, 0.72f * h)
        cubicTo(0.40f * w, 0.72f * h, 0.20f * w, 0.60f * h, 0.20f * w, 0.42f * h)
        close()
    }
    drawPath(face, Skin)

    // ---- blush
    val blushAlpha = (e.blush * 0.55f).coerceIn(0f, 1f)
    drawOval(BlushPink.copy(alpha = blushAlpha), Offset(0.24f * w, 0.585f * h), Size(0.14f * w, 0.05f * h))
    drawOval(BlushPink.copy(alpha = blushAlpha), Offset(0.62f * w, 0.585f * h), Size(0.14f * w, 0.05f * h))

    // ---- side locks
    drawOval(Hair, Offset(0.13f * w, 0.28f * h), Size(0.11f * w, 0.50f * h))
    drawOval(Hair, Offset(0.76f * w, 0.28f * h), Size(0.11f * w, 0.50f * h))

    // ---- eyes
    val ey = 0.50f * h
    val ew = 0.075f * w
    val eh = 0.075f * h
    val openAmt = (e.eyeOpen * blink).coerceAtLeast(0f)
    val irisPink = emotion == "love"
    for (ex in listOf(0.37f * w, 0.63f * w)) {
        if (openAmt < 0.12f) {
            drawArc(
                color = Lash, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(ex - ew, ey - 0.02f * h), size = Size(2f * ew, 0.04f * h),
                style = Stroke(thick, cap = StrokeCap.Round)
            )
        } else {
            val ry = eh * openAmt
            val eyePath = Path().apply { addOval(Rect(ex - ew, ey - ry, ex + ew, ey + ry)) }
            clipPath(eyePath) {
                drawRect(Color.White, Offset(ex - ew, ey - ry), Size(2f * ew, 2f * ry))
                val r = ew * 0.95f
                val ix = ex + e.lookX * ew * 0.35f
                val iy = ey + e.lookY * ry * 0.35f
                val top = Color(0xFF5B21B6)
                val bottom = if (irisPink) Color(0xFFF472B6) else Color(0xFFC084FC)
                drawOval(
                    brush = Brush.verticalGradient(listOf(top, bottom), startY = iy - r, endY = iy + r),
                    topLeft = Offset(ix - r * 0.85f, iy - r * 1.05f),
                    size = Size(r * 1.7f, r * 2.1f)
                )
                drawOval(Pupil, Offset(ix - r * 0.38f, iy - r * 0.5f), Size(r * 0.76f, r * 1.0f))
                drawCircle(Color.White, radius = r * 0.26f, center = Offset(ix - r * 0.3f, iy - r * 0.45f))
                drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.12f, center = Offset(ix + r * 0.35f, iy + r * 0.4f))
            }
            drawArc(
                color = Lash, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(ex - ew, ey - ry), size = Size(2f * ew, 2f * ry),
                style = Stroke(thick, cap = StrokeCap.Round)
            )
        }
    }

    // ---- eyebrows
    val browY = 0.405f * h - e.browRaise * 0.03f * h
    fun brow(xc: Float, leftSide: Boolean, extraRaise: Float) {
        val outerX = if (leftSide) xc - 0.055f * w else xc + 0.055f * w
        val innerX = if (leftSide) xc + 0.055f * w else xc - 0.055f * w
        val y = browY - extraRaise * 0.03f * h
        drawLine(
            color = Hair,
            start = Offset(outerX, y + e.browTilt * 0.010f * h),
            end = Offset(innerX, y - e.browTilt * 0.015f * h),
            strokeWidth = thick,
            cap = StrokeCap.Round
        )
    }
    brow(0.37f * w, true, 0f)
    brow(0.63f * w, false, e.browAsym)

    // ---- nose
    drawLine(
        color = Color(0xFFE0A899),
        start = Offset(cx, 0.585f * h),
        end = Offset(cx + 0.008f * w, 0.60f * h),
        strokeWidth = thin,
        cap = StrokeCap.Round
    )

    // ---- mouth
    val my = 0.66f * h
    val smileBoost = if (e.smile > 0f) e.smile * 0.2f else 0f
    val mw = 0.06f * w * (1f + smileBoost)
    val open = max(e.mouthOpen, speakOpen)
    if (open > 0.07f) {
        val mh = (0.012f + 0.05f * open) * h
        val mwo = mw * (1f - 0.35f * open)
        drawOval(MouthIn, Offset(cx - mwo, my - mh * 0.3f), Size(2f * mwo, mh))
        drawOval(Tongue, Offset(cx - mwo * 0.55f, my + mh * 0.25f), Size(mwo * 1.1f, mh * 0.42f))
    } else {
        val smilePath = Path().apply {
            moveTo(cx - mw, my)
            quadraticBezierTo(cx, my + e.smile * 0.045f * h, cx + mw, my)
        }
        drawPath(smilePath, MouthLine, style = Stroke(thin * 1.3f, cap = StrokeCap.Round))
    }

    // ---- bangs
    val bangs = Path().apply {
        moveTo(0.14f * w, 0.46f * h)
        cubicTo(0.10f * w, 0.06f * h, 0.90f * w, 0.06f * h, 0.86f * w, 0.46f * h)
        lineTo(0.76f * w, 0.30f * h)
        lineTo(0.68f * w, 0.37f * h)
        lineTo(0.58f * w, 0.24f * h)
        lineTo(0.50f * w, 0.33f * h)
        lineTo(0.42f * w, 0.24f * h)
        lineTo(0.30f * w, 0.37f * h)
        lineTo(0.24f * w, 0.30f * h)
        close()
    }
    drawPath(bangs, Brush.verticalGradient(listOf(Hair, HairLight), startY = 0f, endY = 0.46f * h))
    drawPath(bangs, HairRim.copy(alpha = 0.5f), style = Stroke(thin))

    // ---- butterfly hair clip
    drawOval(HairRim, Offset(0.66f * w, 0.17f * h), Size(0.07f * w, 0.05f * h))
    drawOval(HairRim, Offset(0.73f * w, 0.14f * h), Size(0.07f * w, 0.05f * h))
    drawCircle(Color(0xFF22D3EE), radius = 0.008f * w, center = Offset(0.725f * w, 0.185f * h))

    // ---- extras: heart when in love, dots while thinking
    if (emotion == "love") {
        val a = 0.6f + 0.4f * pulse
        drawPath(heartPath(0.88f * w, 0.30f * h, 0.035f * w), BlushPink.copy(alpha = a.coerceIn(0f, 1f)))
    }
    if (emotion == "thinking") {
        val active = (thinkingPhase * 2.99f).toInt()
        for (i in 0..2) {
            drawCircle(
                color = Lavender.copy(alpha = if (i == active) 1f else 0.3f),
                radius = 0.012f * w,
                center = Offset((0.82f + i * 0.045f) * w, 0.30f * h)
            )
        }
    }
}
