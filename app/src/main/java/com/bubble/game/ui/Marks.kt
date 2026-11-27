package com.bubble.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.bubble.game.game.Animal
import com.bubble.game.game.Side
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 공격받았지만 안 터진 상대 방울에 1초 남짓 남는 캐릭터 흔적 (모습만, 승패에는 영향 없음).
 * 눈송이 얼음 · 황금 코팅 · 핑크 하트 · 까망 발톱 · 복냥이 福 도장 · 문어 먹물 · 펭귄 눈 ·
 * 판다 발도장 · 아기 용 불꽃 · 토끼 발자국 · 유니콘 별가루 · 기본 고양이 발도장 · 기본 햄스터 해바라기씨
 */
enum class MarkKind { ICE, GOLD, HEART, CLAW, STAMP, INK, SNOW, BAMBOO, FIRE, HOP, MAGIC, PAW, SEED }

fun markKind(skin: Animal?, side: Side): MarkKind = when (skin) {
    Animal.SNOW_HAMSTER -> MarkKind.ICE
    Animal.GOLD_HAMSTER -> MarkKind.GOLD
    Animal.PINK_CAT -> MarkKind.HEART
    Animal.BLACK_CAT -> MarkKind.CLAW
    Animal.LUCKY_CAT -> MarkKind.STAMP
    Animal.OCTOPUS -> MarkKind.INK
    Animal.PENGUIN -> MarkKind.SNOW
    Animal.PANDA -> MarkKind.BAMBOO
    Animal.DRAGON -> MarkKind.FIRE
    Animal.RABBIT -> MarkKind.HOP
    Animal.UNICORN -> MarkKind.MAGIC
    Animal.HAMSTER -> MarkKind.SEED
    else -> if (com.bubble.game.game.SideLook.of(side) == Side.CAT) MarkKind.PAW else MarkKind.SEED
}

data class HitMark(val key: Long, val at: Offset, val kind: MarkKind)

const val HIT_MARK_MS = 1_400

@Composable
fun HitMarkFx(m: HitMark, onDone: (HitMark) -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(m.key) {
        p.animateTo(1f, tween(HIT_MARK_MS, easing = LinearEasing))
        onDone(m)
    }
    val seeds = remember(m.key) { val r = Random(m.key); List(14) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        val r = 62.dp.toPx()
        drawMark(m.kind, m.at, r, p.value, seeds)
    }
}

/** 0→0.15 나타남, 끝 0.25 동안 사라짐 */
private fun fadeOf(p: Float, inEnd: Float = 0.15f, outStart: Float = 0.75f) =
    (p / inEnd).coerceIn(0f, 1f) * (1f - ((p - outStart) / (1f - outStart)).coerceIn(0f, 1f))

private fun DrawScope.drawMark(kind: MarkKind, c: Offset, r: Float, p: Float, s: List<Triple<Float, Float, Float>>) {
    val a = fadeOf(p)
    val tau = (2 * PI).toFloat()
    when (kind) {
        // ❄️ 얼음이 덮였다가 쨍! 깨져 조각이 튐
        MarkKind.ICE -> {
            if (p < 0.72f) {
                drawCircle(Brush.radialGradient(listOf(Color(0x99E6F7FF), Color(0xCCAEE2FF), Color(0x99FFFFFF)), c, r), r * (0.7f + 0.3f * (p / 0.15f).coerceAtMost(1f)), c, alpha = a)
                repeat(6) { i ->
                    val ang = i * tau / 6 + 0.3f
                    val e = Offset(c.x + cos(ang) * r * 0.85f, c.y + sin(ang) * r * 0.85f)
                    drawLine(Color.White.copy(alpha = 0.9f * a), c, e, 3.dp.toPx(), StrokeCap.Round)
                    val mid = Offset(c.x + cos(ang) * r * 0.5f, c.y + sin(ang) * r * 0.5f)
                    for (d in listOf(-0.6f, 0.6f)) drawLine(Color.White.copy(alpha = 0.8f * a), mid, Offset(mid.x + cos(ang + d) * r * 0.22f, mid.y + sin(ang + d) * r * 0.22f), 2.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(Color.White, r * 0.95f, c, alpha = 0.7f * a, style = Stroke(3.dp.toPx()))
            } else {
                val q = (p - 0.72f) / 0.28f
                s.take(10).forEachIndexed { i, (u, v, w) ->
                    val ang = u * tau
                    val d = r * (0.3f + q * (0.9f + v))
                    val pos = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d + q * q * r * 0.6f)
                    val sz = (5f + w * 8f) * density
                    rotate(q * 360f * (if (i % 2 == 0) 1 else -1), pos) {
                        drawRect(Color(0xFFCDEFFF).copy(alpha = 1f - q), Offset(pos.x - sz / 2, pos.y - sz / 2), Size(sz, sz * 0.7f))
                    }
                }
            }
        }
        // 🏆 금빛 코팅 + 빛 줄기 + 반짝
        MarkKind.GOLD -> {
            drawCircle(Brush.radialGradient(listOf(Color(0x88FFF3B0), Color(0xAAFFC107), Color(0x55FFB300)), c, r), r, c, alpha = a)
            val x = c.x - r + p * 2.4f * r
            drawLine(Color.White.copy(alpha = 0.7f * a), Offset(x - r * 0.4f, c.y + r * 0.7f), Offset(x + r * 0.2f, c.y - r * 0.8f), 10.dp.toPx(), StrokeCap.Round)
            s.take(6).forEach { (u, v, w) -> star(Offset(c.x + (u - 0.5f) * r * 1.8f, c.y + (v - 0.5f) * r * 1.8f), (4f + 6f * sin((p * 3 + w) * PI.toFloat()).coerceAtLeast(0f)) * density, Color(0xFFFFF59D).copy(alpha = a)) }
        }
        // 🌸 볼 빨개지고 하트가 뿅뿅
        MarkKind.HEART -> {
            for (sx in listOf(-1f, 1f)) drawOval(Color(0xFFFF7EB3).copy(alpha = 0.45f * a), Offset(c.x + sx * r * 0.45f - r * 0.18f, c.y + r * 0.1f), Size(r * 0.36f, r * 0.2f))
            s.take(5).forEachIndexed { i, (u, _, w) ->
                val q = ((p - i * 0.08f) / 0.7f).coerceIn(0f, 1f)
                if (q > 0f) heartAt(Offset(c.x + (u - 0.5f) * r * 1.2f + sin(q * 6f + w * 6f) * 8f * density, c.y - q * r * 1.3f), (9f + w * 6f) * density, Color(0xFFFF5FA2).copy(alpha = (1f - q) * a))
            }
        }
        // 🐈‍⬛ 발톱 자국 세 줄
        MarkKind.CLAW -> {
            val draw = (p / 0.25f).coerceIn(0f, 1f)
            repeat(3) { i ->
                val off = (i - 1) * r * 0.32f
                val st = Offset(c.x - r * 0.4f + off, c.y - r * 0.6f)
                val en = Offset(c.x + r * 0.4f + off, c.y + r * 0.6f)
                val e = Offset(st.x + (en.x - st.x) * draw, st.y + (en.y - st.y) * draw)
                drawLine(Color(0xFF3A2A4F).copy(alpha = 0.85f * a), st, e, 7.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFB39DDB).copy(alpha = 0.7f * a), Offset(st.x + 3 * density, st.y), Offset(e.x + 3 * density, e.y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        // 💰 빨간 福 도장 쾅
        MarkKind.STAMP -> {
            val slam = if (p < 0.15f) 1.8f - 0.8f * (p / 0.15f) else 1f
            val sz = r * 0.95f * slam
            val al = (p / 0.1f).coerceIn(0f, 1f) * (1f - ((p - 0.75f) / 0.25f).coerceIn(0f, 1f))
            rotate(-12f, c) {
                drawRoundRect(Color(0xFFE53935).copy(alpha = 0.85f * al), Offset(c.x - sz / 2, c.y - sz / 2), Size(sz, sz), androidx.compose.ui.geometry.CornerRadius(sz * 0.18f))
                drawRoundRect(Color.White.copy(alpha = 0.8f * al), Offset(c.x - sz * 0.42f, c.y - sz * 0.42f), Size(sz * 0.84f, sz * 0.84f), androidx.compose.ui.geometry.CornerRadius(sz * 0.14f), style = Stroke(2.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText("福", c.x, c.y + sz * 0.22f, Paint().apply {
                    color = android.graphics.Color.argb((255 * al).toInt(), 255, 255, 255)
                    textSize = sz * 0.62f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
                })
            }
            if (p in 0.15f..0.4f) drawCircle(Color(0xFFE53935).copy(alpha = 0.5f * (1f - (p - 0.15f) / 0.25f)), r * (0.6f + (p - 0.15f) * 3f), c, style = Stroke(4.dp.toPx()))
        }
        // 🐙 먹물 얼룩 철퍽
        MarkKind.INK -> {
            val ink = Color(0xFF3B2557)
            drawCircle(ink.copy(alpha = 0.75f * a), r * 0.45f * (p / 0.12f).coerceAtMost(1f), c)
            s.take(9).forEach { (u, v, w) ->
                val ang = u * tau
                val d = r * (0.35f + v * 0.55f) * (p / 0.15f).coerceAtMost(1f)
                drawCircle(ink.copy(alpha = 0.7f * a), (5f + w * 10f) * density, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d + p * w * r * 0.4f))
            }
            drawCircle(Color.White.copy(alpha = 0.35f * a), r * 0.12f, Offset(c.x - r * 0.15f, c.y - r * 0.15f))
        }
        // 🐧 눈이 소복이 쌓였다가 털림
        MarkKind.SNOW -> {
            val fall = p > 0.7f
            val q = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
            if (!fall) {
                val grow = (p / 0.3f).coerceIn(0f, 1f)
                // 방울 윗부분에 눈덩이가 몽글몽글 쌓임
                for (k in -3..3) {
                    val ang = (-90f + k * 22f) * PI.toFloat() / 180f
                    val pos = Offset(c.x + cos(ang) * r * 0.82f, c.y + sin(ang) * r * 0.82f)
                    drawCircle(Color.White.copy(alpha = 0.97f), r * (0.2f + 0.05f * (3 - kotlin.math.abs(k)) / 3f) * grow, pos)
                }
                drawCircle(Color(0xFFDDEFFF).copy(alpha = 0.6f * grow), r * 0.12f, Offset(c.x - r * 0.2f, c.y - r * 0.85f))
                s.take(8).forEach { (u, v, _) -> drawCircle(Color.White.copy(alpha = 0.9f), 4f * density, Offset(c.x + (u - 0.5f) * r * 1.6f, c.y - r * 1.4f + (p * 2f + v) % 1f * r * 1.1f)) }
            } else {
                s.take(12).forEach { (u, v, w) -> drawCircle(Color.White.copy(alpha = 1f - q), (3f + w * 4f) * density, Offset(c.x + (u - 0.5f) * r * 1.6f + (u - 0.5f) * q * r, c.y - r * 0.7f + q * (0.5f + v) * r * 1.6f)) }
            }
        }
        // 🐼 판다 발도장 + 대나무 잎
        MarkKind.BAMBOO -> {
            paw(Offset(c.x, c.y + r * 0.1f), r * 0.55f * (if (p < 0.12f) 1.4f - 0.4f * p / 0.12f else 1f), Color(0xFF2B2B2B).copy(alpha = 0.8f * a))
            s.take(3).forEachIndexed { i, (u, v, _) ->
                val pos = Offset(c.x + (u - 0.5f) * r * 1.3f, c.y - r * 0.55f + v * r * 0.3f)
                rotate(-30f + i * 35f, pos) { drawOval(Color(0xFF66BB6A).copy(alpha = a), Offset(pos.x - r * 0.22f, pos.y - r * 0.08f), Size(r * 0.44f, r * 0.16f)) }
            }
        }
        // 🐉 불꽃 화르륵 + 그을림 + 연기
        MarkKind.FIRE -> {
            drawCircle(Brush.radialGradient(listOf(Color(0x55FFC107), Color(0x22FF5722), Color.Transparent), Offset(c.x, c.y + r * 0.4f), r * 1.1f), r * 1.1f, c, alpha = a)
            drawCircle(Color(0xFF3E2723).copy(alpha = 0.15f * a), r * 0.85f, Offset(c.x, c.y + r * 0.1f))
            s.take(7).forEachIndexed { i, (u, v, w) ->
                val fl = 0.75f + 0.25f * sin((p * 18f + w * 10f))
                val base = Offset(c.x + (u - 0.5f) * r * 1.3f, c.y + r * 0.6f - kotlin.math.abs(u - 0.5f) * r * 0.4f)
                flame(base, r * (0.5f + v * 0.35f) * fl * (1f - p * 0.5f), a)
            }
            s.drop(7).take(5).forEach { (u, v, w) ->
                val q = (p * 1.2f + v) % 1f
                drawCircle(Color(0xFF9E9E9E).copy(alpha = 0.45f * (1f - q) * a), (8f + q * 16f) * density, Offset(c.x + (u - 0.5f) * r + sin(q * 5f + w) * 10f, c.y - q * r * 1.5f))
            }
        }
        // 🐰 발자국 콩콩 (세 번)
        MarkKind.HOP -> {
            repeat(3) { i ->
                val t0 = i * 0.18f
                if (p >= t0) {
                    val pos = Offset(c.x - r * 0.5f + i * r * 0.5f, c.y + r * 0.35f - i * r * 0.35f)
                    val pop = if (p < t0 + 0.08f) 1.3f - 0.3f * (p - t0) / 0.08f else 1f
                    for (sx in listOf(-1f, 1f)) drawOval(Color(0xFFF48FB1).copy(alpha = 0.85f * a), Offset(pos.x + sx * r * 0.1f - r * 0.07f * pop, pos.y - r * 0.16f * pop), Size(r * 0.14f * pop, r * 0.3f * pop))
                }
            }
        }
        // 🦄 무지개 고리 + 별가루
        MarkKind.MAGIC -> {
            val ring = List(7) { i -> Color.hsv((i * 50f + p * 360f) % 360f, 0.55f, 1f) }
            drawCircle(Brush.sweepGradient(ring, c), r * 0.95f, c, alpha = a, style = Stroke(6.dp.toPx()))
            s.take(10).forEachIndexed { i, (u, v, w) ->
                val ang = u * tau + p * 4f
                val d = r * (0.6f + v * 0.5f)
                star(Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), (3f + w * 5f) * density, Color.hsv((i * 36f) % 360f, 0.35f, 1f).copy(alpha = a))
            }
        }
        // 🐱 분홍 발바닥 도장
        MarkKind.PAW -> paw(Offset(c.x, c.y + r * 0.1f), r * 0.5f * (if (p < 0.12f) 1.4f - 0.4f * p / 0.12f else 1f), Color(0xFFFF8FB8).copy(alpha = 0.85f * a))
        // 🐹 해바라기씨 톡톡
        MarkKind.SEED -> s.take(5).forEachIndexed { i, (u, v, _) ->
            val q = ((p - i * 0.05f) / 0.5f).coerceIn(0f, 1f)
            val pos = Offset(c.x + (u - 0.5f) * r * 1.2f, c.y - r * 0.2f + (v - 0.5f) * r * 0.8f - sin(q * PI.toFloat()) * r * 0.3f)
            rotate(u * 180f, pos) {
                drawOval(Color(0xFF4E342E).copy(alpha = a), Offset(pos.x - r * 0.1f, pos.y - r * 0.17f), Size(r * 0.2f, r * 0.34f))
                drawLine(Color(0xFFEFEBE9).copy(alpha = a), Offset(pos.x, pos.y - r * 0.12f), Offset(pos.x, pos.y + r * 0.12f), 2.dp.toPx())
            }
        }
    }
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 1.6f)
        quadraticTo(c.x, c.y, c.x + r * 1.6f, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r * 1.6f)
        quadraticTo(c.x, c.y, c.x - r * 1.6f, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r * 1.6f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.heartAt(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.3f, c.x, c.y - r * 0.45f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(p, color)
}

/** 발바닥: 큰 패드 + 발가락 넷 */
private fun DrawScope.paw(c: Offset, r: Float, color: Color) {
    drawOval(color, Offset(c.x - r * 0.45f, c.y - r * 0.1f), Size(r * 0.9f, r * 0.7f))
    val toes = listOf(Offset(-0.55f, -0.35f), Offset(-0.2f, -0.65f), Offset(0.2f, -0.65f), Offset(0.55f, -0.35f))
    toes.forEach { t -> drawOval(color, Offset(c.x + t.x * r - r * 0.15f, c.y + t.y * r - r * 0.18f), Size(r * 0.3f, r * 0.36f)) }
}

private fun DrawScope.flame(base: Offset, h: Float, a: Float) {
    val outer = Path().apply {
        moveTo(base.x, base.y - h)
        cubicTo(base.x + h * 0.5f, base.y - h * 0.5f, base.x + h * 0.4f, base.y, base.x, base.y)
        cubicTo(base.x - h * 0.4f, base.y, base.x - h * 0.5f, base.y - h * 0.5f, base.x, base.y - h)
        close()
    }
    drawPath(outer, Color(0xFFFF7043).copy(alpha = 0.9f * a))
    val inner = Path().apply {
        moveTo(base.x, base.y - h * 0.6f)
        cubicTo(base.x + h * 0.25f, base.y - h * 0.3f, base.x + h * 0.2f, base.y, base.x, base.y)
        cubicTo(base.x - h * 0.2f, base.y, base.x - h * 0.25f, base.y - h * 0.3f, base.x, base.y - h * 0.6f)
        close()
    }
    drawPath(inner, Color(0xFFFFEB3B).copy(alpha = 0.95f * a))
}
