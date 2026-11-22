package com.bubble.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.data.OnlineRepository
import com.bubble.game.game.Animal
import com.bubble.game.game.Side
import com.bubble.game.i18n.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ======================= 캐릭터별 이펙트 테마 =======================

/** 터질 때·날아갈 때 흩뿌리는 모양 */
enum class FxShape { STAR, HEART, PAW, SNOW, COIN, BONE, LEAF, CLOUD, MOON, SEED, INK, FLAME }

/** 캐릭터마다 다른 반짝이 색과 모양, 터질 때 붙는 이모지 */
data class FxTheme(val colors: List<Color>, val shapes: List<FxShape>, val emoji: String) {
    val main: Color get() = colors.first()
}

fun fxTheme(side: Side, skin: Animal?): FxTheme = when (skin ?: if (side == Side.CAT) null else Animal.HAMSTER) {
    null -> FxTheme(listOf(Color(0xFFFF9A3C), Color(0xFFFFC94D), Color.White, Color(0xFFFFD3A8)), listOf(FxShape.PAW, FxShape.STAR), "🐾")
    Animal.HAMSTER -> FxTheme(listOf(Color(0xFFB98CFF), Color(0xFFFF9EC7), Color.White, Color(0xFFFFE3A3)), listOf(FxShape.SEED, FxShape.HEART, FxShape.STAR), "🌻")
    Animal.PINK_CAT -> FxTheme(listOf(Color(0xFFFF4FA0), Color(0xFFFFA8D2), Color.White, Color(0xFFFFD6EC)), listOf(FxShape.HEART, FxShape.STAR), "💗")
    Animal.BLACK_CAT -> FxTheme(listOf(Color(0xFF7A4DFF), Color(0xFFFFD54A), Color.White, Color(0xFF3B2A6B)), listOf(FxShape.MOON, FxShape.STAR), "🌙")
    Animal.SNOW_HAMSTER -> FxTheme(listOf(Color(0xFF5CC8FF), Color(0xFFBFE9FF), Color.White, Color(0xFF9AB8FF)), listOf(FxShape.SNOW, FxShape.STAR), "❄️")
    Animal.GOLD_HAMSTER -> FxTheme(listOf(Color(0xFFFFB800), Color(0xFFFFE680), Color.White, Color(0xFFFF9F1C)), listOf(FxShape.COIN, FxShape.STAR), "✨")
    Animal.OCTOPUS -> FxTheme(listOf(Color(0xFF2E1F4D), Color(0xFFB57BEE), Color(0xFF8E52D6), Color.White), listOf(FxShape.INK, FxShape.STAR), "🐙")
    Animal.PENGUIN -> FxTheme(listOf(Color(0xFF7FD3FF), Color.White, Color(0xFF3A4A68), Color(0xFFBFE9FF)), listOf(FxShape.SNOW, FxShape.STAR), "🐧")
    Animal.PANDA -> FxTheme(listOf(Color(0xFF6CCB5F), Color.White, Color(0xFF3B3B45), Color(0xFFB8F0A8)), listOf(FxShape.LEAF, FxShape.HEART), "🐼")
    Animal.DRAGON -> FxTheme(listOf(Color(0xFFFF6A2B), Color(0xFFFFC21A), Color(0xFFFF2D55), Color.White), listOf(FxShape.FLAME, FxShape.STAR), "🔥")
    Animal.RABBIT -> FxTheme(listOf(Color(0xFFFF8FB8), Color.White, Color(0xFFFFC94D), Color(0xFF7C6CF2)), listOf(FxShape.HEART, FxShape.STAR), "🐰")
    Animal.UNICORN -> FxTheme(listOf(Color(0xFFFF7EB3), Color(0xFFFFE066), Color(0xFF7FC8FF), Color(0xFFB98CFF), Color(0xFF7DDB9B), Color.White), listOf(FxShape.STAR, FxShape.HEART), "🌈")
    Animal.LUCKY_CAT -> FxTheme(listOf(Color(0xFFFFC21A), Color(0xFFE5303F), Color.White, Color(0xFFFFE680)), listOf(FxShape.COIN, FxShape.STAR, FxShape.HEART), "💰")
    Animal.SHEEP -> FxTheme(listOf(Color(0xFF9EC9FF), Color.White, Color(0xFFFFC2DE), Color(0xFFE8F1FF)), listOf(FxShape.CLOUD, FxShape.STAR), "☁️")
    Animal.DOG -> FxTheme(listOf(Color(0xFFC98A4B), Color(0xFF7FD3FF), Color.White, Color(0xFFFFE0B0)), listOf(FxShape.BONE, FxShape.PAW, FxShape.STAR), "🦴")
    Animal.GIRAFFE -> FxTheme(listOf(Color(0xFFFFC53D), Color(0xFF6CCB5F), Color(0xFFD9893A), Color.White), listOf(FxShape.LEAF, FxShape.STAR), "🍃")
}

/** [shape]을 [c]에 반지름 [r]로 그림 */
fun DrawScope.fxShape(shape: FxShape, c: Offset, r: Float, color: Color, rot: Float = 0f) {
    if (r <= 0.3f || color.alpha <= 0.01f) return
    rotate(rot, pivot = c) {
        when (shape) {
            FxShape.STAR -> {
                sparkle(c, r * 1.25f, color)
                drawCircle(Color.White.copy(alpha = color.alpha * 0.9f), r * 0.22f, c)
            }
            FxShape.HEART -> {
                val p = Path().apply {
                    moveTo(c.x, c.y + r * 0.9f)
                    cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.3f, c.x, c.y - r * 0.45f)
                    cubicTo(c.x + r * 0.6f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
                    close()
                }
                drawPath(p, color)
                drawCircle(Color.White.copy(alpha = color.alpha * 0.7f), r * 0.2f, c + Offset(-r * 0.45f, -r * 0.35f))
            }
            FxShape.PAW -> {
                drawOval(color, Offset(c.x - r * 0.55f, c.y - r * 0.05f), Size(r * 1.1f, r * 0.9f))
                for ((dx, dy) in listOf(-0.62f to -0.3f, -0.22f to -0.72f, 0.22f to -0.72f, 0.62f to -0.3f)) {
                    drawCircle(color, r * 0.26f, c + Offset(dx * r, dy * r))
                }
            }
            FxShape.SNOW -> {
                val w = r * 0.22f
                for (k in 0 until 3) {
                    val a = k * PI / 3
                    val d = Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                    drawLine(color, c - d, c + d, w, StrokeCap.Round)
                    for (s in listOf(-1f, 1f)) {
                        val tip = c + d * (0.6f * s)
                        val b1 = Offset((cos(a + 0.7) * r * 0.32f).toFloat(), (sin(a + 0.7) * r * 0.32f).toFloat())
                        val b2 = Offset((cos(a - 0.7) * r * 0.32f).toFloat(), (sin(a - 0.7) * r * 0.32f).toFloat())
                        drawLine(color, tip, tip + b1 * s, w * 0.8f, StrokeCap.Round)
                        drawLine(color, tip, tip + b2 * s, w * 0.8f, StrokeCap.Round)
                    }
                }
            }
            FxShape.COIN -> {
                drawCircle(color, r, c)
                drawCircle(Color(0xFFE08A00).copy(alpha = color.alpha), r * 0.72f, c, style = Stroke(r * 0.16f))
                drawCircle(Color.White.copy(alpha = color.alpha * 0.8f), r * 0.22f, c + Offset(-r * 0.35f, -r * 0.35f))
            }
            FxShape.BONE -> {
                drawRoundRect(color, Offset(c.x - r * 0.7f, c.y - r * 0.2f), Size(r * 1.4f, r * 0.4f))
                for (sx in listOf(-0.75f, 0.75f)) for (sy in listOf(-0.22f, 0.22f)) {
                    drawCircle(color, r * 0.27f, c + Offset(sx * r, sy * r))
                }
            }
            FxShape.LEAF -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    quadraticBezierTo(c.x + r * 0.95f, c.y, c.x, c.y + r)
                    quadraticBezierTo(c.x - r * 0.95f, c.y, c.x, c.y - r)
                    close()
                }
                drawPath(p, color)
                drawLine(Color.White.copy(alpha = color.alpha * 0.6f), Offset(c.x, c.y - r * 0.8f), Offset(c.x, c.y + r * 0.8f), r * 0.1f)
            }
            FxShape.CLOUD -> {
                drawCircle(color, r * 0.5f, c + Offset(-r * 0.45f, r * 0.12f))
                drawCircle(color, r * 0.62f, c + Offset(0f, -r * 0.12f))
                drawCircle(color, r * 0.48f, c + Offset(r * 0.48f, r * 0.12f))
                drawRoundRect(color, Offset(c.x - r * 0.9f, c.y), Size(r * 1.8f, r * 0.6f), androidx.compose.ui.geometry.CornerRadius(r * 0.3f))
            }
            FxShape.MOON -> {
                val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(r * 0.45f, -r * 0.25f), r * 0.85f)) }
                drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, color)
            }
            FxShape.INK -> {
                // 먹물 방울: 큰 덩어리 + 튄 방울들
                drawCircle(color, r * 0.75f, c)
                drawCircle(color, r * 0.4f, c + Offset(r * 0.7f, -r * 0.35f))
                drawCircle(color, r * 0.32f, c + Offset(-r * 0.65f, r * 0.45f))
                drawCircle(color, r * 0.2f, c + Offset(r * 0.2f, r * 0.95f))
                drawCircle(Color.White.copy(alpha = color.alpha * 0.35f), r * 0.2f, c + Offset(-r * 0.25f, -r * 0.3f))
            }
            FxShape.FLAME -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - r * 1.1f)
                    cubicTo(c.x + r * 0.9f, c.y - r * 0.2f, c.x + r * 0.8f, c.y + r * 0.9f, c.x, c.y + r * 0.9f)
                    cubicTo(c.x - r * 0.8f, c.y + r * 0.9f, c.x - r * 0.9f, c.y - r * 0.2f, c.x, c.y - r * 1.1f)
                    close()
                }
                drawPath(p, color)
                drawOval(Color(0xFFFFF4B0).copy(alpha = color.alpha), Offset(c.x - r * 0.32f, c.y), Size(r * 0.64f, r * 0.8f))
            }
            FxShape.SEED -> {
                drawOval(color, Offset(c.x - r * 0.45f, c.y - r), Size(r * 0.9f, r * 2f))
                drawLine(Color.White.copy(alpha = color.alpha * 0.7f), Offset(c.x, c.y - r * 0.7f), Offset(c.x, c.y + r * 0.7f), r * 0.14f, StrokeCap.Round)
            }
        }
    }
}

/** 파티클 하나의 무작위 값 */
private data class Part(val a: Float, val s: Float, val spin: Float, val k: Int)

private fun parts(n: Int) = List(n) { Part(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2 - 1, Random.nextInt(1000)) }

/**
 * 화려한 폭발: 섬광 + 빛줄기 + 충격파 2겹 + 캐릭터 모양 파편 + 반짝이 가루.
 * [p]는 0 → 1 진행도, [scale]로 크기·양을 조절.
 */
private fun DrawScope.burst(c: Offset, radius: Float, p: Float, theme: FxTheme, ps: List<Part>, rays: Int = 12) {
    val fade = 1f - p
    // 섬광
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = fade * fade * 0.95f), theme.main.copy(alpha = fade * fade * 0.5f), Color.Transparent), c, radius * (0.5f + p * 0.9f)),
        radius * (0.5f + p * 0.9f), c,
    )
    // 빛줄기
    if (p < 0.6f) {
        val rp = p / 0.6f
        for (i in 0 until rays) {
            val ang = i * 2 * PI / rays + p * 0.6
            val d = Offset(cos(ang).toFloat(), sin(ang).toFloat())
            drawLine(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 1f - rp), theme.colors[i % theme.colors.size].copy(alpha = 0f)), c + d * radius * 0.2f, c + d * radius * (0.6f + rp * 1.1f)),
                c + d * radius * (0.2f + rp * 0.5f), c + d * radius * (0.6f + rp * 1.1f), (1f - rp) * radius * 0.09f, StrokeCap.Round,
            )
        }
    }
    // 충격파 2겹
    drawCircle(theme.main.copy(alpha = fade * 0.85f), radius * (0.5f + p * 1.0f), c, style = Stroke(fade * radius * 0.06f))
    val p2 = ((p - 0.12f) / 0.88f).coerceIn(0f, 1f)
    if (p2 > 0f) drawCircle(Color.White.copy(alpha = (1f - p2) * 0.9f), radius * (0.4f + p2 * 1.4f), c, style = Stroke((1f - p2) * radius * 0.04f))
    // 파편 (캐릭터 모양)
    ps.forEachIndexed { i, q ->
        val ang = (i / ps.size.toFloat() + q.a * 0.08f) * 2 * PI
        val ease = 1f - (1f - p) * (1f - p)
        val dist = radius * (0.25f + ease * (0.9f + q.s * 0.9f))
        val pos = c + Offset((cos(ang) * dist).toFloat(), (sin(ang) * dist).toFloat() + p * p * radius * 0.5f)
        val color = theme.colors[q.k % theme.colors.size]
        val shape = theme.shapes[q.k % theme.shapes.size]
        val sz = radius * (0.06f + q.s * 0.06f) * (1f - p * 0.6f)
        fxShape(shape, pos, sz, color.copy(alpha = (1f - p * p)), q.spin * 360f * p)
    }
    // 반짝이 가루 (깜빡임)
    for (i in 0 until ps.size) {
        val q = ps[(i * 7) % ps.size]
        val ang = (q.a + i * 0.13f) * 2 * PI
        val dist = radius * (0.3f + p * (0.5f + q.s * 1.3f))
        val pos = c + Offset((cos(ang) * dist).toFloat(), (sin(ang) * dist).toFloat())
        val tw = 0.5f + 0.5f * sin(p * 30f + i).toFloat()
        drawCircle(Color.White.copy(alpha = fade * tw), radius * 0.025f * (1f + q.s), pos)
    }
}

// ======================= 방울 안 효과 (터짐 · 선택) =======================

/** 방울이 터질 때 캐릭터별 화려한 효과. [p]가 1이면 안 보임. */
@Composable
fun PopBurst(side: Side, skin: Animal?, p: Float) {
    val theme = remember(side, skin) { fxTheme(side, skin) }
    val ps = remember { parts(30) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
            burst(center, size.minDimension / 2 * 1.15f, p, theme, ps, rays = 14)
        }
        val pop = if (p < 0.25f) 0.4f + p / 0.25f * 0.9f else 1.3f - (p - 0.25f) * 0.3f
        Text(
            theme.emoji + " " + tr("펑!", "POP!", zh = "啪！", hi = "फट!", es = "¡PUM!", fr = "POP !", ar = "بوب!", de = "PLOPP!", pt = "POP!", ru = "ХЛОП!", ja = "パン！"),
            fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White,
            style = TextStyle(shadow = Shadow(theme.main, Offset(0f, 0f), 18f)),
            modifier = Modifier.scale(pop).alpha(((1f - p) * 1.4f).coerceIn(0f, 1f)).graphicsLayer { rotationZ = -8f + p * 10f },
        )
    }
}

/** 고른 방울 주위를 빙글빙글 도는 반짝이 */
@Composable
fun SelectedSparkles(side: Side, skin: Animal?) {
    val theme = remember(side, skin) { fxTheme(side, skin) }
    val t = rememberInfiniteTransition(label = "sel")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "spin")
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2
        // 은은한 후광
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, Ui.gold.copy(alpha = 0.45f), Color.Transparent), center, r * 1.3f),
            r * 1.3f, center,
        )
        for (i in 0 until 8) {
            val ang = (spin + i / 8f) * 2 * PI
            val rr = r * (1.08f + 0.06f * sin(spin * 4 * PI + i).toFloat())
            val pos = center + Offset((cos(ang) * rr).toFloat(), (sin(ang) * rr).toFloat())
            val tw = 0.55f + 0.45f * sin(spin * 6 * PI + i * 1.7).toFloat()
            val color = if (i % 2 == 0) theme.colors[i / 2 % theme.colors.size] else Ui.gold
            if (i % 2 == 0) {
                fxShape(theme.shapes[i / 2 % theme.shapes.size], pos, r * 0.13f * tw, color, spin * 360f)
            } else {
                sparkle(pos, r * 0.16f * tw, color)
            }
        }
    }
}

// ======================= 날아가서 치는 방울 =======================

/** 화면에 날아다니는 방울 하나 (좌표는 [ShotLayer] 기준) */
data class FlyingShot(
    val key: Long,
    val from: Offset,
    val to: Offset,
    val side: Side,
    val skin: Animal?,
    val power: Int,
    /** 상대 방울을 침 (아니면 내 방울 키우기) */
    val attack: Boolean,
    /** 이 방울을 치면 터짐 */
    val boom: Boolean,
    /** 분할: 가위로 싹둑 자른 절반이 옆 방울로 날아감 */
    val split: Boolean = false,
)

const val SHOT_FLY_MS = 520L
private const val SHOT_IMPACT_MS = 750

/** 날아가는 방울들. [onImpact]는 부딪히는 순간, [onDone]은 효과가 끝났을 때. */
@Composable
fun ShotLayer(shots: List<FlyingShot>, onImpact: (FlyingShot) -> Unit, onDone: (FlyingShot) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        shots.forEach { s ->
            androidx.compose.runtime.key(s.key) { ShotFx(s, onImpact, onDone) }
        }
    }
}

@Composable
private fun ShotFx(s: FlyingShot, onImpact: (FlyingShot) -> Unit, onDone: (FlyingShot) -> Unit) {
    val theme = remember(s.side, s.skin) { fxTheme(s.side, s.skin) }
    val fly = remember { Animatable(0f) }
    val hit = remember { Animatable(0f) }
    val ps = remember { parts(14 + s.power * 3 + if (s.boom) 8 else 0) }
    val trail = remember { parts(22) }
    LaunchedEffect(Unit) {
        // 살짝 뒤로 당겼다가 쓩 날아감
        fly.animateTo(1f, tween(SHOT_FLY_MS.toInt(), easing = CubicBezierEasing(0.45f, -0.25f, 0.75f, 1f)))
        onImpact(s)
        hit.animateTo(1f, tween(SHOT_IMPACT_MS, easing = FastOutSlowInEasing))
        onDone(s)
    }
    val d = s.to - s.from
    val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(1f)
    val perp = Offset(-d.y / len, d.x / len)
    val bend = if (s.from.x <= s.to.x) 1f else -1f
    val ctrl = (s.from + s.to) / 2f + perp * (len * 0.28f * bend)
    fun at(t: Float): Offset {
        val u = 1f - t
        return s.from * (u * u) + ctrl * (2 * u * t) + s.to * (t * t)
    }
    val f = fly.value.coerceIn(0f, 1f)
    val head = at(f)
    val density = LocalDensity.current
    val orbPx = with(density) { (34 + s.power * 4).dp.toPx() }

    Canvas(Modifier.fillMaxSize()) {
        // ✂️ 분할: 원래 방울을 대각선으로 싹둑 (빛나는 칼자국 + 반짝이)
        if (s.split) {
            val sp = (fly.value / 0.55f).coerceIn(0f, 1f)
            val slashT = (fly.value / 0.3f).coerceIn(0f, 1f)
            val fadeS = if (fly.value < 0.55f) 1f else (1f - (fly.value - 0.55f) / 0.45f).coerceIn(0f, 1f) * (if (hit.value > 0f) 0f else 1f)
            val len = orbPx * 2.2f
            val dir = Offset(0.75f, -0.66f)
            val a0 = s.from - dir * len
            val a1 = a0 + dir * (len * 2 * slashT)
            drawLine(theme.main.copy(alpha = 0.55f * fadeS), a0, a1, orbPx * 0.45f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = fadeS), a0, a1, orbPx * 0.16f, StrokeCap.Round)
            // 칼끝 반짝
            sparkle(a1, orbPx * 0.55f * fadeS, Color.White.copy(alpha = fadeS))
            if (sp > 0.3f) burst(s.from, orbPx * 1.6f, ((sp - 0.3f) / 0.7f).coerceIn(0f, 1f), theme, ps, rays = 10)
        }
        if (hit.value == 0f) {
            // 꼬리: 반짝이 + 캐릭터 모양
            trail.forEachIndexed { k, q ->
                val tk = f - (k + 1) * 0.028f
                if (tk <= 0f) return@forEachIndexed
                val fade = 1f - k / trail.size.toFloat()
                val wob = perp * (sin((tk * 22f + q.a * 6f).toDouble()).toFloat() * orbPx * 0.35f * q.s)
                val pos = at(tk) + wob
                drawCircle(theme.main.copy(alpha = fade * 0.25f), orbPx * 0.5f * fade, at(tk))
                if (k % 2 == 0) {
                    fxShape(theme.shapes[q.k % theme.shapes.size], pos, orbPx * 0.22f * fade, theme.colors[q.k % theme.colors.size].copy(alpha = fade), q.spin * 300f * f)
                } else {
                    sparkle(pos, orbPx * 0.2f * fade, Color.White.copy(alpha = fade))
                }
            }
            // 빛나는 방울
            drawCircle(
                Brush.radialGradient(listOf(Color.White, theme.main.copy(alpha = 0.7f), theme.main.copy(alpha = 0f)), head, orbPx * 1.1f),
                orbPx * 1.1f, head,
            )
            drawCircle(Color.White.copy(alpha = 0.95f), orbPx * 0.62f, head, style = Stroke(orbPx * 0.07f))
            drawCircle(Color.White.copy(alpha = 0.9f), orbPx * 0.12f, head + Offset(-orbPx * 0.28f, -orbPx * 0.3f))
        } else {
            burst(s.to, orbPx * (1.5f + s.power * 0.08f) * (if (s.boom) 1.2f else 1f), hit.value, theme, ps, rays = if (s.boom) 18 else 12)
        }
    }
    // ✂️ 가위가 휙 지나감
    if (s.split && hit.value == 0f && fly.value < 0.6f) {
        val k = (fly.value / 0.6f).coerceIn(0f, 1f)
        val scPx = with(density) { 30.dp.toPx() }
        val travel = orbPx * 2.2f
        Text(
            "✂️", fontSize = 44.sp,
            modifier = Modifier
                .offset { IntOffset((s.from.x - travel * 0.75f + travel * 1.5f * k).roundToInt(), (s.from.y + travel * 0.66f - travel * 1.32f * k - scPx).roundToInt()) }
                .graphicsLayer { translationX = -size.width / 2; rotationZ = -40f + 30f * sin(k * 12f) }
                .scale(0.6f + 0.8f * sin(k * PI.toFloat()))
                .alpha(if (k < 0.8f) 1f else (1f - k) / 0.2f),
        )
    }
    // 방울 안의 캐릭터 (빙글빙글)
    if (hit.value == 0f) {
        val sizeDp = with(density) { (orbPx * 1.0f).toDp() }
        Critter(
            side = s.side, skin = s.skin, happy = true,
            modifier = Modifier
                .offset { IntOffset((head.x - orbPx / 2).roundToInt(), (head.y - orbPx / 2).roundToInt()) }
                .size(sizeDp)
                .graphicsLayer { rotationZ = f * 360f * (if (s.attack) 1f else -1f); scaleX = 0.8f + f * 0.3f; scaleY = 0.8f + f * 0.3f },
        )
    } else {
        // 부딪힌 자리에 +N
        val h = hit.value
        // 터지면 방울 쪽에서 "펑!"을 띄우니 여기선 더한 수만
        val label = if (s.split) "✂️ +${s.power}" else "+${s.power}"
        val textPx = with(density) { 40.dp.toPx() }
        Text(
            label, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White,
            style = TextStyle(shadow = Shadow(theme.main, Offset.Zero, 16f)),
            modifier = Modifier
                .offset { IntOffset(s.to.x.roundToInt(), (s.to.y - textPx * 0.9f - h * textPx * 1.2f).roundToInt()) }
                .graphicsLayer { translationX = -size.width / 2; translationY = -size.height / 2 }
                .scale(if (h < 0.2f) 0.5f + h * 4f else 1.3f - (h - 0.2f) * 0.35f)
                .alpha(if (h < 0.7f) 1f else (1f - h) / 0.3f),
        )
    }
}

// ======================= 출석 완료 팝업 =======================

/** 출석 완료! 쏟아지는 색종이 · 회전하는 빛 · 도장 쾅 · 💰 숫자 올라가기 */
@Composable
fun CheckinCelebration(day: Int, coins: Int, potion: Boolean, panda: Int = 0, onDismiss: () -> Unit) {
    val t = rememberInfiniteTransition(label = "checkin")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "tw")

    val pop = remember { Animatable(0f) }
    val stamp = remember { Animatable(0f) }
    val count = remember { Animatable(0f) }
    val burstP = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(380)
        stamp.animateTo(1f, tween(320, easing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)))
        burstP.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(650)
        count.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val burstTheme = FxTheme(listOf(Ui.gold, Ui.primary, Color.White, Ui.accent), listOf(FxShape.STAR, FxShape.HEART, FxShape.COIN), "🎉")
    val burstParts = remember { parts(34) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xCC3A1F5C), Color(0xEE140B22))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        CelebrationBackdrop(pop.value)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .scale(0.6f + 0.4f * pop.value)
                .alpha(pop.value.coerceIn(0f, 1f))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFF7FB), Color(0xFFF1ECFF))), RoundedCornerShape(30.dp))
                .border(3.dp, Brush.sweepGradient(listOf(Ui.primary, Ui.gold, Ui.accent, Color(0xFF6EC1FF), Ui.primary)), RoundedCornerShape(30.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            // 도장 쾅!
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp).graphicsLayer { clip = false }) {
                    if (burstP.value > 0f && burstP.value < 1f) burst(center, size.minDimension / 2 * 0.75f, burstP.value, burstTheme, burstParts, rays = 16)
                    // 반짝반짝 둘레
                    for (i in 0 until 6) {
                        val ang = (twinkle + i / 6f) * 2 * PI
                        val rr = size.minDimension * 0.36f
                        val tw = 0.5f + 0.5f * sin(twinkle * 4 * PI + i * 1.3).toFloat()
                        sparkle(center + Offset((cos(ang) * rr).toFloat(), (sin(ang) * rr).toFloat()), 9.dp.toPx() * tw, listOf(Ui.gold, Ui.primary, Ui.accent)[i % 3])
                    }
                }
                val s = stamp.value
                Box(
                    Modifier
                        .size(108.dp)
                        .graphicsLayer {
                            val k = 2.6f - 1.6f * s
                            scaleX = k; scaleY = k
                            rotationZ = -14f * s
                        }
                        .alpha((s * 2f).coerceAtMost(1f))
                        .background(Brush.radialGradient(listOf(Color(0xFFFF7EB3), Color(0xFFE0518F))), CircleShape)
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("${day}일째", "DAY $day", zh = "第${day}天", es = "DÍA $day", fr = "JOUR $day", de = "TAG $day", pt = "DIA $day", ru = "ДЕНЬ $day", ja = "${day}日目"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("✔", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr("출석 완료!", "Checked in!", zh = "签到成功！", es = "¡Asistencia lista!", fr = "Présence validée !", de = "Eingecheckt!", pt = "Presença feita!", ru = "Вход отмечен!", ja = "出席完了！"),
                fontSize = 30.sp, fontWeight = FontWeight.Black,
                style = TextStyle(brush = Brush.horizontalGradient(listOf(Ui.primaryDark, Ui.accent)), shadow = Shadow(Ui.gold.copy(alpha = 0.6f), Offset(0f, 3f), 10f)),
            )
            Spacer(Modifier.height(14.dp))
            // 7일 진행
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (d in 1..7) {
                    val done = d <= day
                    val today = d == day
                    val todayScale = if (today) (stamp.value * 1.25f).coerceAtMost(1f + 0.25f * (1f - burstP.value)) else 1f
                    Box(
                        Modifier
                            .size(32.dp)
                            .scale(if (today) todayScale else 1f)
                            .background(
                                when {
                                    today -> Brush.radialGradient(listOf(Ui.gold, Color(0xFFFF9F1C)))
                                    done -> Brush.radialGradient(listOf(Ui.primary, Ui.primaryDark))
                                    else -> Brush.radialGradient(listOf(Color(0x22000000), Color(0x18000000)))
                                },
                                CircleShape,
                            )
                            .border(if (today) 2.dp else 0.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (done) "✓" else if (d == 7) "💖" else "$d",
                            color = if (done) Color.White else Ui.text.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Black, fontSize = if (done) 16.sp else 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // 보상
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RewardChip("💰", "+${(coins * count.value).roundToInt()}", Ui.gold)
                if (potion) RewardChip("💖", "+1", Color(0xFFFF5C8A), Modifier.scale(0.6f + 0.4f * count.value))
            }
            // 🐼 출석 판다 보너스 (자리가 모자라지 않게 아랫줄에)
            if (panda > 0) {
                Spacer(Modifier.height(8.dp))
                RewardChip("🐼", tr("판다 보너스 💰 +$panda", "Panda bonus 💰 +$panda", zh = "熊猫奖励 💰 +$panda", es = "Bono panda 💰 +$panda",
                    fr = "Bonus panda 💰 +$panda", de = "Panda-Bonus 💰 +$panda", pt = "Bônus panda 💰 +$panda", ru = "Бонус панды 💰 +$panda",
                    ja = "パンダボーナス 💰 +$panda"), Color(0xFF6CCB5F), Modifier.scale(0.6f + 0.4f * count.value))
            }
            Spacer(Modifier.height(10.dp))
            val nextDay = day % 7 + 1
            Text(
                if (day == 7) tr("🎉 7일 개근! 내일은 1일째부터 다시 시작해요", "🎉 7-day streak! Tomorrow starts again at day 1", zh = "🎉 连续 7 天！明天从第 1 天重新开始", es = "🎉 ¡7 días seguidos! Mañana vuelves al día 1", fr = "🎉 7 jours d’affilée ! Demain, retour au jour 1", de = "🎉 7 Tage am Stück! Morgen geht’s bei Tag 1 weiter", pt = "🎉 7 dias seguidos! Amanhã recomeça no dia 1", ru = "🎉 7 дней подряд! Завтра снова с 1-го дня", ja = "🎉 7日皆勤！明日は1日目から")
                else tr("내일도 오면 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", "Come back tomorrow for 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", zh = "明天再来可得 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", es = "Vuelve mañana por 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", fr = "Reviens demain pour 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", de = "Morgen wieder: 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", pt = "Volte amanhã por 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", ru = "Завтра: 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}", ja = "明日も来ると 💰 +${OnlineRepository.CHECKIN_COINS[nextDay - 1]}"),
                fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)), RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("좋아요! 💕", "Yay! 💕", zh = "太好了！💕", es = "¡Genial! 💕", fr = "Super ! 💕", de = "Juhu! 💕", pt = "Oba! 💕", ru = "Ура! 💕", ja = "やった！💕"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun RewardChip(icon: String, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .border(2.dp, color.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.size(6.dp))
        Text(text, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ui.text)
    }
}

/** 축하 팝업 뒤: 회전하는 금빛 빛줄기 + 떨어지는 색종이 */
@Composable
private fun CelebrationBackdrop(shine: Float) {
    val t = rememberInfiniteTransition(label = "backdrop")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing)), label = "fall")
    val confetti = remember { List(70) { Part(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2 - 1, Random.nextInt(1000)) } }
    val confettiColors = listOf(Ui.primary, Ui.gold, Ui.accent, Color(0xFF5CD6B4), Color(0xFF6EC1FF), Color(0xFFFF8A65), Color.White)
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height * 0.42f)
        val big = size.maxDimension
        for (i in 0 until 16) {
            val a0 = (spin + i / 16f) * 2 * PI
            val a1 = a0 + PI / 22
            val path = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + (cos(a0) * big).toFloat(), c.y + (sin(a0) * big).toFloat())
                lineTo(c.x + (cos(a1) * big).toFloat(), c.y + (sin(a1) * big).toFloat())
                close()
            }
            drawPath(path, Brush.radialGradient(listOf(Ui.gold.copy(alpha = 0.32f * shine), Color.Transparent), c, big * 0.6f))
        }
        confetti.forEachIndexed { i, q ->
            val y = ((fall + q.a) % 1f) * (size.height + 80f) - 40f
            val x = q.s * size.width + sin((fall * 2 * PI * (1 + q.spin) + i).toDouble()).toFloat() * 30f
            val color = confettiColors[q.k % confettiColors.size]
            val rot = (fall * 720f * q.spin) + q.k
            val sz = 5.dp.toPx() + q.s * 4.dp.toPx()
            when (q.k % 3) {
                0 -> rotate(rot, Offset(x, y)) { drawRect(color, Offset(x - sz, y - sz * 0.45f), Size(sz * 2, sz * 0.9f)) }
                1 -> drawCircle(color, sz * 0.6f, Offset(x, y))
                else -> sparkle(Offset(x, y), sz, color)
            }
        }
    }
}

// ======================= 미션 완료 팝업 =======================

/** 미션 보상 받기! 과녁이 빙글 돌며 튀어나오고 팡 터짐 · 💰 숫자 올라가기 */
@Composable
fun MissionCelebration(label: String, coins: Int, onDismiss: () -> Unit) {
    val t = rememberInfiniteTransition(label = "mission")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "tw")
    val pop = remember { Animatable(0f) }
    val badge = remember { Animatable(0f) }
    val burstP = remember { Animatable(0f) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        badge.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(420)
        burstP.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        count.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val burstTheme = FxTheme(listOf(Ui.gold, Color(0xFF5CD6B4), Color.White, Ui.primary), listOf(FxShape.COIN, FxShape.STAR), "🎯")
    val burstParts = remember { parts(30) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xCC1F3A5C), Color(0xEE0B1622))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        CelebrationBackdrop(pop.value)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .scale(0.6f + 0.4f * pop.value)
                .alpha(pop.value.coerceIn(0f, 1f))
                .background(Brush.verticalGradient(listOf(Color(0xFFF4FFFB), Color(0xFFEFF3FF))), RoundedCornerShape(30.dp))
                .border(3.dp, Brush.sweepGradient(listOf(Color(0xFF5CD6B4), Ui.gold, Ui.accent, Color(0xFF6EC1FF), Color(0xFF5CD6B4))), RoundedCornerShape(30.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp).graphicsLayer { clip = false }) {
                    if (burstP.value > 0f && burstP.value < 1f) burst(center, size.minDimension / 2 * 0.75f, burstP.value, burstTheme, burstParts, rays = 16)
                    for (i in 0 until 6) {
                        val ang = (twinkle + i / 6f) * 2 * PI
                        val rr = size.minDimension * 0.36f
                        val tw = 0.5f + 0.5f * sin(twinkle * 4 * PI + i * 1.3).toFloat()
                        sparkle(center + Offset((cos(ang) * rr).toFloat(), (sin(ang) * rr).toFloat()), 9.dp.toPx() * tw, listOf(Ui.gold, Color(0xFF5CD6B4), Ui.accent)[i % 3])
                    }
                }
                Box(
                    Modifier
                        .size(108.dp)
                        .graphicsLayer {
                            scaleX = badge.value; scaleY = badge.value
                            rotationZ = (1f - badge.value) * -360f
                        }
                        .background(Brush.radialGradient(listOf(Color(0xFF7BE3C4), Color(0xFF2FB58F))), CircleShape)
                        .border(4.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎯", fontSize = 54.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr("미션 완료!", "Mission complete!", zh = "任务完成！", es = "¡Misión cumplida!", fr = "Mission réussie !", de = "Mission geschafft!", pt = "Missão cumprida!", ru = "Задание выполнено!", ja = "ミッション達成！"),
                fontSize = 30.sp, fontWeight = FontWeight.Black,
                style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF1F9E7A), Ui.accent)), shadow = Shadow(Ui.gold.copy(alpha = 0.6f), Offset(0f, 3f), 10f)),
            )
            Spacer(Modifier.height(6.dp))
            Text("✅ $label", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.8f))
            Spacer(Modifier.height(16.dp))
            RewardChip("💰", "+${(coins * count.value).roundToInt()}", Ui.gold, Modifier.scale(0.85f + 0.15f * count.value))
            Spacer(Modifier.height(10.dp))
            Text(
                tr("상점에서 쓸 수 있는 💰 포인트가 들어왔어요", "💰 shop points added — spend them in the shop", zh = "💰 商店积分已到账", es = "💰 Puntos de tienda añadidos", fr = "💰 Points boutique ajoutés", de = "💰 Shop-Punkte gutgeschrieben", pt = "💰 Pontos da loja adicionados", ru = "💰 Очки магазина начислены", ja = "💰 ショップポイントが入りました"),
                fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF3CC9A0), Ui.accent)), RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("좋아요! 💕", "Yay! 💕", zh = "太好了！💕", es = "¡Genial! 💕", fr = "Super ! 💕", de = "Juhu! 💕", pt = "Oba! 💕", ru = "Ура! 💕", ja = "やった！💕"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

// ======================= 💰 복냥이 동전 =======================

/** 복냥이 효과로 튀어나오는 동전 하나 (좌표는 [ShotLayer]와 같은 기준) */
data class CoinPop(val key: Long, val at: Offset, val mine: Boolean, val amount: Int = 1)

/** 금화 하나 (빙글 도는 정도 [spin]: 1이면 정면, 0에 가까우면 옆면) */
private fun DrawScope.goldCoin(c: Offset, r: Float, spin: Float, alpha: Float = 1f) {
    val w = r * spin.coerceIn(0.12f, 1f)
    // 옆면 두께
    drawOval(Color(0xFFC77800).copy(alpha = alpha), Offset(c.x - w + r * 0.08f, c.y - r + r * 0.06f), Size(w * 2, r * 2))
    drawOval(
        Brush.linearGradient(listOf(Color(0xFFFFF3A0), Color(0xFFFFC21A), Color(0xFFE59A00)), Offset(c.x - w, c.y - r), Offset(c.x + w, c.y + r)),
        Offset(c.x - w, c.y - r), Size(w * 2, r * 2), alpha = alpha,
    )
    drawOval(Color(0xFFFFF7C2).copy(alpha = alpha * 0.9f), Offset(c.x - w * 0.72f, c.y - r * 0.72f), Size(w * 1.44f, r * 1.44f), style = Stroke(r * 0.09f))
    // 가운데 ₩ 대신 반짝 별
    if (spin > 0.45f) sparkle(c, r * 0.42f * spin, Color(0xFFFFFBE6).copy(alpha = alpha))
}

/**
 * 💰 복냥이 동전 터짐: 금빛 햇살이 돌고, 큰 금화가 통 튀어 오르며 빙글빙글,
 * 작은 금화들이 분수처럼 흩어졌다 떨어지고, 반짝이 가루와 "💰 +1"이 반짝.
 */
@Composable
fun CoinPopFx(c: CoinPop, onDone: (CoinPop) -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1_700, easing = LinearEasing))
        onDone(c)
    }
    val theme = remember { FxTheme(listOf(Color(0xFFFFC21A), Color(0xFFFFE680), Color.White, Color(0xFFFF8A3D)), listOf(FxShape.STAR, FxShape.COIN), "💰") }
    val ps = remember { parts(22) }
    // 분수처럼 튀는 작은 금화들: (방향, 세기, 회전 속도)
    val fountain = remember { List(9) { i -> Triple(-PI / 2 + (i - 4) * 0.33 + (Random.nextDouble() - 0.5) * 0.2, 0.75f + Random.nextFloat() * 0.5f, 10f + Random.nextFloat() * 14f) } }
    val dust = remember { parts(26) }
    val density = LocalDensity.current
    val t = p.value
    val unit = with(density) { 1.dp.toPx() }
    val coinR = 20f * unit
    // 큰 금화: 위로 통 튀었다가 살짝 내려옴
    val upT = (t / 0.8f).coerceAtMost(1f)
    val headY = c.at.y - 110f * unit * (1f - (1f - upT) * (1f - upT)) + 20f * unit * (t * t)
    val head = Offset(c.at.x, headY)

    Canvas(Modifier.fillMaxSize()) {
        // 1) 맞은 자리 폭발 (금빛)
        if (t < 0.6f) burst(c.at, coinR * 3.4f, t / 0.6f, theme, ps, rays = 14)
        // 2) 큰 금화 뒤로 도는 금빛 햇살
        val glowA = (if (t < 0.15f) t / 0.15f else if (t > 0.75f) (1f - t) / 0.25f else 1f).coerceIn(0f, 1f)
        for (i in 0 until 12) {
            val a0 = t * 2.2 + i * 2 * PI / 12
            val a1 = a0 + PI / 18
            val len = coinR * (3.2f + 0.4f * sin(t * 20f + i))
            val ray = Path().apply {
                moveTo(head.x, head.y)
                lineTo(head.x + (cos(a0) * len).toFloat(), head.y + (sin(a0) * len).toFloat())
                lineTo(head.x + (cos(a1) * len).toFloat(), head.y + (sin(a1) * len).toFloat())
                close()
            }
            drawPath(ray, Brush.radialGradient(listOf(Color(0xFFFFE680).copy(alpha = 0.75f * glowA), Color.Transparent), head, len))
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8D0).copy(alpha = 0.95f * glowA), Color(0xFFFFC21A).copy(alpha = 0.35f * glowA), Color.Transparent), head, coinR * 2.6f), coinR * 2.6f, head)
        // 3) 분수 금화들 (중력으로 떨어지며 빙글빙글)
        fountain.forEachIndexed { i, (ang, power, spinSpeed) ->
            val ft = ((t - 0.05f) / 0.9f).coerceIn(0f, 1f)
            if (ft <= 0f) return@forEachIndexed
            val v = 210f * unit * power
            val x = c.at.x + (cos(ang) * v * ft).toFloat()
            val y = c.at.y + (sin(ang) * v * ft).toFloat() + 260f * unit * ft * ft
            val a = if (ft < 0.7f) 1f else (1f - ft) / 0.3f
            val spin = kotlin.math.abs(cos((ft * spinSpeed + i).toDouble())).toFloat()
            goldCoin(Offset(x, y), coinR * 0.45f, spin, a)
            if (i % 2 == 0) sparkle(Offset(x, y) - Offset(0f, coinR * 0.6f), coinR * 0.3f * a, Color.White.copy(alpha = a))
        }
        // 4) 반짝이 가루 (위로 날리며 깜빡)
        dust.forEachIndexed { i, q ->
            val dx = (q.a - 0.5f) * 180f * unit
            val dy = -(40f + q.s * 160f) * unit * t
            val tw = 0.5f + 0.5f * sin(t * 40f + i)
            val a = (1f - t) * tw
            val pos = c.at + Offset(dx * t, dy)
            if (i % 3 == 0) sparkle(pos, (4f + q.s * 5f) * unit * a, Color(0xFFFFE680).copy(alpha = a))
            else drawCircle(Color.White.copy(alpha = a), (1.5f + q.s * 2f) * unit, pos)
        }
        // 5) 큰 금화
        if (t < 0.9f) {
            val a = if (t < 0.8f) 1f else (0.9f - t) / 0.1f
            val spin = kotlin.math.abs(cos(t * 16.0)).toFloat()
            val pop = if (t < 0.1f) 0.4f + t / 0.1f * 0.8f else 1.2f - (t - 0.1f) * 0.25f
            goldCoin(head, coinR * pop, spin, a)
            // 금화 둘레 반짝이 4개
            for (k in 0 until 4) {
                val ang = t * 9.0 + k * PI / 2
                val rr = coinR * 1.8f
                sparkle(head + Offset((cos(ang) * rr).toFloat(), (sin(ang) * rr).toFloat()), coinR * 0.38f * a * (0.6f + 0.4f * sin(t * 30f + k)), Color.White.copy(alpha = a))
            }
        }
    }
    val tt = ((t - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val textPx = 46f * unit
    Text(
        "💰 +${c.amount}",
        fontSize = 28.sp, fontWeight = FontWeight.Black,
        style = TextStyle(
            brush = Brush.verticalGradient(listOf(Color(0xFFFFFBE0), Color(0xFFFFD43B), Color(0xFFFF9F1C))),
            shadow = Shadow(Color(0xFF9A4A00), Offset(0f, 3f), 10f),
        ),
        modifier = Modifier
            .offset { IntOffset(c.at.x.roundToInt(), (headY - textPx - tt * 24f * unit).roundToInt()) }
            .graphicsLayer {
                translationX = -size.width / 2; translationY = -size.height / 2
                rotationZ = -6f + 6f * tt
            }
            .scale(if (tt < 0.12f) 0.3f + tt / 0.12f * 1.1f else 1.4f - (tt - 0.12f) * 0.35f)
            .alpha(if (tt < 0.7f) 1f else ((1f - tt) / 0.3f).coerceIn(0f, 1f)),
    )
}


// ======================= 💖 부활 =======================

/** 물약이 떨어져 깨지기까지 (이때 살아난 방울이 보임) */
const val REVIVE_DROP_MS = 540L

/** 부활 효과 하나 (좌표는 [ShotLayer]와 같은 기준) */
data class ReviveBurst(val key: Long, val at: Offset, val side: Side, val skin: Animal?)

/**
 * 💖 부활물약: 하늘에서 빛기둥이 내려오고, 물약이 떨어져 톡 깨지며
 * 분홍 충격파 · 천사 고리 · 하트가 둥실둥실 솟아오르고 "💖 부활!"
 */
@Composable
fun ReviveFx(r: ReviveBurst, onDone: (ReviveBurst) -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1_800, easing = LinearEasing))
        onDone(r)
    }
    val pink = Color(0xFFFF5C8A)
    val theme = remember { FxTheme(listOf(pink, Color(0xFFFFB3D1), Color.White, Ui.gold), listOf(FxShape.HEART, FxShape.STAR), "💖") }
    val ps = remember { parts(28) }
    val hearts = remember { parts(14) }
    val density = LocalDensity.current
    val unit = with(density) { 1.dp.toPx() }
    val t = p.value
    // 0 ~ 0.3: 물약이 떨어짐, 0.3: 톡! 깨짐
    val dropT = (t / 0.3f).coerceIn(0f, 1f)
    val after = ((t - 0.3f) / 0.7f).coerceIn(0f, 1f)
    // (1800ms × 0.3 = 540ms = REVIVE_DROP_MS 에 깨짐)
    val bottleY = r.at.y - 170f * unit * (1f - dropT * dropT)

    Canvas(Modifier.fillMaxSize()) {
        // 빛기둥 (위에서 내려옴)
        val beamA = (if (t < 0.15f) t / 0.15f else 1f - ((t - 0.5f) / 0.5f).coerceIn(0f, 1f))
        val beamW = 90f * unit * (0.6f + 0.4f * sin(t * 14f))
        // 아래 끝은 방울 안에서 부드럽게 사라짐 + 바닥에 빛 웅덩이
        val beamEnd = r.at.y + 30f * unit
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.55f to Color(0xFFFFE3EF).copy(alpha = 0.7f * beamA),
                0.85f to Color.White.copy(alpha = 0.85f * beamA), 1f to Color.Transparent, startY = 0f, endY = beamEnd,
            ),
            Offset(r.at.x - beamW / 2, 0f), Size(beamW, beamEnd),
        )
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.85f to Color.White.copy(alpha = 0.95f * beamA), 1f to Color.Transparent, startY = 0f, endY = beamEnd),
            Offset(r.at.x - beamW * 0.15f, 0f), Size(beamW * 0.3f, beamEnd),
        )
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * beamA), Color(0xFFFFB3D1).copy(alpha = 0.4f * beamA), Color.Transparent), r.at, 80f * unit), 80f * unit, r.at)
        // 깨질 때 분홍 폭발
        if (after > 0f && after < 1f) burst(r.at, 95f * unit, after, theme, ps, rays = 16)
        // 천사 고리 (방울 위에서 빙글)
        if (after > 0f) {
            val ringA = (if (after < 0.2f) after / 0.2f else 1f - ((after - 0.6f) / 0.4f).coerceIn(0f, 1f))
            val ringC = r.at + Offset(0f, -78f * unit - 8f * unit * sin(after * 10f))
            drawOval(Ui.gold.copy(alpha = 0.95f * ringA), Offset(ringC.x - 40f * unit, ringC.y - 10f * unit), Size(80f * unit, 20f * unit), style = Stroke(6f * unit))
            drawOval(Color.White.copy(alpha = 0.8f * ringA), Offset(ringC.x - 40f * unit, ringC.y - 10f * unit), Size(80f * unit, 20f * unit), style = Stroke(2f * unit))
            for (k in 0 until 5) {
                val ang = after * 8f + k * 2f * PI.toFloat() / 5
                val pos = ringC + Offset(cos(ang) * 40f * unit, sin(ang) * 10f * unit)
                sparkle(pos, 6f * unit * ringA, Color.White.copy(alpha = ringA))
            }
        }
        // 둥실둥실 솟아오르는 하트
        hearts.forEachIndexed { i, q ->
            val ht = ((after - q.a * 0.3f) / 0.7f).coerceIn(0f, 1f)
            if (ht <= 0f || ht >= 1f) return@forEachIndexed
            val x = r.at.x + (q.s - 0.5f) * 150f * unit + sin(ht * 9f + i) * 14f * unit
            val y = r.at.y - ht * (120f + q.s * 90f) * unit
            val a = if (ht < 0.7f) 1f else (1f - ht) / 0.3f
            fxShape(FxShape.HEART, Offset(x, y), (7f + q.s * 7f) * unit, (if (i % 3 == 0) Color(0xFFFFB3D1) else pink).copy(alpha = a), q.spin * 20f)
        }
        // 떨어지는 물약 병
        if (t < 0.32f) {
            val c = Offset(r.at.x, bottleY)
            val br = 16f * unit
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD6E6), Color.Transparent), c, br * 2.4f), br * 2.4f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9EC0), pink), c - Offset(br * 0.3f, br * 0.3f), br * 1.3f), br, c)
            drawRoundRect(Color(0xFFFFD6E6), Offset(c.x - br * 0.35f, c.y - br * 1.55f), Size(br * 0.7f, br * 0.7f))
            drawRoundRect(Color(0xFFB07A4F), Offset(c.x - br * 0.42f, c.y - br * 1.85f), Size(br * 0.84f, br * 0.4f))
            fxShape(FxShape.HEART, c + Offset(0f, br * 0.1f), br * 0.45f, Color.White.copy(alpha = 0.9f))
            sparkle(c + Offset(br * 0.9f, -br * 0.9f), br * 0.5f, Color.White)
        }
    }
    // "💖 부활!"
    if (after > 0f) {
        val textPx = 60f * unit
        Text(
            "💖 " + tr("부활!", "Revived!", zh = "复活！", es = "¡Revive!", fr = "Ressuscité !", de = "Wiederbelebt!", pt = "Reviveu!", ru = "Ожил!", ja = "復活！"),
            fontSize = 26.sp, fontWeight = FontWeight.Black,
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color(0xFFFF7AA8), Color(0xFFE0115F))),
                shadow = Shadow(Color.White, Offset(0f, 0f), 12f),
            ),
            modifier = Modifier
                .offset { IntOffset(r.at.x.roundToInt(), (r.at.y - textPx - after * 30f * unit).roundToInt()) }
                .graphicsLayer { translationX = -size.width / 2; translationY = -size.height / 2 }
                .scale(if (after < 0.12f) 0.3f + after / 0.12f * 1.1f else 1.4f - (after - 0.12f) * 0.4f)
                .alpha(if (after < 0.75f) 1f else ((1f - after) / 0.25f).coerceIn(0f, 1f)),
        )
    }
}

// ======================= 스킨별 승리 효과 · 타이머 테마 =======================

/**
 * 이겼을 때 내 스킨 테마로 쏟아지는 비 (하트 · 달 · 눈송이 · 금화 …).
 * 🐹 황금 햄스터는 금가루 폭죽이 여기저기서 팡팡 터짐. 승패에는 영향 없음 (모습만).
 */
@Composable
fun WinRain(side: Side, skin: Animal?) {
    val theme = remember(side, skin) { fxTheme(side, skin) }
    val t = rememberInfiniteTransition(label = "winRain")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4_200, easing = LinearEasing)), label = "fall")
    val boom by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "boom")
    val drops = remember { parts(46) }
    val gold = skin == Animal.GOLD_HAMSTER
    val goldTheme = remember { FxTheme(listOf(Color(0xFFFFC21A), Color(0xFFFFF4B0), Color.White, Color(0xFFFF9F1C)), listOf(FxShape.COIN, FxShape.STAR), "✨") }
    val fireParts = remember { parts(26) }
    val snow = skin == Animal.SNOW_HAMSTER
    val flakes = remember { parts(34) }
    Canvas(Modifier.fillMaxSize()) {
        // ❄️ 눈송이 햄스터: 가장자리에 서리가 끼고, 큰 눈꽃이 바람에 휘날림
        if (snow) {
            val frost = Color(0xFFDFF4FF)
            drawRect(Brush.verticalGradient(listOf(frost.copy(alpha = 0.75f), Color.Transparent), 0f, size.height * 0.22f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, frost.copy(alpha = 0.6f)), size.height * 0.8f, size.height))
            drawRect(Brush.horizontalGradient(listOf(frost.copy(alpha = 0.5f), Color.Transparent), 0f, size.width * 0.18f))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, frost.copy(alpha = 0.5f)), size.width * 0.82f, size.width))
            flakes.forEachIndexed { i, q ->
                // 왼쪽 위에서 오른쪽 아래로 휘몰아치며 빙글빙글
                val tt = (fall * (0.8f + q.s * 0.5f) + q.a) % 1f
                val x = -60f + tt * (size.width + 160f) + sin(tt * 9f + i) * 40f
                val y = q.spin.let { (it + 1f) / 2f } * size.height * 0.9f + tt * size.height * 0.35f - size.height * 0.1f + kotlin.math.cos(tt * 7f + i) * 30f
                val r = (10f + q.s * 14f) * density
                fxShape(FxShape.SNOW, Offset(x, y), r, (if (i % 3 == 0) Color(0xFF7FD3FF) else Color.White).copy(alpha = 0.95f), tt * 540f + i * 20f)
                if (i % 4 == 0) sparkle(Offset(x + r, y - r), r * 0.5f * (0.5f + 0.5f * sin(fall * 40f + i)), Color.White)
            }
        }
        drops.forEachIndexed { i, q ->
            val y = ((fall * (0.7f + q.s * 0.6f) + q.a) % 1f) * (size.height + 120f) - 60f
            val x = ((q.k % 997) / 997f) * size.width + sin(fall * 2 * PI.toFloat() * (1 + q.spin) + i) * 26f
            val r = (7f + q.s * 9f) * density
            val shape = theme.shapes[q.k % theme.shapes.size]
            val c0 = theme.colors[q.k % theme.colors.size]
            // 하얀 금화는 빈 고리처럼 보여서 테마 색으로
            val color = if (shape == FxShape.COIN && c0 == Color.White) theme.main else c0
            fxShape(shape, Offset(x, y), r, color.copy(alpha = 0.9f), fall * 360f * q.spin + q.k)
        }
        // 🐹 황금 햄스터: 금가루 폭죽 (자리를 바꿔 가며 3군데)
        if (gold) {
            val round = (fall * 3).toInt()
            for (k in 0 until 3) {
                val p = ((boom + k / 3f) % 1f)
                val seed = (round * 7 + k * 13) % 10
                val c = Offset(size.width * (0.2f + 0.3f * k + (seed - 5) * 0.01f), size.height * (0.18f + 0.07f * ((seed + k) % 4)))
                burst(c, size.minDimension * 0.16f, p, goldTheme, fireParts, rays = 12)
            }
        }
    }
}

/** 타이머 테마: ❄️ 눈송이 햄스터 = 얼음 게이지 · ✨ 황금 햄스터 = 황금 게이지. 그 외 null */
data class TimerTheme(val icon: String, val colors: List<Color>, val flake: FxShape)

fun timerTheme(skin: Animal?): TimerTheme? = when (skin) {
    Animal.SNOW_HAMSTER -> TimerTheme("❄️", listOf(Color(0xFFBFE9FF), Color(0xFF5CC8FF), Color(0xFF3A8DFF)), FxShape.SNOW)
    Animal.GOLD_HAMSTER -> TimerTheme("✨", listOf(Color(0xFFFFF4B0), Color(0xFFFFC21A), Color(0xFFFF9F1C)), FxShape.COIN)
    Animal.PINK_CAT -> TimerTheme("💗", listOf(Color(0xFFFFD6EC), Color(0xFFFF8FC4), Color(0xFFFF4FA0)), FxShape.HEART)
    Animal.BLACK_CAT -> TimerTheme("🌙", listOf(Color(0xFF9B8CFF), Color(0xFF6A4DE0), Color(0xFF3B2A6B)), FxShape.STAR)
    else -> null
}
