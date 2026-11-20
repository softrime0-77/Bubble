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
import com.bubble.game.game.Animal
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
    val wool = Color(0xFFFFFFFF)
    val woolShade = Color(0xFFEDE7F6)
    val sheepFace = Color(0xFFFFE4CC)
    val dogFur = Color(0xFFF3D2A2)
    val dogEar = Color(0xFFB7794A)
    val giraffe = Color(0xFFFFD45C)
    val giraffeSpot = Color(0xFFD9893A)
    val giraffeMuzzle = Color(0xFFFFEDB8)
}

/** 눈을 깜빡이며 살짝 흔들리는 캐릭터. [phase]로 개체마다 박자를 다르게 합니다. */
@Composable
fun Critter(
    side: Side,
    modifier: Modifier = Modifier,
    phase: Int = 0,
    happy: Boolean = false,
    /** 상점에서 산 동물을 장착했으면 그 동물로 그림 */
    skin: Animal? = null,
) {
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
            when (skin) {
                Animal.SHEEP -> drawSheep(eyesClosed, happy)
                Animal.DOG -> drawDog(eyesClosed, happy)
                Animal.GIRAFFE -> drawGiraffe(eyesClosed, happy)
                null -> if (side == Side.CAT) drawCat(eyesClosed, happy) else drawHamster(eyesClosed, happy)
            }
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

private fun DrawScope.drawSheep(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.56f
    val r = w * 0.36f
    // 몽글몽글 양털
    val puffs = listOf(
        Offset(0f, -1.0f), Offset(-0.7f, -0.75f), Offset(0.7f, -0.75f), Offset(-1.0f, -0.1f),
        Offset(1.0f, -0.1f), Offset(-0.75f, 0.55f), Offset(0.75f, 0.55f), Offset(0f, 0.8f),
    )
    puffs.forEach { o -> drawCircle(Palette.woolShade, r * 0.42f, Offset(cx + o.x * r, cy + o.y * r + r * 0.05f)) }
    puffs.forEach { o -> drawCircle(Palette.wool, r * 0.4f, Offset(cx + o.x * r, cy + o.y * r)) }
    drawCircle(Palette.wool, r * 0.85f, Offset(cx, cy))
    // 옆으로 난 귀
    for (s in listOf(-1f, 1f)) {
        drawOval(Palette.sheepFace, Offset(cx + s * r * 0.95f - r * 0.3f, cy - r * 0.2f), Size(r * 0.6f, r * 0.3f))
    }
    // 얼굴
    drawOval(Palette.sheepFace, Offset(cx - r * 0.6f, cy - r * 0.45f), Size(r * 1.2f, r * 1.15f))
    // 앞머리 털
    for (i in -1..1) drawCircle(Palette.wool, r * 0.24f, Offset(cx + i * r * 0.3f, cy - r * 0.5f))
    drawEyes(cx, cy, r * 0.3f, r * 0.12f, closed, happy)
    drawCircle(Palette.blush, r * 0.13f, Offset(cx - r * 0.42f, cy + r * 0.25f))
    drawCircle(Palette.blush, r * 0.13f, Offset(cx + r * 0.42f, cy + r * 0.25f))
    drawOval(Palette.nose, Offset(cx - r * 0.09f, cy + r * 0.2f), Size(r * 0.18f, r * 0.11f))
    drawMouth(cx, cy + r * 0.34f, r * 0.12f)
}

private fun DrawScope.drawDog(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.56f
    val r = w * 0.37f
    // 축 늘어진 귀
    for (s in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(cx + s * r * 0.55f, cy - r * 0.8f)
            quadraticBezierTo(cx + s * r * 1.35f, cy - r * 0.7f, cx + s * r * 1.15f, cy + r * 0.35f)
            quadraticBezierTo(cx + s * r * 0.95f, cy + r * 0.55f, cx + s * r * 0.7f, cy + r * 0.15f)
            close()
        }
        drawPath(ear, Palette.dogEar)
    }
    drawOval(Palette.dogFur, Offset(cx - r, cy - r * 0.95f), Size(r * 2f, r * 1.85f))
    // 한쪽 눈 얼룩
    drawOval(Palette.dogEar.copy(alpha = 0.55f), Offset(cx + r * 0.12f, cy - r * 0.42f), Size(r * 0.55f, r * 0.5f))
    // 주둥이
    drawOval(Color.White, Offset(cx - r * 0.45f, cy + r * 0.02f), Size(r * 0.9f, r * 0.62f))
    drawEyes(cx, cy - r * 0.15f, r * 0.38f, r * 0.14f, closed, happy)
    drawCircle(Palette.blush, r * 0.15f, Offset(cx - r * 0.65f, cy + r * 0.2f))
    drawCircle(Palette.blush, r * 0.15f, Offset(cx + r * 0.65f, cy + r * 0.2f))
    drawOval(Palette.ink, Offset(cx - r * 0.14f, cy + r * 0.06f), Size(r * 0.28f, r * 0.19f))
    drawMouth(cx, cy + r * 0.3f, r * 0.14f)
    // 쏙 내민 혀
    drawOval(Palette.nose, Offset(cx - r * 0.09f, cy + r * 0.36f), Size(r * 0.18f, r * 0.22f))
}

private fun DrawScope.drawGiraffe(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.6f
    val r = w * 0.34f
    // 뿔 (오시콘)
    for (s in listOf(-1f, 1f)) {
        drawLine(Palette.giraffeSpot, Offset(cx + s * r * 0.35f, cy - r * 0.7f), Offset(cx + s * r * 0.45f, cy - r * 1.35f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
        drawCircle(Palette.giraffeSpot, r * 0.14f, Offset(cx + s * r * 0.45f, cy - r * 1.38f))
    }
    // 귀
    for (s in listOf(-1f, 1f)) {
        drawOval(Palette.giraffe, Offset(cx + s * r * 0.95f - r * 0.32f, cy - r * 0.75f), Size(r * 0.64f, r * 0.3f))
        drawOval(Palette.hamInner, Offset(cx + s * r * 0.95f - r * 0.2f, cy - r * 0.7f), Size(r * 0.4f, r * 0.17f))
    }
    // 길쭉한 얼굴
    drawOval(Palette.giraffe, Offset(cx - r * 0.8f, cy - r * 0.95f), Size(r * 1.6f, r * 1.95f))
    // 무늬
    drawCircle(Palette.giraffeSpot.copy(alpha = 0.8f), r * 0.14f, Offset(cx - r * 0.42f, cy - r * 0.62f))
    drawCircle(Palette.giraffeSpot.copy(alpha = 0.8f), r * 0.1f, Offset(cx + r * 0.5f, cy - r * 0.5f))
    drawCircle(Palette.giraffeSpot.copy(alpha = 0.8f), r * 0.09f, Offset(cx + r * 0.15f, cy - r * 0.78f))
    // 주둥이
    drawOval(Palette.giraffeMuzzle, Offset(cx - r * 0.6f, cy + r * 0.15f), Size(r * 1.2f, r * 0.8f))
    drawEyes(cx, cy - r * 0.2f, r * 0.36f, r * 0.13f, closed, happy)
    drawCircle(Palette.blush, r * 0.14f, Offset(cx - r * 0.6f, cy + r * 0.12f))
    drawCircle(Palette.blush, r * 0.14f, Offset(cx + r * 0.6f, cy + r * 0.12f))
    drawCircle(Palette.ink, r * 0.06f, Offset(cx - r * 0.2f, cy + r * 0.38f))
    drawCircle(Palette.ink, r * 0.06f, Offset(cx + r * 0.2f, cy + r * 0.38f))
    drawMouth(cx, cy + r * 0.6f, r * 0.13f)
}
