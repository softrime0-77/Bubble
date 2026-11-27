package com.bubble.game.ui

import com.bubble.game.i18n.tr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.game.Side
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object Ui {
    val bgTop = Color(0xFFFFE3F1)
    val bgBottom = Color(0xFFDCD6FF)
    val primary = Color(0xFFFF7EB3)
    val primaryDark = Color(0xFFE0518F)
    val accent = Color(0xFF7C6CF2)
    val gold = Color(0xFFFFC94D)
    val danger = Color(0xFFFF5A6E)
    val text = Color(0xFF4A3B5C)
    val card = Color(0xF2FFFFFF)
}

/** 화면 전체에 둥실둥실 떠오르는 비눗방울 배경. */
@Composable
fun BubbleBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val time by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "time",
    )
    val seeds = remember {
        val r = Random(7)
        List(16) { Triple(r.nextFloat(), r.nextFloat(), 10f + r.nextFloat() * 34f) }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ui.bgTop, Ui.bgBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            seeds.forEachIndexed { i, (x, start, r) ->
                val speed = 0.6f + (i % 4) * 0.25f
                val y = 1.1f - ((start + time * speed) % 1.2f)
                val wobble = sin((time * 2 * PI * (1 + i % 3)) + i).toFloat() * 14f
                val c = Offset(x * size.width + wobble, y * size.height)
                drawCircle(Color.White.copy(alpha = 0.28f), r * density / 2.5f, c)
                drawCircle(
                    Color.White.copy(alpha = 0.6f), r * density / 2.5f, c,
                    style = Stroke(width = 1.5f * density),
                )
                drawCircle(Color.White.copy(alpha = 0.8f), r * density / 9f, c + Offset(-r, -r) * (density / 7f))
            }
        }
        content()
    }
}

enum class BubbleMode { NORMAL, SELECTED, TARGET, DISABLED }

/**
 * 세트 하나 = 비눗방울 하나. 안에 고양이/햄스터가 n마리 들어 있습니다.
 * 4마리면 터지기 직전이라 빨갛게 떨고, [explodeKey]가 바뀌면 펑 터지는 효과를 냅니다.
 */
@Composable
fun SetBubble(
    side: Side,
    count: Int,
    title: String,
    mode: BubbleMode,
    size: Dp,
    preview: String? = null,
    explodeKey: Int? = null,
    onClick: (() -> Unit)? = null,
    skin: com.bubble.game.game.Animal? = null,
    modifier: Modifier = Modifier,
    /** 🐰 연승 토끼 리본 수 */
    ribbons: Int = 0,
) {
    val t = rememberInfiniteTransition(label = "bubble")
    val breathe by t.animateFloat(
        0.97f, 1.03f, infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val shake by t.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "shake",
    )
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val danger = count == 4
    val dead = count == 0

    // 마리 수가 바뀔 때마다 통통 튀는 효과
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(count) {
        bounce.snapTo(0.82f)
        bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
    }
    val lift by animateFloatAsState(
        if (mode == BubbleMode.SELECTED) -10f else 0f, spring(dampingRatio = 0.5f), label = "lift",
    )

    // 터짐 효과 (0 → 1)
    val boom = remember { Animatable(1f) }
    var lastBoom by remember { mutableStateOf(explodeKey) }
    LaunchedEffect(explodeKey) {
        if (explodeKey != null && explodeKey != lastBoom) {
            lastBoom = explodeKey
            boom.snapTo(0f)
            boom.animateTo(1f, tween(1_200, easing = FastOutSlowInEasing))
        }
    }

    val click = onClick
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                translationY = lift * density
                translationX = if (danger) shake * 2.2f * density else 0f
            }
            .then(
                if (click != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = click,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bubbleScale = if (dead) 0.9f else breathe * bounce.value
        Canvas(Modifier.fillMaxSize().scale(bubbleScale)) {
            val r = this.size.minDimension / 2 * 0.92f
            val c = center
            if (dead) {
                drawCircle(
                    Color.White.copy(alpha = 0.35f), r, c,
                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
                )
                return@Canvas
            }
            val tint = when {
                danger -> Ui.danger
                com.bubble.game.game.SideLook.of(side) == Side.CAT -> Color(0xFFFFB86B)
                else -> Color(0xFFC8A2FF)
            }
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.85f), tint.copy(alpha = 0.35f), tint.copy(alpha = 0.55f)),
                    center = c + Offset(-r * 0.3f, -r * 0.35f),
                    radius = r * 1.5f,
                ),
                r, c,
            )
            drawCircle(Color.White.copy(alpha = 0.9f), r, c, style = Stroke(width = 2.5.dp.toPx()))
            // 반짝이는 하이라이트
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.13f, c + Offset(-r * 0.5f, -r * 0.55f))
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.06f, c + Offset(-r * 0.25f, -r * 0.72f))
            when (mode) {
                BubbleMode.SELECTED -> drawCircle(Ui.gold, r + 5.dp.toPx(), c, style = Stroke(width = 5.dp.toPx()))
                BubbleMode.TARGET -> drawCircle(
                    Ui.primary.copy(alpha = 0.5f + pulse * 0.5f), r + 4.dp.toPx(), c,
                    style = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 12f), pulse * 34f)),
                )
                else -> Unit
            }
            if (danger) {
                drawCircle(Ui.danger.copy(alpha = 0.25f + pulse * 0.45f), r + 2.dp.toPx(), c, style = Stroke(width = 3.dp.toPx()))
            }
        }

        // 고른 방울: 빙글빙글 반짝이
        if (mode == BubbleMode.SELECTED && !dead) SelectedSparkles(side, skin)

        // 캐릭터들
        if (!dead) {
            BoxWithConstraints(Modifier.fillMaxSize().scale(bubbleScale)) {
                val half = maxWidth / 2
                val critter = if (count == 1) maxWidth * 0.55f else maxWidth * 0.36f
                critterSlots(count).forEachIndexed { i, o ->
                    key(count, i) {
                        val pop = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
                        }
                        Critter(
                            side = side,
                            skin = skin,
                            ribbons = ribbons,
                            phase = i + if (side == Side.CAT) 0 else 5,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = half * o.x * 1.25f, y = half * o.y * 1.25f)
                                .size(critter)
                                .scale(pop.value),
                        )
                    }
                }
            }
        } else {
            Text(tr("텅…", "Empty…", zh = "空…", hi = "खाली…", es = "Vacía…", fr = "Vide…", ar = "فارغة…", de = "Leer…", pt = "Vazia…", ru = "Пусто…", ja = "からっぽ…"), color = Ui.text.copy(alpha = 0.45f), fontSize = 14.sp)
        }

        // 펑! (캐릭터마다 다른 모양으로 터짐)
        if (boom.value < 1f) PopBurst(side, skin, boom.value)

        // 결과 미리보기 말풍선
        if (preview != null) {
            Text(
                preview,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-6).dp)
                    .background(if (preview.contains("💥")) Ui.danger else Ui.accent, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Text(
            if (dead) "$title 💀" else tr("$title · ${count}마리", "$title · ${count}", zh = "$title · ${count}", hi = "$title · ${count}", es = "$title · ${count}", fr = "$title · ${count}", ar = "$title · ${count}", de = "$title · ${count}", pt = "$title · ${count}", ru = "$title · ${count}", ja = "$title · ${count}匹"),
            color = if (danger) Ui.danger else Ui.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 10.dp)
                .alpha(if (mode == BubbleMode.DISABLED && !dead) 0.6f else 1f)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ======================= 계급 배지 =======================

private fun rankColors(rank: com.bubble.game.game.Rank, hueShift: Float): List<Color> = when (rank) {
    com.bubble.game.game.Rank.DIRT -> listOf(Color(0xFF9C7A5B), Color(0xFF7A5A3E))
    com.bubble.game.game.Rank.COPPER -> listOf(Color(0xFFF0A36B), Color(0xFFB8673A), Color(0xFFE38B52))
    com.bubble.game.game.Rank.SILVER -> listOf(Color(0xFFF4F6FA), Color(0xFFAEB6C4), Color(0xFFE2E6EE))
    com.bubble.game.game.Rank.GOLD -> listOf(Color(0xFFFFF1A8), Color(0xFFFFC02E), Color(0xFFE59A00), Color(0xFFFFE27A))
    com.bubble.game.game.Rank.RUBY -> listOf(Color(0xFFFF8FB0), Color(0xFFE0115F), Color(0xFF9B0B3F), Color(0xFFFF5C8A))
    com.bubble.game.game.Rank.SAPPHIRE -> listOf(Color(0xFF9AD7FF), Color(0xFF1F6FEB), Color(0xFF0B3D91), Color(0xFF5AA9FF))
    // 다이아: 무지개빛이 계속 흐르는 가장 화려한 배지
    com.bubble.game.game.Rank.DIAMOND -> List(6) { i ->
        Color.hsv((hueShift * 360f + i * 50f) % 360f, 0.32f, 1f)
    }
    // 핑크다이아: 분홍 → 자홍 → 라벤더 사이를 물결치듯 흐르는 빛
    com.bubble.game.game.Rank.PINK_DIAMOND -> List(6) { i ->
        val h = 312f + 34f * sin(((hueShift + i / 6f) * 2 * PI).toFloat())
        Color.hsv((h + 360f) % 360f, 0.36f + 0.1f * (i % 2), 1f)
    }
}

/** 작은 하트 (핑크다이아 반짝이) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.heart(c: Offset, r: Float, color: Color) {
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.3f, c.x, c.y - r * 0.45f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(p, color)
}

/**
 * 계급 배지. 흙은 효과 없음, 구리 → 은 → 금 → 루비 → 사파이어 → 다이아로 갈수록
 * 반짝이 수·빛 줄기·무지개 효과가 늘어나 더 화려해집니다.
 */
@Composable
fun RankBadge(score: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val rank = com.bubble.game.game.Rank.of(score)
    val level = rank.sparkle
    val t = rememberInfiniteTransition(label = "rank")
    val shine by t.animateFloat(
        -0.4f, 1.4f,
        infiniteRepeatable(tween(if (level >= 3) 1_600 else 2_400, easing = LinearEasing)),
        label = "shine",
    )
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_300, easing = LinearEasing)), label = "tw")
    val hue by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "hue")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")

    val fontSize = if (large) 18.sp else 12.sp
    val padH = if (large) 14.dp else 8.dp
    val padV = if (large) 6.dp else 2.dp
    val textColor = when (rank) {
        com.bubble.game.game.Rank.DIRT, com.bubble.game.game.Rank.COPPER,
        com.bubble.game.game.Rank.RUBY, com.bubble.game.game.Rank.SAPPHIRE -> Color.White
        com.bubble.game.game.Rank.PINK_DIAMOND -> Color(0xFF9C1060)
        else -> Color(0xFF4A3B5C)
    }
    // 흙 0 → 구리 2 → 은 4 → 금 6 → 루비 8 → 사파이어 11 → 다이아 16 → 핑크다이아 24
    val sparkleCount = when (level) { 1 -> 2; 2 -> 4; 3 -> 6; 4 -> 8; 5 -> 11; 6 -> 16; 7 -> 24; else -> 0 }
    val pink = rank == com.bubble.game.game.Rank.PINK_DIAMOND
    val seeds = remember(sparkleCount) {
        val r = Random(level * 31 + 7)
        List(sparkleCount) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        // 금 이상은 뒤에서 은은하게 빛남 (높을수록 크고 진하게)
        if (level >= 3) {
            Canvas(Modifier.matchParentSize().scale(1.15f + (level - 3) * 0.08f)) {
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(rankColors(rank, hue)[1].copy(alpha = (0.4f + (level - 3) * 0.1f) * glow), Color.Transparent),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                )
            }
        }
        Text(
            "${rank.emoji} ${rank.label}",
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Black,
            color = textColor,
            modifier = Modifier
                // 빛 줄기가 둥근 배지 밖으로 나가지 않게 모양대로 자름
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.linearGradient(rankColors(rank, hue)),
                    RoundedCornerShape(50),
                )
                .then(
                    if (level >= 2) {
                        // 빛 줄기가 배지를 훑고 지나감
                        // 글자 뒤(배경 위)로 지나가게 해서 글자가 흐려지지 않게
                        Modifier.drawBehind {
                            // 핑크다이아는 빛 줄기가 두 개 (반 박자 차이로)
                            val bands = if (pink) listOf(shine, (shine + 0.9f) % 1.8f - 0.4f) else listOf(shine)
                            bands.forEach { b ->
                                val x = b * size.width
                                drawRect(
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                                        start = Offset(x - size.height, 0f),
                                        end = Offset(x + size.height, size.height),
                                    ),
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = padH, vertical = padV),
        )
        // 반짝이
        if (sparkleCount > 0) {
            Canvas(Modifier.matchParentSize()) {
                seeds.forEachIndexed { i, (sx, sy, phase) ->
                    val p = (twinkle + phase) % 1f
                    val a = sin(p * PI).toFloat()
                    val pos = Offset(
                        (sx * 1.3f - 0.15f) * size.width,
                        (sy * 1.8f - 0.4f) * size.height,
                    )
                    val color = when (rank) {
                        com.bubble.game.game.Rank.PINK_DIAMOND -> listOf(Color(0xFFFF5FB4), Color.White, Color(0xFFFFB8E2), Color(0xFFE6A8FF))[i % 4]
                        com.bubble.game.game.Rank.DIAMOND -> Color.hsv((hue * 360f + i * 47f) % 360f, 0.5f, 1f)
                        com.bubble.game.game.Rank.SAPPHIRE -> if (i % 2 == 0) Color(0xFFBFE6FF) else Color.White
                        com.bubble.game.game.Rank.RUBY -> if (i % 2 == 0) Color(0xFFFFC2D6) else Color.White
                        com.bubble.game.game.Rank.GOLD -> Color(0xFFFFF4B0)
                        else -> Color.White
                    }
                    val r = (if (large) 7f else 4.5f) * density * (0.4f + a * 0.8f)
                    // 핑크다이아는 셋 중 하나가 하트
                    if (pink && i % 3 == 0) heart(pos, r * 0.75f, color.copy(alpha = a)) else sparkle(pos, r, color.copy(alpha = a))
                }
            }
        }
        // 핑크다이아 전용: 숨쉬는 빛 고리 + 배지 둘레를 도는 별빛
        if (pink) {
            Canvas(Modifier.matchParentSize()) {
                val grow = 1f + glow * 0.12f
                val w = size.width * 1.08f * grow
                val h = size.height * 1.55f * grow
                drawRoundRect(
                    Brush.linearGradient(listOf(Color(0xFFFF6FBF), Color(0xFFFFD1EC), Color(0xFFD9A3FF), Color(0xFFFF6FBF))),
                    topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
                    alpha = 0.35f + 0.45f * glow,
                    style = Stroke(width = (if (large) 2.5f else 1.6f) * density),
                )
                repeat(6) { i ->
                    val ang = ((twinkle + i / 6f) * 2 * PI).toFloat()
                    val pos = center + Offset(cos(ang) * size.width * 0.6f, sin(ang) * size.height * 1.05f)
                    val c = listOf(Color.White, Color(0xFFFF8FCF), Color(0xFFE6B8FF))[i % 3]
                    val r = (if (large) 5.5f else 3.5f) * density
                    if (i % 2 == 0) sparkle(pos, r, c) else heart(pos, r * 0.7f, c)
                }
            }
        }
    }
}

/** 계급이 올랐을 때 축하 화면. 높은 계급일수록 반짝이가 더 많이, 더 크게 터집니다. */
@Composable
fun RankUpCelebration(score: Int, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { com.bubble.game.audio.Music.sfx(com.bubble.game.audio.Music.Sfx.RANK_UP) }
    val rank = com.bubble.game.game.Rank.of(score)
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1_800, easing = FastOutSlowInEasing)) }
    val count = 16 + rank.sparkle * 14
    val parts = remember { List(count) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xAA201530))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = burst.value
            parts.forEachIndexed { i, (a, s, h) ->
                val ang = (i / count.toFloat() + a * 0.05f) * 2 * PI
                val dist = size.minDimension * (0.1f + p * (0.35f + s * 0.3f))
                val pos = center + Offset((cos(ang) * dist).toFloat(), (sin(ang) * dist).toFloat() + p * p * 120f)
                val color = when (rank) {
                    com.bubble.game.game.Rank.PINK_DIAMOND -> listOf(Color(0xFFFF4FA8), Color(0xFFFFB3E0), Color.White, Color(0xFFE3A6FF), Color(0xFFFFD6EC))[i % 5]
                    com.bubble.game.game.Rank.DIAMOND -> Color.hsv(h * 360f, 0.55f, 1f)
                    com.bubble.game.game.Rank.GOLD -> listOf(Ui.gold, Color(0xFFFFF4B0), Color.White)[i % 3]
                    com.bubble.game.game.Rank.RUBY -> listOf(Color(0xFFE0115F), Color(0xFFFF8FB0), Color.White)[i % 3]
                    com.bubble.game.game.Rank.SAPPHIRE -> listOf(Color(0xFF1F6FEB), Color(0xFF9AD7FF), Color.White)[i % 3]
                    else -> listOf(rankColors(rank, 0f)[0], Color.White)[i % 2]
                }
                val pr = (1f - p * 0.7f) * (6f + s * (4f + rank.sparkle * 3f)) * density
                if (rank == com.bubble.game.game.Rank.PINK_DIAMOND && i % 3 == 0) {
                    heart(pos, pr * 0.8f, color.copy(alpha = 1f - p * 0.6f))
                } else {
                    sparkle(pos, pr, color.copy(alpha = 1f - p * 0.6f))
                }
            }
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(pop.value),
        ) {
            Text(tr("🎉 승급! 🎉", "🎉 Rank up! 🎉", zh = "🎉 升级！🎉", hi = "🎉 रैंक अप! 🎉", es = "🎉 ¡Subes de rango! 🎉", fr = "🎉 Rang supérieur ! 🎉", ar = "🎉 ترقية! 🎉", de = "🎉 Aufgestiegen! 🎉", pt = "🎉 Subiu de rank! 🎉", ru = "🎉 Новый ранг! 🎉", ja = "🎉 昇格！🎉"), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
            RankBadge(score, large = true, modifier = Modifier.scale(1.6f))
            androidx.compose.foundation.layout.Spacer(Modifier.size(22.dp))
            Text(
                tr("${rank.label} 계급이 되었어요!", "You reached ${rank.label}!", zh = "你升到了${rank.label}！", hi = "आप ${rank.label} पर पहुँच गए!", es = "¡Llegaste a ${rank.label}!", fr = "Tu as atteint ${rank.label} !", ar = "وصلت إلى ${rank.label}!", de = "Du hast ${rank.label} erreicht!", pt = "Você chegou a ${rank.label}!", ru = "Вы достигли ранга ${rank.label}!", ja = "${rank.label}ランクになりました！"),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text(tr("화면을 누르면 닫혀요", "Tap anywhere to close", zh = "点击任意处关闭", hi = "बंद करने के लिए कहीं भी टैप करें", es = "Toca en cualquier lugar para cerrar", fr = "Touche n’importe où pour fermer", ar = "اضغط في أي مكان للإغلاق", de = "Zum Schließen irgendwo tippen", pt = "Toque em qualquer lugar para fechar", ru = "Нажмите в любом месте, чтобы закрыть", ja = "画面をタップすると閉じます"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}
