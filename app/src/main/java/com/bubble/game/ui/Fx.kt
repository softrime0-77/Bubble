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
enum class FxShape { STAR, HEART, PAW, SNOW, COIN, BONE, LEAF, CLOUD, MOON, SEED }

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
        val label = "+${s.power}"
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
fun CheckinCelebration(day: Int, coins: Int, potion: Boolean, onDismiss: () -> Unit) {
    val t = rememberInfiniteTransition(label = "checkin")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing)), label = "fall")
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
    val confetti = remember { List(70) { Part(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2 - 1, Random.nextInt(1000)) } }
    val confettiColors = listOf(Ui.primary, Ui.gold, Ui.accent, Color(0xFF5CD6B4), Color(0xFF6EC1FF), Color(0xFFFF8A65), Color.White)
    val burstTheme = FxTheme(listOf(Ui.gold, Ui.primary, Color.White, Ui.accent), listOf(FxShape.STAR, FxShape.HEART, FxShape.COIN), "🎉")
    val burstParts = remember { parts(34) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xCC3A1F5C), Color(0xEE140B22))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // 회전하는 빛줄기 + 색종이
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
                drawPath(path, Brush.radialGradient(listOf(Ui.gold.copy(alpha = 0.32f * pop.value), Color.Transparent), c, big * 0.6f))
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
