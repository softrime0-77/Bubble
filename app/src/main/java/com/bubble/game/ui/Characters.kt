package com.bubble.game.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import com.bubble.game.game.Side

object Palette {
    val catFur = Color(0xFFFFB74D)
    val catStripe = Color(0xFFF57C00)
    val catInner = Color(0xFFFFE0B2)
    val hamFur = Color(0xFFE6B98A)
    val hamBelly = Color(0xFFFFF3E0)
    val hamInner = Color(0xFFF8BBD0)
    val ink = Color(0xFF4E342E)
    val blush = Color(0x99FF80AB)
    val nose = Color(0xFFFF6F91)
}

/** 눈을 깜빡이며 살짝 흔들리는 캐릭터. [phase]로 개체마다 박자를 다르게 합니다. */
@Composable
fun Critter(side: Side, modifier: Modifier = Modifier, phase: Int = 0, happy: Boolean = false) {
    val t = rememberInfiniteTransition(label = "critter")
    val sway by t.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900 + phase * 137 % 400), RepeatMode.Reverse),
        label = "sway",
    )
    val blink by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200 + phase * 311 % 1_500)),
        label = "blink",
    )
    val eyesClosed = blink > 0.94f
    Canvas(modifier) {
        rotate(sway, pivot = Offset(size.width / 2, size.height * 0.9f)) {
            if (side == Side.CAT) drawCat(eyesClosed, happy) else drawHamster(eyesClosed, happy)
        }
    }
}

private fun DrawScope.drawEyes(cx: Float, cy: Float, gap: Float, r: Float, closed: Boolean, happy: Boolean) {
    for (dx in listOf(-gap, gap)) {
        if (closed || happy) {
            val p = Path().apply {
                moveTo(cx + dx - r, cy)
                quadraticBezierTo(cx + dx, cy - r * 1.3f, cx + dx + r, cy)
            }
            drawPath(p, Palette.ink, style = Stroke(width = r * 0.55f, cap = StrokeCap.Round))
        } else {
            drawCircle(Palette.ink, r, Offset(cx + dx, cy))
            drawCircle(Color.White, r * 0.38f, Offset(cx + dx + r * 0.3f, cy - r * 0.35f))
        }
    }
}

private fun DrawScope.drawMouth(cx: Float, cy: Float, w: Float) {
    val p = Path().apply {
        moveTo(cx - w, cy)
        quadraticBezierTo(cx - w / 2, cy + w * 0.8f, cx, cy)
        quadraticBezierTo(cx + w / 2, cy + w * 0.8f, cx + w, cy)
    }
    drawPath(p, Palette.ink, style = Stroke(width = w * 0.28f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCat(closed: Boolean, happy: Boolean) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h * 0.58f
    val r = w * 0.36f
    // 귀
    for (s in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(cx + s * r * 0.95f, cy - r * 0.35f)
            lineTo(cx + s * r * 0.85f, cy - r * 1.35f)
            lineTo(cx + s * r * 0.2f, cy - r * 0.85f)
            close()
        }
        drawPath(ear, Palette.catFur)
        val inner = Path().apply {
            moveTo(cx + s * r * 0.8f, cy - r * 0.55f)
            lineTo(cx + s * r * 0.78f, cy - r * 1.1f)
            lineTo(cx + s * r * 0.4f, cy - r * 0.8f)
            close()
        }
        drawPath(inner, Palette.hamInner)
    }
    // 얼굴
    drawOval(Palette.catFur, Offset(cx - r * 1.05f, cy - r * 0.95f), Size(r * 2.1f, r * 1.85f))
    // 줄무늬
    for (i in -1..1) {
        drawLine(
            Palette.catStripe,
            Offset(cx + i * r * 0.28f, cy - r * 0.92f),
            Offset(cx + i * r * 0.22f, cy - r * 0.6f),
            strokeWidth = r * 0.12f, cap = StrokeCap.Round,
        )
    }
    drawOval(Palette.catInner, Offset(cx - r * 0.55f, cy + r * 0.05f), Size(r * 1.1f, r * 0.7f))
    drawEyes(cx, cy - r * 0.12f, r * 0.42f, r * 0.15f, closed, happy)
    drawCircle(Palette.blush, r * 0.17f, Offset(cx - r * 0.7f, cy + r * 0.18f))
    drawCircle(Palette.blush, r * 0.17f, Offset(cx + r * 0.7f, cy + r * 0.18f))
    drawCircle(Palette.nose, r * 0.08f, Offset(cx, cy + r * 0.15f))
    drawMouth(cx, cy + r * 0.25f, r * 0.17f)
    // 수염
    for (s in listOf(-1f, 1f)) for (k in listOf(-0.08f, 0.1f)) {
        drawLine(
            Palette.ink.copy(alpha = 0.6f),
            Offset(cx + s * r * 0.45f, cy + r * (0.25f + k)),
            Offset(cx + s * r * 1.15f, cy + r * (0.18f + k * 1.8f)),
            strokeWidth = r * 0.04f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawHamster(closed: Boolean, happy: Boolean) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h * 0.58f
    val r = w * 0.38f
    // 동그란 귀
    for (s in listOf(-1f, 1f)) {
        drawCircle(Palette.hamFur, r * 0.32f, Offset(cx + s * r * 0.7f, cy - r * 0.8f))
        drawCircle(Palette.hamInner, r * 0.18f, Offset(cx + s * r * 0.7f, cy - r * 0.78f))
    }
    // 통통한 몸/얼굴
    drawOval(Palette.hamFur, Offset(cx - r * 1.1f, cy - r * 0.95f), Size(r * 2.2f, r * 1.9f))
    // 하얀 배/볼
    drawOval(Palette.hamBelly, Offset(cx - r * 0.75f, cy - r * 0.1f), Size(r * 1.5f, r * 0.95f))
    drawOval(Palette.hamBelly, Offset(cx - r * 0.25f, cy - r * 0.75f), Size(r * 0.5f, r * 0.8f))
    drawEyes(cx, cy - r * 0.15f, r * 0.45f, r * 0.14f, closed, happy)
    drawCircle(Palette.blush, r * 0.2f, Offset(cx - r * 0.72f, cy + r * 0.15f))
    drawCircle(Palette.blush, r * 0.2f, Offset(cx + r * 0.72f, cy + r * 0.15f))
    drawOval(Palette.nose, Offset(cx - r * 0.08f, cy + r * 0.05f), Size(r * 0.16f, r * 0.11f))
    drawMouth(cx, cy + r * 0.2f, r * 0.14f)
    // 작은 앞발
    for (s in listOf(-1f, 1f)) {
        drawOval(Palette.hamInner, Offset(cx + s * r * 0.32f - r * 0.12f, cy + r * 0.55f), Size(r * 0.24f, r * 0.18f))
    }
}

/** 세트 안에 n마리를 동그랗게 배치할 때 쓰는 위치 (단위: 세트 반지름 대비). */
fun critterSlots(n: Int): List<Offset> = when (n) {
    1 -> listOf(Offset(0f, 0f))
    2 -> listOf(Offset(-0.33f, 0f), Offset(0.33f, 0f))
    3 -> listOf(Offset(0f, -0.3f), Offset(-0.33f, 0.25f), Offset(0.33f, 0.25f))
    4 -> listOf(Offset(-0.32f, -0.3f), Offset(0.32f, -0.3f), Offset(-0.32f, 0.3f), Offset(0.32f, 0.3f))
    else -> emptyList()
}

/** 작은 장식용 하트/반짝이. */
fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    translate(center.x, center.y) {
        val p = Path().apply {
            moveTo(0f, -r)
            quadraticBezierTo(0f, 0f, r, 0f)
            quadraticBezierTo(0f, 0f, 0f, r)
            quadraticBezierTo(0f, 0f, -r, 0f)
            quadraticBezierTo(0f, 0f, 0f, -r)
            close()
        }
        drawPath(p, color)
    }
}
