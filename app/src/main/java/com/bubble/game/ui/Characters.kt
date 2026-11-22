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
import androidx.compose.ui.graphics.Brush
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
    /** 🐰 연승 토끼의 리본 수 (연승, 최대 5) */
    ribbons: Int = 0,
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
                Animal.HAMSTER -> drawHamster(eyesClosed, happy)
                Animal.PINK_CAT -> drawCat(eyesClosed, happy, fur = Color(0xFFF8A5C2), stripe = Color(0xFFE2558F), muzzle = Color(0xFFFFE3EF))
                Animal.BLACK_CAT -> drawCat(eyesClosed, happy, fur = Color(0xFF5B5662), stripe = Color(0xFF38343E), muzzle = Color(0xFFCFC8D6))
                Animal.SNOW_HAMSTER -> drawHamster(eyesClosed, happy, fur = Color(0xFFF4F6FB), belly = Color.White, inner = Color(0xFFBFD8FF), sparkles = Color(0xFFBFE6FF))
                Animal.GOLD_HAMSTER -> drawHamster(eyesClosed, happy, fur = Color(0xFFFFC94D), belly = Color(0xFFFFF3C4), inner = Color(0xFFFFB38A), sparkles = Color(0xFFFFF59D))
                Animal.OCTOPUS -> drawOctopus(eyesClosed, happy)
                Animal.PENGUIN -> drawPenguin(eyesClosed, happy)
                Animal.PANDA -> drawPanda(eyesClosed, happy)
                Animal.DRAGON -> drawDragon(eyesClosed, happy)
                Animal.RABBIT -> drawRabbit(eyesClosed, happy, ribbons)
                Animal.UNICORN -> drawUnicorn(eyesClosed, happy)
                Animal.LUCKY_CAT -> {
                    drawCat(eyesClosed, happy, fur = Color(0xFFFFFBF2), stripe = Color(0xFFF2A33A), muzzle = Color(0xFFFFFFFF))
                    drawLuckyCharm()
                }
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

private fun DrawScope.drawCat(
    closed: Boolean,
    happy: Boolean,
    fur: Color = Palette.catFur,
    stripe: Color = Palette.catStripe,
    muzzle: Color = Palette.catInner,
) {
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
        drawPath(ear, fur)
        val inner = Path().apply {
            moveTo(cx + s * r * 0.8f, cy - r * 0.55f)
            lineTo(cx + s * r * 0.78f, cy - r * 1.1f)
            lineTo(cx + s * r * 0.4f, cy - r * 0.8f)
            close()
        }
        drawPath(inner, Palette.hamInner)
    }
    // 얼굴
    drawOval(fur, Offset(cx - r * 1.05f, cy - r * 0.95f), Size(r * 2.1f, r * 1.85f))
    // 줄무늬
    for (i in -1..1) {
        drawLine(
            stripe,
            Offset(cx + i * r * 0.28f, cy - r * 0.92f),
            Offset(cx + i * r * 0.22f, cy - r * 0.6f),
            strokeWidth = r * 0.12f, cap = StrokeCap.Round,
        )
    }
    drawOval(muzzle, Offset(cx - r * 0.55f, cy + r * 0.05f), Size(r * 1.1f, r * 0.7f))
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

/** 복냥이: 빨간 목줄 + 금화 방울 + 이마의 복 무늬 */
private fun DrawScope.drawLuckyCharm() {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h * 0.58f
    val r = w * 0.36f
    // 이마 얼룩 (삼색이 느낌)
    drawOval(Color(0xFF4A3B3B).copy(alpha = 0.85f), Offset(cx + r * 0.25f, cy - r * 0.9f), Size(r * 0.55f, r * 0.38f))
    // 빨간 목줄
    val collar = Path().apply {
        moveTo(cx - r * 0.85f, cy + r * 0.72f)
        quadraticTo(cx, cy + r * 1.08f, cx + r * 0.85f, cy + r * 0.72f)
    }
    drawPath(collar, Color(0xFFE5303F), style = Stroke(width = r * 0.17f, cap = StrokeCap.Round))
    // 금화 방울
    val bell = Offset(cx, cy + r * 1.0f)
    drawCircle(Color(0xFFFFC21A), r * 0.25f, bell)
    drawCircle(Color(0xFFE59A00), r * 0.25f, bell, style = Stroke(width = r * 0.05f))
    drawCircle(Color(0xFFE59A00), r * 0.12f, bell, style = Stroke(width = r * 0.04f))
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.06f, bell + Offset(-r * 0.09f, -r * 0.09f))
}

private fun DrawScope.drawHamster(
    closed: Boolean,
    happy: Boolean,
    fur: Color = Palette.hamFur,
    belly: Color = Palette.hamBelly,
    inner: Color = Palette.hamInner,
    /** 유료 스킨: 머리 위 반짝이 색 (null이면 없음) */
    sparkles: Color? = null,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h * 0.58f
    val r = w * 0.38f
    // 동그란 귀
    for (s in listOf(-1f, 1f)) {
        drawCircle(fur, r * 0.32f, Offset(cx + s * r * 0.7f, cy - r * 0.8f))
        drawCircle(inner, r * 0.18f, Offset(cx + s * r * 0.7f, cy - r * 0.78f))
    }
    // 통통한 몸/얼굴
    drawOval(fur, Offset(cx - r * 1.1f, cy - r * 0.95f), Size(r * 2.2f, r * 1.9f))
    // 하얀 배/볼
    drawOval(belly, Offset(cx - r * 0.75f, cy - r * 0.1f), Size(r * 1.5f, r * 0.95f))
    drawOval(belly, Offset(cx - r * 0.25f, cy - r * 0.75f), Size(r * 0.5f, r * 0.8f))
    drawEyes(cx, cy - r * 0.15f, r * 0.45f, r * 0.14f, closed, happy)
    drawCircle(Palette.blush, r * 0.2f, Offset(cx - r * 0.72f, cy + r * 0.15f))
    drawCircle(Palette.blush, r * 0.2f, Offset(cx + r * 0.72f, cy + r * 0.15f))
    drawOval(Palette.nose, Offset(cx - r * 0.08f, cy + r * 0.05f), Size(r * 0.16f, r * 0.11f))
    drawMouth(cx, cy + r * 0.2f, r * 0.14f)
    // 작은 앞발
    for (s in listOf(-1f, 1f)) {
        drawOval(inner, Offset(cx + s * r * 0.32f - r * 0.12f, cy + r * 0.55f), Size(r * 0.24f, r * 0.18f))
    }
    sparkles?.let { c ->
        sparkle(Offset(cx - r * 1.0f, cy - r * 1.05f), r * 0.2f, c)
        sparkle(Offset(cx + r * 1.05f, cy - r * 0.75f), r * 0.14f, Color.White)
        sparkle(Offset(cx + r * 0.25f, cy - r * 1.25f), r * 0.11f, c)
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


// ======================= 새 캐릭터 =======================

/** 🐙 문어: 보라 머리 + 아래로 꼬불꼬불 다리 */
private fun DrawScope.drawOctopus(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.52f
    val r = w * 0.36f
    val body = Color(0xFFB57BEE)
    val dark = Color(0xFF8E52D6)
    // 다리 6개
    for (i in 0 until 6) {
        val x = cx + (i - 2.5f) * r * 0.36f
        val leg = Path().apply {
            moveTo(x - r * 0.13f, cy + r * 0.45f)
            quadraticTo(x - r * 0.2f + (i % 2) * r * 0.3f, cy + r * 1.05f, x + (if (i < 3) -1 else 1) * r * 0.16f, cy + r * 1.12f)
            quadraticTo(x + r * 0.05f, cy + r * 0.85f, x + r * 0.13f, cy + r * 0.45f)
            close()
        }
        drawPath(leg, if (i % 2 == 0) body else dark)
    }
    // 둥근 머리
    drawOval(body, Offset(cx - r * 1.0f, cy - r * 1.05f), Size(r * 2f, r * 1.75f))
    // 머리 무늬
    drawCircle(dark.copy(alpha = 0.6f), r * 0.13f, Offset(cx - r * 0.45f, cy - r * 0.68f))
    drawCircle(dark.copy(alpha = 0.6f), r * 0.09f, Offset(cx + r * 0.3f, cy - r * 0.78f))
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.12f, Offset(cx - r * 0.55f, cy - r * 0.45f))
    drawEyes(cx, cy - r * 0.05f, r * 0.38f, r * 0.15f, closed, happy)
    drawCircle(Palette.blush, r * 0.16f, Offset(cx - r * 0.68f, cy + r * 0.25f))
    drawCircle(Palette.blush, r * 0.16f, Offset(cx + r * 0.68f, cy + r * 0.25f))
    // 동그랗게 내민 입
    drawCircle(dark, r * 0.1f, Offset(cx, cy + r * 0.32f))
    drawCircle(Palette.nose, r * 0.05f, Offset(cx, cy + r * 0.33f))
}

/** 🐧 펭귄: 검은 머리 + 하얀 하트 얼굴 + 주황 부리 */
private fun DrawScope.drawPenguin(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.57f
    val r = w * 0.37f
    val navy = Color(0xFF3A4A68)
    drawOval(navy, Offset(cx - r, cy - r * 1.0f), Size(r * 2f, r * 1.95f))
    // 하얀 얼굴 (하트 모양)
    drawOval(Color.White, Offset(cx - r * 0.78f, cy - r * 0.55f), Size(r * 0.9f, r * 1.25f))
    drawOval(Color.White, Offset(cx - r * 0.12f, cy - r * 0.55f), Size(r * 0.9f, r * 1.25f))
    drawOval(Color.White, Offset(cx - r * 0.6f, cy + r * 0.05f), Size(r * 1.2f, r * 0.8f))
    // 정수리 하이라이트
    drawOval(Color.White.copy(alpha = 0.25f), Offset(cx - r * 0.5f, cy - r * 0.92f), Size(r * 0.5f, r * 0.22f))
    drawEyes(cx, cy - r * 0.08f, r * 0.36f, r * 0.14f, closed, happy)
    drawCircle(Palette.blush, r * 0.16f, Offset(cx - r * 0.6f, cy + r * 0.25f))
    drawCircle(Palette.blush, r * 0.16f, Offset(cx + r * 0.6f, cy + r * 0.25f))
    // 부리
    val beak = Path().apply {
        moveTo(cx - r * 0.18f, cy + r * 0.12f)
        lineTo(cx + r * 0.18f, cy + r * 0.12f)
        lineTo(cx, cy + r * 0.38f)
        close()
    }
    drawPath(beak, Color(0xFFFFA726))
}

/** 🐼 판다: 하얀 얼굴 + 까만 귀·눈 얼룩 */
private fun DrawScope.drawPanda(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.58f
    val r = w * 0.37f
    val black = Color(0xFF3B3B45)
    for (s in listOf(-1f, 1f)) drawCircle(black, r * 0.32f, Offset(cx + s * r * 0.78f, cy - r * 0.78f))
    drawOval(Color.White, Offset(cx - r * 1.05f, cy - r * 0.95f), Size(r * 2.1f, r * 1.85f))
    // 눈 얼룩 (살짝 기운 타원)
    for (s in listOf(-1f, 1f)) {
        rotate(s * -25f, pivot = Offset(cx + s * r * 0.4f, cy - r * 0.1f)) {
            drawOval(black, Offset(cx + s * r * 0.4f - r * 0.24f, cy - r * 0.1f - r * 0.3f), Size(r * 0.48f, r * 0.6f))
        }
    }
    // 눈 (얼룩 위라 흰 눈동자)
    for (s in listOf(-1f, 1f)) {
        val c = Offset(cx + s * r * 0.4f, cy - r * 0.12f)
        if (closed || happy) {
            val p = Path().apply { moveTo(c.x - r * 0.12f, c.y); quadraticTo(c.x, c.y - r * 0.16f, c.x + r * 0.12f, c.y) }
            drawPath(p, Color.White, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color.White, r * 0.11f, c)
            drawCircle(black, r * 0.06f, c + Offset(r * 0.02f, 0f))
        }
    }
    drawCircle(Palette.blush, r * 0.15f, Offset(cx - r * 0.7f, cy + r * 0.28f))
    drawCircle(Palette.blush, r * 0.15f, Offset(cx + r * 0.7f, cy + r * 0.28f))
    drawOval(black, Offset(cx - r * 0.14f, cy + r * 0.14f), Size(r * 0.28f, r * 0.18f))
    drawMouth(cx, cy + r * 0.36f, r * 0.14f)
    // 대나무 잎 (머리 위)
    val leaf = Path().apply {
        moveTo(cx + r * 0.2f, cy - r * 0.95f)
        quadraticTo(cx + r * 0.55f, cy - r * 1.35f, cx + r * 0.75f, cy - r * 1.25f)
        quadraticTo(cx + r * 0.5f, cy - r * 1.0f, cx + r * 0.2f, cy - r * 0.95f)
        close()
    }
    drawPath(leaf, Color(0xFF6CCB5F))
}

/** 🐉 아기 용: 민트 얼굴 + 작은 뿔 + 귀 지느러미 */
private fun DrawScope.drawDragon(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.58f
    val r = w * 0.36f
    val green = Color(0xFF7DDB9B)
    val dark = Color(0xFF3FAE6A)
    // 귀 지느러미
    for (s in listOf(-1f, 1f)) {
        val fin = Path().apply {
            moveTo(cx + s * r * 0.8f, cy - r * 0.45f)
            lineTo(cx + s * r * 1.35f, cy - r * 0.75f)
            lineTo(cx + s * r * 1.2f, cy - r * 0.35f)
            lineTo(cx + s * r * 1.38f, cy - r * 0.15f)
            lineTo(cx + s * r * 0.85f, cy - r * 0.05f)
            close()
        }
        drawPath(fin, dark)
    }
    // 뿔
    for (s in listOf(-1f, 1f)) {
        val horn = Path().apply {
            moveTo(cx + s * r * 0.25f, cy - r * 0.8f)
            quadraticTo(cx + s * r * 0.35f, cy - r * 1.35f, cx + s * r * 0.55f, cy - r * 1.4f)
            quadraticTo(cx + s * r * 0.5f, cy - r * 1.05f, cx + s * r * 0.55f, cy - r * 0.75f)
            close()
        }
        drawPath(horn, Color(0xFFFFE9B0))
    }
    drawOval(green, Offset(cx - r, cy - r * 0.95f), Size(r * 2f, r * 1.85f))
    // 머리 위 비늘
    for (i in -1..1) drawCircle(dark, r * 0.08f, Offset(cx + i * r * 0.22f, cy - r * 0.82f))
    // 주둥이
    drawOval(Color(0xFFD9F7DF), Offset(cx - r * 0.55f, cy + r * 0.02f), Size(r * 1.1f, r * 0.72f))
    drawEyes(cx, cy - r * 0.18f, r * 0.4f, r * 0.15f, closed, happy)
    drawCircle(Palette.blush, r * 0.15f, Offset(cx - r * 0.72f, cy + r * 0.18f))
    drawCircle(Palette.blush, r * 0.15f, Offset(cx + r * 0.72f, cy + r * 0.18f))
    // 콧구멍
    drawCircle(dark, r * 0.05f, Offset(cx - r * 0.13f, cy + r * 0.15f))
    drawCircle(dark, r * 0.05f, Offset(cx + r * 0.13f, cy + r * 0.15f))
    drawMouth(cx, cy + r * 0.38f, r * 0.15f)
    // 작은 송곳니
    drawCircle(Color.White, r * 0.045f, Offset(cx - r * 0.09f, cy + r * 0.45f))
}

/** 🐰 연승 토끼: 긴 귀 + 연승 수만큼 리본 (최대 5) */
private fun DrawScope.drawRabbit(closed: Boolean, happy: Boolean, ribbons: Int) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.64f
    val r = w * 0.33f
    val fur = Color(0xFFFFFBFD)
    // 긴 귀
    for (s in listOf(-1f, 1f)) {
        drawOval(fur, Offset(cx + s * r * 0.42f - r * 0.22f, cy - r * 1.95f), Size(r * 0.44f, r * 1.4f))
        drawOval(Palette.hamInner, Offset(cx + s * r * 0.42f - r * 0.11f, cy - r * 1.8f), Size(r * 0.22f, r * 1.1f))
    }
    drawOval(fur, Offset(cx - r * 1.05f, cy - r * 0.9f), Size(r * 2.1f, r * 1.8f))
    drawOval(Color(0xFFF1E8F0), Offset(cx - r * 1.05f, cy - r * 0.9f), Size(r * 2.1f, r * 1.8f), style = Stroke(r * 0.04f))
    drawEyes(cx, cy - r * 0.1f, r * 0.42f, r * 0.15f, closed, happy)
    drawCircle(Palette.blush, r * 0.17f, Offset(cx - r * 0.7f, cy + r * 0.22f))
    drawCircle(Palette.blush, r * 0.17f, Offset(cx + r * 0.7f, cy + r * 0.22f))
    drawOval(Palette.nose, Offset(cx - r * 0.1f, cy + r * 0.1f), Size(r * 0.2f, r * 0.14f))
    drawMouth(cx, cy + r * 0.3f, r * 0.15f)
    // 리본: 오른쪽 귀 → 왼쪽 귀 → 머리 순서로 하나씩
    val spots = listOf(
        Offset(cx + r * 0.42f, cy - r * 1.05f), Offset(cx - r * 0.42f, cy - r * 1.05f),
        Offset(cx + r * 0.42f, cy - r * 1.55f), Offset(cx - r * 0.42f, cy - r * 1.55f),
        Offset(cx, cy - r * 0.82f),
    )
    val colors = listOf(Color(0xFFFF5C8A), Color(0xFF7C6CF2), Color(0xFFFFC94D), Color(0xFF5CD6B4), Color(0xFFFF8A3D))
    // 5연승: 리본이 모두 금빛으로 빛남
    val golden = ribbons >= spots.size
    for (i in 0 until ribbons.coerceIn(0, spots.size)) {
        if (golden) {
            drawCircle(Brush.radialGradient(listOf(Color(0xAAFFE27A), Color.Transparent), center = spots[i], radius = r * 0.45f), r * 0.45f, spots[i])
            bow(spots[i], r * 0.22f, Color(0xFFFFC107))
        } else {
            bow(spots[i], r * 0.2f, colors[i])
        }
    }
}

/** 작은 리본 매듭 */
private fun DrawScope.bow(c: Offset, r: Float, color: Color) {
    for (s in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + s * r * 1.2f, c.y - r * 0.7f)
            lineTo(c.x + s * r * 1.2f, c.y + r * 0.7f)
            close()
        }
        drawPath(wing, color)
    }
    drawCircle(color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f), r * 0.35f, c)
    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.12f, c + Offset(-r * 0.7f, -r * 0.25f))
}

/** 🦄 유니콘: 하얀 얼굴 + 금빛 뿔 + 무지개 갈기 */
private fun DrawScope.drawUnicorn(closed: Boolean, happy: Boolean) {
    val w = size.width
    val cx = w / 2
    val cy = size.height * 0.6f
    val r = w * 0.34f
    val rainbow = listOf(Color(0xFFFF7EB3), Color(0xFFFFB86B), Color(0xFFFFE066), Color(0xFF7DDB9B), Color(0xFF7FC8FF), Color(0xFFB98CFF))
    // 무지개 갈기 (왼쪽 뒤로)
    rainbow.forEachIndexed { i, c ->
        drawCircle(c, r * 0.3f, Offset(cx - r * 0.98f - (i % 2) * r * 0.1f, cy - r * 0.8f + i * r * 0.27f))
    }
    // 귀
    for (s in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(cx + s * r * 0.35f, cy - r * 0.75f)
            lineTo(cx + s * r * 0.6f, cy - r * 1.3f)
            lineTo(cx + s * r * 0.82f, cy - r * 0.6f)
            close()
        }
        drawPath(ear, Color.White)
    }
    // 금빛 뿔 (줄무늬)
    val horn = Path().apply {
        moveTo(cx - r * 0.16f, cy - r * 0.85f)
        lineTo(cx, cy - r * 1.75f)
        lineTo(cx + r * 0.16f, cy - r * 0.85f)
        close()
    }
    drawPath(horn, Brush.verticalGradient(listOf(Color(0xFFFFF4B0), Color(0xFFFFC21A)), cy - r * 1.75f, cy - r * 0.85f))
    for (k in 1..3) {
        val y = cy - r * 0.85f - k * r * 0.22f
        val hw = r * 0.16f * (1f - k * 0.24f)
        drawLine(Color(0xFFE59A00), Offset(cx - hw, y + r * 0.05f), Offset(cx + hw, y - r * 0.05f), r * 0.04f)
    }
    drawOval(Color.White, Offset(cx - r, cy - r * 0.9f), Size(r * 2f, r * 1.85f))
    drawOval(Color(0xFFEDE7F6), Offset(cx - r, cy - r * 0.9f), Size(r * 2f, r * 1.85f), style = Stroke(r * 0.04f))
    // 앞머리 (무지개 한 줌)
    drawCircle(rainbow[0], r * 0.2f, Offset(cx - r * 0.25f, cy - r * 0.78f))
    drawCircle(rainbow[5], r * 0.16f, Offset(cx + r * 0.05f, cy - r * 0.82f))
    drawEyes(cx, cy - r * 0.1f, r * 0.4f, r * 0.15f, closed, happy)
    drawCircle(Palette.blush, r * 0.17f, Offset(cx - r * 0.68f, cy + r * 0.22f))
    drawCircle(Palette.blush, r * 0.17f, Offset(cx + r * 0.68f, cy + r * 0.22f))
    drawCircle(Color(0xFFFFB3D1), r * 0.05f, Offset(cx - r * 0.12f, cy + r * 0.2f))
    drawCircle(Color(0xFFFFB3D1), r * 0.05f, Offset(cx + r * 0.12f, cy + r * 0.2f))
    drawMouth(cx, cy + r * 0.35f, r * 0.14f)
}
