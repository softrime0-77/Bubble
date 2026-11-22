package com.bubble.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bubble.game.data.Country
import com.bubble.game.data.Profile
import com.bubble.game.data.RankRow
import com.bubble.game.game.Animal
import com.bubble.game.game.Side
import com.bubble.game.i18n.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 랭킹 한 사람 (TOP 100 목록과 "내 주변 순위" 둘 다 이걸로 보여줌) */
data class RankedPlayer(
    val position: Int,
    val uid: String,
    val name: String,
    val country: String?,
    val score: Int,
    val wins: Int?,
    val losses: Int?,
    /** 지금 보이는 캐릭터 (null이면 기본 고양이) */
    val look: Animal?,
)

/** 다른 사람에게 보이는 내 캐릭터: 장착했고 실제로 쓸 수 있는 것만 (서버 lookSkin과 같은 규칙 + 남은 동물) */
fun Profile.look(): Animal? = equipped?.takeIf { a ->
    a.free || (a.cosmetic && a in skins) || (a.rankOnly && score >= a.rankMin) || (a.consumable && animalCount(a) > 0)
}

fun Profile.ranked(position: Int) = RankedPlayer(position, uid, name, country, score, wins, losses, look())
fun RankRow.ranked() = RankedPlayer(rank, uid, name, country, score, wins, losses, look)

/**
 * 순위 이펙트 단계. 계급표처럼 위로 갈수록 화려하게:
 * 1위 10 → 10위 1 (빛 줄기·반짝이·테두리), 11~30위 0 (반짝이만), 그 밖은 -1 (효과 없음).
 */
fun rankFxLevel(position: Int): Int = when (position) {
    in 1..10 -> 11 - position
    in 11..30 -> 0
    else -> -1
}

/** 순위별 줄 배경색: 1위 금 · 2위 은 · 3위 동 · 4~10위 분홍 보라 */
private fun rowColors(position: Int, hue: Float): List<Color> = when (position) {
    1 -> listOf(Color(0xFFFFF6C2), Color(0xFFFFD54F), Color.hsv((40f + hue * 20f) % 360f, 0.75f, 1f), Color(0xFFFFF0A8))
    2 -> listOf(Color(0xFFFFFFFF), Color(0xFFDCE3EA), Color(0xFFB8C4CF), Color(0xFFF1F4F8))
    3 -> listOf(Color(0xFFFFEBD9), Color(0xFFFFC58F), Color(0xFFE59A5C), Color(0xFFFFE0C4))
    else -> listOf(Color(0xFFFFE6F2), Color(0xFFF3E6FF), Color(0xFFFFE6F2))
}

private fun medal(position: Int) = when (position) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$position" }

/** 4~10위 테마색: 4~5위 보라 · 6~7위 하늘 · 8~10위 분홍 */
private fun tierColor(position: Int): Color = when (position) {
    1 -> Color(0xFFFFC107)
    2 -> Color(0xFFB0C4D6)
    3 -> Color(0xFFFF9E57)
    4, 5 -> Color(0xFFB388FF)
    6, 7 -> Color(0xFF7FC8FF)
    else -> Color(0xFFFF8FC8)
}

/** 랭킹 한 줄. 누르면 [onClick]으로 카드 팝업. */
@Composable
fun RankingRow(p: RankedPlayer, me: Boolean, onClick: () -> Unit) {
    val level = rankFxLevel(p.position)
    val first = p.position == 1
    val shape = RoundedCornerShape(if (level >= 1) 14.dp else 10.dp)
    // 1위: 살짝 숨 쉬듯 커졌다 작아짐 + 이름이 금빛으로 일렁임
    val t = rememberInfiniteTransition(label = "row")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400), RepeatMode.Reverse), label = "breathe")
    val goldShift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_200, easing = LinearEasing)), label = "gold")

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (first) 12.dp else if (level >= 8) 6.dp else if (level >= 1) 3.dp else 0.dp)
            .scale(if (first) 1f + 0.018f * breathe else 1f),
    ) {
        if (level >= 0) RowFx(p.position, level, shape, front = false, modifier = Modifier.matchParentSize())
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (me) Ui.primary.copy(alpha = 0.18f) else Color.Transparent, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = if (first) 16.dp else if (level >= 8) 10.dp else if (level >= 1) 8.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                if (first) {
                    Text("👑", fontSize = 16.sp, modifier = Modifier.offset(y = (-22 - 3 * breathe).dp).rotate(-12f + 8f * breathe))
                }
                Text(
                    medal(p.position),
                    color = if (level >= 1) tierColor(p.position).copy(alpha = 1f).let { c -> lerpColor(c, Ui.text, 0.45f) } else Ui.text,
                    fontWeight = FontWeight.Black, maxLines = 1,
                    fontSize = when (p.position) { 1 -> 30.sp; 2, 3 -> 24.sp; in 4..10 -> 18.sp; else -> 14.sp },
                )
            }
            Text(Country.flag(p.country), fontSize = if (first) 18.sp else 16.sp, modifier = Modifier.width(26.dp))
            val name = p.name + if (me) tr(" (나)", " (me)", zh = " (我)", hi = " (मैं)", es = " (yo)", fr = " (moi)", ar = " (أنا)", de = " (ich)", pt = " (eu)", ru = " (я)", ja = " (自分)") else ""
            if (first) {
                // 금빛 그라데이션 글자가 흐르듯 움직임
                val x = goldShift * 600f
                Text(
                    name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF6B3E00), Color(0xFFA86400), Color(0xFFD99A00), Color(0xFFA86400), Color(0xFF6B3E00)),
                            start = Offset(x - 300f, 0f), end = Offset(x, 0f),
                            tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                        ),
                        fontSize = 18.sp, fontWeight = FontWeight.Black,
                        shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset.Zero, 8f),
                    ),
                )
            } else if (p.look == Animal.UNICORN) {
                // 🦄 유니콘: 닉네임이 무지개색으로 흐름
                val x = goldShift * 600f
                Text(
                    name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(rainbowText, start = Offset(x - 300f, 0f), end = Offset(x, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Mirror),
                        fontSize = if (level >= 8) 16.sp else 14.sp, fontWeight = FontWeight.Black,
                    ),
                )
            } else {
                Text(
                    name, modifier = Modifier.weight(1f), color = Ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (level >= 1) FontWeight.Black else FontWeight.Normal,
                    fontSize = if (level >= 8) 16.sp else 14.sp,
                )
            }
            RankBadge(p.score)
            Spacer(Modifier.width(8.dp))
            Text(
                tr("${p.score}점", "${p.score} pts", zh = "${p.score} 分", hi = "${p.score} अंक", es = "${p.score} pts", fr = "${p.score} pts", ar = "${p.score} نقطة", de = "${p.score} Pkt", pt = "${p.score} pts", ru = "${p.score} очк.", ja = "${p.score}点"),
                color = if (first) Color(0xFFC77700) else Ui.primaryDark, fontWeight = FontWeight.Black,
                fontSize = if (first) 16.sp else 14.sp,
            )
        }
        if (level >= 1) RowFx(p.position, level, shape, front = true, modifier = Modifier.matchParentSize())
    }
}

private fun lerpColor(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
)

/**
 * [front] false: 줄 뒤 (햇살·후광·배경·빛 줄기·빛나는 테두리)
 * [front] true: 줄 앞 (반짝이·떠오르는 빛가루·도는 별빛)
 */
@Composable
private fun RowFx(position: Int, level: Int, shape: RoundedCornerShape, front: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rowFx")
    // 높은 순위일수록 빛 줄기가 빨리 지나감
    val shine by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1_200 + (10 - level.coerceAtLeast(0)) * 170, easing = LinearEasing)), label = "shine")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_500, easing = LinearEasing)), label = "tw")
    val hue by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "hue")
    val glow by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "glow")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "rise")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin")
    // 1위 10 → 10위 1: 반짝이 32 → 5 / 11~30위: 3개
    val count = if (level >= 1) 3 + level * 2 + (if (level == 10) 9 else 0) else 3
    val seeds = remember(position) {
        val r = Random(position * 97 + 13)
        List(count) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    // 떠오르는 빛가루: 1위 18 → 10위 2
    val motes = remember(position) {
        val r = Random(position * 31 + 5)
        List(if (level >= 1) level * 2 - 2 + (if (level == 10) 4 else 0) else 0) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val tc = tierColor(position)

    Box(modifier) {
        if (!front) {
            if (level >= 1) {
                // 1~3위: 메달 뒤에서 도는 햇살 (1위는 크고 길게)
                if (level >= 8) {
                    Canvas(Modifier.matchParentSize()) {
                        val c = Offset(30.dp.toPx(), size.height / 2)
                        val len = if (level == 10) size.width * 0.75f else size.width * 0.35f
                        val rays = if (level == 10) 16 else 10
                        val alpha = if (level == 10) 0.32f + 0.18f * glow else 0.18f + 0.08f * glow
                        repeat(rays) { i ->
                            val a0 = (spin / 360f + i.toFloat() / rays) * 2 * PI.toFloat()
                            val w = if (i % 2 == 0) 0.09f else 0.05f
                            val path = Path().apply {
                                moveTo(c.x, c.y)
                                lineTo(c.x + cos(a0 - w) * len, c.y + sin(a0 - w) * len)
                                lineTo(c.x + cos(a0 + w) * len, c.y + sin(a0 + w) * len)
                                close()
                            }
                            drawPath(path, Brush.radialGradient(listOf(tc.copy(alpha = alpha), Color.Transparent), center = c, radius = len))
                        }
                    }
                }
                // 후광: 줄 바깥으로 번지는 빛 (위로 갈수록 크고 진하게)
                Canvas(Modifier.matchParentSize().scale(1f + 0.012f * level + if (level == 10) 0.05f * glow else 0f, 1f + 0.05f * level + if (level == 10) 0.25f * glow else 0f)) {
                    val a = (0.12f + level * 0.035f) * (0.6f + 0.4f * glow)
                    drawRoundRect(
                        Brush.radialGradient(listOf(tc.copy(alpha = a), tc.copy(alpha = a * 0.4f), Color.Transparent), radius = size.width * 0.62f),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                    )
                }
                Canvas(Modifier.matchParentSize().clip(shape)) {
                    // 배경 (4~10위는 아래로 갈수록 옅게)
                    val bgA = if (position <= 3) 1f else 0.45f + level * 0.07f
                    val cols = if (position <= 3) rowColors(position, hue) else listOf(Color.White, tc.copy(alpha = 0.35f), Color(0xFFFFE6F2), tc.copy(alpha = 0.25f))
                    drawRect(Brush.linearGradient(cols.map { it.copy(alpha = it.alpha * bgA) }))
                    // 1위: 안쪽에서 맥박처럼 빛나는 금빛
                    if (level == 10) {
                        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * glow), Color.Transparent), center = Offset(size.width * 0.3f, size.height / 2), radius = size.width * 0.5f))
                    }
                    // 빛 줄기 (1위 세 줄 · 2~3위 두 줄)
                    val bands = when {
                        level == 10 -> listOf(shine, (shine + 0.6f + 0.4f) % 1.8f - 0.4f, (shine + 1.2f + 0.4f) % 1.8f - 0.4f)
                        level >= 8 -> listOf(shine, (shine + 0.9f + 0.4f) % 1.8f - 0.4f)
                        else -> listOf(shine)
                    }
                    bands.forEach { b ->
                        val x = b * size.width
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f + level * 0.05f), Color.Transparent),
                                start = Offset(x - size.height, 0f),
                                end = Offset(x + size.height, size.height),
                            ),
                        )
                    }
                }
                // 빛나는 테두리: 넓고 흐린 빛 + 선명한 선 (1위 무지개)
                Canvas(Modifier.matchParentSize()) {
                    val brush = if (level == 10) {
                        Brush.sweepGradient(List(7) { i -> Color.hsv((hue * 360f + i * 60f) % 360f, 0.6f, 1f) })
                    } else if (level >= 8) {
                        Brush.linearGradient(rowColors(position, hue).drop(1))
                    } else {
                        Brush.linearGradient(listOf(tc, Color.White, tc))
                    }
                    val w = (if (level == 10) 3f else if (level >= 8) 2.2f else 1f + level * 0.15f) * density
                    val cr = CornerRadius(14.dp.toPx())
                    drawRoundRect(brush, cornerRadius = cr, style = Stroke(w * 3.5f), alpha = (0.12f + level * 0.025f) * (0.5f + 0.5f * glow))
                    drawRoundRect(brush, cornerRadius = cr, style = Stroke(w), alpha = 0.55f + level * 0.045f)
                }
            }
        } else {
            Canvas(Modifier.matchParentSize()) {
                // 떠오르는 빛가루
                motes.forEachIndexed { i, (sx, ph, sz) ->
                    val q = (rise * (0.7f + sz * 0.6f) + ph) % 1f
                    val x = (0.08f + sx * 0.88f) * size.width + sin((q * 2 + sx) * PI.toFloat() * 2) * 4f * density
                    // 글자를 가리지 않게 위·아래 가장자리에서만 떠오름
                    val y = if (i % 2 == 0) size.height * (0.28f - q * 0.55f) else size.height * (1.25f - q * 0.5f)
                    val a = sin(q * PI).toFloat()
                    val r = (1.4f + sz * 1.8f + level * 0.12f) * density
                    val col = if (level == 10) (if (i % 3 == 0) Color.White else Color(0xFFFFD54F)) else if (i % 2 == 0) Color.White else tc
                    drawCircle(col.copy(alpha = a * 0.35f), r * 2.4f, Offset(x, y))
                    drawCircle(col.copy(alpha = a), r, Offset(x, y))
                }
            }
        }
        if (front || level == 0) {
            // 반짝이
            Canvas(Modifier.matchParentSize()) {
                seeds.forEachIndexed { i, (sx, sy, ph) ->
                    val p = (twinkle * (1f + (i % 3) * 0.2f) + ph) % 1f
                    val a = sin(p * PI).toFloat()
                    // 글자를 가리지 않게 테두리 근처(위·아래)에만
                    val fy = if (sy < 0.5f) -0.12f + sy * 0.4f else 0.92f + (sy - 0.5f) * 0.4f
                    val pos = Offset((0.03f + sx * 0.94f) * size.width, fy * size.height)
                    val base = if (level >= 1) 2.6f + level * 0.5f else 2.6f
                    val r = base * density * (0.4f + a * 0.8f)
                    val color = when {
                        level == 10 -> listOf(Color(0xFFFFF59D), Color.White, Color(0xFFFFD54F), Color.hsv((hue * 360f + i * 50f) % 360f, 0.45f, 1f))[i % 4]
                        level >= 8 -> if (i % 2 == 0) Color.White else lerpColor(tc, Color.White, 0.5f)
                        level >= 1 -> if (i % 2 == 0) Color.White else lerpColor(tc, Color.White, 0.3f)
                        else -> if (i % 2 == 0) Color(0xFFFFE27A) else Color(0xFFFFB3D6)
                    }
                    twinkleStar(pos, r, color.copy(alpha = a * (if (level >= 1) 0.95f else 0.8f)))
                }
                // 1~3위: 메달 주위를 도는 별빛 (1위 5개)
                if (level >= 8) {
                    val cx = 30.dp.toPx()
                    val cy = size.height / 2
                    val n = if (level == 10) 5 else 2
                    repeat(n) { k ->
                        val ang = (twinkle + k.toFloat() / n) * 2 * PI.toFloat()
                        val pos = Offset(cx + cos(ang) * 24.dp.toPx(), cy + sin(ang) * 16.dp.toPx())
                        twinkleStar(pos, (if (level == 10) 4f else 3f) * density, Color(0xFFFFF8E1).copy(alpha = 0.95f))
                    }
                }
            }
        }
    }
}

/** 네 갈래 반짝이 */
private fun DrawScope.twinkleStar(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 1.6f)
        quadraticTo(c.x, c.y, c.x + r * 1.6f, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r * 1.6f)
        quadraticTo(c.x, c.y, c.x - r * 1.6f, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r * 1.6f)
        close()
    }
    drawPath(p, color)
    drawCircle(color.copy(alpha = color.alpha * 0.35f), r * 0.9f, c)
}

/** 랭킹 줄을 누르면 뜨는 작은 카드: 캐릭터 · 전체 닉네임 · 나라 · 계급 · 점수 · 전적. 순위가 높을수록 화려하게. */
@Composable
fun PlayerCardDialog(p: RankedPlayer, me: Boolean, onDismiss: () -> Unit) {
    val level = rankFxLevel(p.position)
    val tc = tierColor(p.position)
    val t = rememberInfiniteTransition(label = "card")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "spin")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "bob")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "tw")
    val hue by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "hue")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "rise")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4_200, easing = LinearEasing)), label = "fall")
    val boom by t.animateFloat(0f, 3f, infiniteRepeatable(tween(4_500, easing = LinearEasing)), label = "boom")
    val shine by t.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(2_000, easing = LinearEasing)), label = "shine")
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    val stars = remember(level) {
        val r = Random(p.position * 7 + 3)
        List(if (level >= 1) 14 + level * 5 else 10) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val motes = remember(level) {
        val r = Random(p.position * 11 + 1)
        List(if (level >= 1) 8 + level * 3 else 8) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val confetti = remember(level) {
        val r = Random(41)
        List(if (level == 10) 46 else if (level >= 8) 20 else 0) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val fx = FxCard(level, tc, spin, twinkle, hue, glow, rise, fall, boom, shine, bob)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val shown = pop.value.coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize().alpha(shown)) {
                cardBackdrop(fx, p.position, stars, motes, confetti)
            }
            CardBody(p, me, fx, Modifier.scale(0.6f + 0.4f * pop.value).alpha(shown), onDismiss)
        }
    }
}

/** 카드 애니메이션 값 묶음 */
private class FxCard(
    val level: Int, val tc: Color, val spin: Float, val twinkle: Float, val hue: Float, val glow: Float,
    val rise: Float, val fall: Float, val boom: Float, val shine: Float, val bob: Float,
)

private val confettiColors = listOf(Color(0xFFFFD54F), Color(0xFFFF7EB3), Color(0xFF7C6CF2), Color(0xFF6EE7B7), Color(0xFF7FC8FF), Color.White)

/** 카드 뒤 화면 전체: 빛 웅덩이 · 도는 햇살(두 겹) · 폭죽 · 반짝이 · 떠오르는 빛가루 · 꽃가루 */
private fun DrawScope.cardBackdrop(
    fx: FxCard, position: Int,
    stars: List<Triple<Float, Float, Float>>, motes: List<Triple<Float, Float, Float>>, confetti: List<Triple<Float, Float, Float>>,
) {
    val level = fx.level
    val c = center
    val tau = 2 * PI.toFloat()
    // 카드 뒤 빛 웅덩이
    if (level >= 0) {
        val a = if (level >= 1) 0.25f + level * 0.04f else 0.18f
        drawCircle(Brush.radialGradient(listOf(fx.tc.copy(alpha = a * fx.glow), Color.Transparent), center = c, radius = size.minDimension * (0.45f + level.coerceAtLeast(0) * 0.02f)), size.minDimension, c)
    }
    // 도는 햇살: 1~10위, 1~3위는 반대로 도는 가는 햇살이 한 겹 더
    if (level >= 1) {
        val r = size.minDimension * (0.5f + level * 0.03f)
        val layers = if (level >= 8) 2 else 1
        repeat(layers) { layer ->
            val rays = if (layer == 0) 8 + level * 2 else 12 + level
            val dir = if (layer == 0) 1f else -1.6f
            val w = if (layer == 0) 0.1f else 0.035f
            val col = if (position == 1 && layer == 1) Color.White else fx.tc
            val alpha = (if (layer == 0) 0.22f + level * 0.035f else 0.3f) * (0.75f + 0.25f * fx.glow)
            repeat(rays) { i ->
                val a0 = (fx.spin * dir / 360f + i.toFloat() / rays) * tau
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + cos(a0 - w) * r, c.y + sin(a0 - w) * r)
                    lineTo(c.x + cos(a0 + w) * r, c.y + sin(a0 + w) * r)
                    close()
                }
                drawPath(path, Brush.radialGradient(listOf(col.copy(alpha = alpha), Color.Transparent), center = c, radius = r))
            }
        }
    }
    // 1~3위: 카드 둘레에서 팡팡 터지는 폭죽 (1위는 셋)
    if (level >= 8) {
        val spots = listOf(Offset(0.18f, 0.2f), Offset(0.84f, 0.26f), Offset(0.5f, 0.1f))
        val n = if (level == 10) 3 else 1
        repeat(n) { k ->
            val local = (fx.boom - k) .let { if (it < 0f) it + 3f else it }
            if (local < 1f) {
                val q = local
                val o = Offset(spots[k].x * size.width, spots[k].y * size.height)
                val rr = (30f + 60f * q) * density
                val a = (1f - q).coerceIn(0f, 1f)
                repeat(14) { i ->
                    val ang = i / 14f * tau
                    val col = confettiColors[(i + k) % confettiColors.size]
                    val pt = Offset(o.x + cos(ang) * rr, o.y + sin(ang) * rr + q * q * 30f * density)
                    drawCircle(col.copy(alpha = a), (3.2f - 1.6f * q) * density, pt)
                    drawLine(col.copy(alpha = a * 0.5f), Offset(o.x + cos(ang) * rr * 0.6f, o.y + sin(ang) * rr * 0.6f), pt, 1.5f * density)
                }
            }
        }
    }
    // 반짝이
    stars.forEachIndexed { i, (sx, sy, ph) ->
        val q = (fx.twinkle + ph) % 1f
        val a = sin(q * PI).toFloat()
        val col = if (position == 1) Color.hsv((fx.hue * 360f + i * 40f) % 360f, 0.4f, 1f) else if (i % 2 == 0) Color.White else lerpColor(fx.tc, Color.White, 0.3f)
        twinkleStar(Offset(sx * size.width, sy * size.height), (3.5f + level.coerceAtLeast(0) * 0.45f) * density * (0.4f + a), col.copy(alpha = a))
    }
    // 떠오르는 빛가루
    motes.forEachIndexed { i, (sx, ph, sz) ->
        val q = (fx.rise * (0.6f + sz * 0.7f) + ph) % 1f
        val x = sx * size.width + sin((q * 3 + sx) * tau) * 8f * density
        val y = size.height * (1.05f - q * 1.1f)
        val a = sin(q * PI).toFloat()
        val r = (2f + sz * 2.5f) * density
        val col = if (position == 1) (if (i % 3 == 0) Color.White else Color(0xFFFFD54F)) else if (i % 2 == 0) Color.White else fx.tc
        drawCircle(col.copy(alpha = a * 0.3f), r * 2.6f, Offset(x, y))
        drawCircle(col.copy(alpha = a), r, Offset(x, y))
    }
    // 꽃가루 (1~3위)
    confetti.forEachIndexed { i, (sx, ph, sp) ->
        val q = (fx.fall * (0.7f + sp * 0.6f) + ph) % 1f
        val x = sx * size.width + sin((q * 4 + sx * 7) * tau / 2) * 14f * density
        val y = size.height * (q * 1.1f - 0.05f)
        val w = 6f * density
        val h = 10f * density * (0.4f + 0.6f * kotlin.math.abs(cos((q * 6 + sp) * tau)))
        val col = confettiColors[i % confettiColors.size]
        drawRect(col.copy(alpha = 0.9f), Offset(x - w / 2, y - h / 2), androidx.compose.ui.geometry.Size(w, h))
    }
}

@Composable
private fun CardBody(p: RankedPlayer, me: Boolean, fx: FxCard, modifier: Modifier, onDismiss: () -> Unit) {
    val level = fx.level
    val bg = when (p.position) {
        1 -> listOf(Color(0xFFFFFDF0), Color(0xFFFFEDB0), Color(0xFFFFD970))
        2 -> listOf(Color(0xFFFFFFFF), Color(0xFFEDF1F6), Color(0xFFD5DEE7))
        3 -> listOf(Color(0xFFFFF8F1), Color(0xFFFFE2C8), Color(0xFFFFC898))
        in 4..10 -> listOf(Color.White, lerpColor(fx.tc, Color.White, 0.82f), lerpColor(fx.tc, Color.White, 0.65f))
        else -> listOf(Color.White, Color(0xFFF6EEFF))
    }
    val border = when {
        p.position == 1 -> Brush.sweepGradient(List(7) { i -> Color.hsv((fx.hue * 360f + i * 60f) % 360f, 0.6f, 1f) })
        level >= 8 -> Brush.linearGradient(rowColors(p.position, fx.hue).drop(1))
        level >= 1 -> Brush.linearGradient(listOf(fx.tc, Color.White, fx.tc))
        else -> Brush.linearGradient(listOf(Ui.primary.copy(alpha = 0.5f), Ui.accent.copy(alpha = 0.5f)))
    }
    val shape = RoundedCornerShape(30.dp)
    Box(modifier.padding(30.dp)) {
        // 카드 둘레로 번지는 빛 (넓고 흐린 테두리)
        if (level >= 1) {
            Canvas(Modifier.matchParentSize()) {
                val w = (4f + level * 1.4f) * density
                drawRoundRect(border, cornerRadius = CornerRadius(30.dp.toPx()), style = Stroke(w * 2), alpha = (0.18f + level * 0.025f) * fx.glow)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(290.dp)
                .clip(shape)
                .background(Brush.verticalGradient(bg), shape)
                // 카드를 훑고 지나가는 빛 줄기
                .then(
                    if (level >= 0) Modifier.drawBehind {
                        val bands = if (level >= 8) listOf(fx.shine, (fx.shine + 1f + 0.5f) % 2f - 0.5f) else listOf(fx.shine)
                        bands.forEach { b ->
                            val x = b * size.width
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = if (level >= 1) 0.55f else 0.35f), Color.Transparent),
                                    start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height * 0.4f),
                                ),
                            )
                        }
                    } else Modifier,
                )
                .border(if (p.position == 1) 4.dp else if (level >= 1) 3.dp else 2.dp, border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            CardTitle(p, fx)
            Spacer(Modifier.height(6.dp))
            CardCritter(p, fx)
            Spacer(Modifier.height(6.dp))
            // 닉네임 전체 (잘리지 않게 줄바꿈)
            val name = p.name + if (me) tr(" (나)", " (me)", zh = " (我)", es = " (yo)", fr = " (moi)", de = " (ich)", pt = " (eu)", ru = " (я)", ja = " (自分)") else ""
            if (p.position == 1) {
                val x = fx.shine * 400f
                Text(
                    name, textAlign = TextAlign.Center, maxLines = 3,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF6B3E00), Color(0xFFA86400), Color(0xFFD99A00), Color(0xFFA86400), Color(0xFF6B3E00)),
                            start = Offset(x - 300f, 0f), end = Offset(x, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                        ),
                        fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset.Zero, 10f),
                    ),
                )
            } else {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ui.text, textAlign = TextAlign.Center, maxLines = 3)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${Country.flag(p.country)} ${Country.name(p.country).ifBlank { tr("알 수 없음", "Unknown", zh = "未知", es = "Desconocido", fr = "Inconnu", de = "Unbekannt", pt = "Desconhecido", ru = "Неизвестно", ja = "不明") }}",
                fontSize = 15.sp, color = Ui.text.copy(alpha = 0.8f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "🐾 " + (p.look?.label ?: tr("기본 고양이", "Basic Cat", zh = "基础猫咪", es = "Gato básico", fr = "Chat de base", de = "Standardkatze", pt = "Gato básico", ru = "Обычный котик", ja = "ふつうのネコ")),
                fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            RankBadge(p.score, large = true)
            Spacer(Modifier.height(8.dp))
            Text(
                tr("${p.score}점", "${p.score} pts", zh = "${p.score} 分", es = "${p.score} pts", fr = "${p.score} pts", de = "${p.score} Pkt", pt = "${p.score} pts", ru = "${p.score} очк.", ja = "${p.score}点"),
                fontSize = if (p.position == 1) 26.sp else 22.sp, fontWeight = FontWeight.Black,
                color = if (p.position == 1) Color(0xFFC77700) else Ui.primaryDark,
            )
            if (p.wins != null && p.losses != null) {
                Text(
                    tr("${p.wins}승 ${p.losses}패", "${p.wins}W ${p.losses}L", zh = "${p.wins}胜 ${p.losses}负", es = "${p.wins}V ${p.losses}D", fr = "${p.wins}V ${p.losses}D", de = "${p.wins}S ${p.losses}N", pt = "${p.wins}V ${p.losses}D", ru = "${p.wins}В ${p.losses}П", ja = "${p.wins}勝 ${p.losses}敗"),
                    fontSize = 14.sp, color = Ui.text.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (p.position == 1) Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF7EB3), Color(0xFF7C6CF2)))
                        else Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)),
                        RoundedCornerShape(50),
                    )
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("닫기", "Close", zh = "关闭", es = "Cerrar", fr = "Fermer", de = "Schließen", pt = "Fechar", ru = "Закрыть", ja = "閉じる"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }
        // 카드 모서리에서 반짝이는 별 (1~10위)
        if (level >= 1) {
            Canvas(Modifier.matchParentSize()) {
                val corners = listOf(Offset(14f, 14f), Offset(size.width - 14f, 18f), Offset(size.width - 18f, size.height - 14f), Offset(16f, size.height - 18f))
                corners.forEachIndexed { i, o ->
                    val q = (fx.twinkle + i * 0.25f) % 1f
                    val a = sin(q * PI).toFloat()
                    twinkleStar(o, (5f + level * 0.5f) * density * (0.5f + a * 0.7f), (if (p.position == 1) Color(0xFFFFF59D) else Color.White).copy(alpha = a))
                }
            }
        }
    }
}

/** 순위 리본: 순위 색 그라데이션 캡슐 + 빛 */
@Composable
private fun CardTitle(p: RankedPlayer, fx: FxCard) {
    val n = p.position
    val label = tr("${n}위", "#${n}", zh = "第${n}名", ja = "${n}位")
    val text = if (n <= 3) "${medal(n)} $label" else label
    val level = fx.level
    if (level >= 1) {
        val cols = when (n) {
            1 -> listOf(Color(0xFFFFB300), Color(0xFFFFE082), Color(0xFFFFB300))
            2 -> listOf(Color(0xFF90A4AE), Color(0xFFECEFF1), Color(0xFF90A4AE))
            3 -> listOf(Color(0xFFE0803A), Color(0xFFFFD2A8), Color(0xFFE0803A))
            else -> listOf(fx.tc, lerpColor(fx.tc, Color.White, 0.6f), fx.tc)
        }
        Text(
            text,
            fontSize = if (n == 1) 26.sp else 20.sp, fontWeight = FontWeight.Black,
            color = if (n <= 3) Color(0xFF5A3A00).let { if (n == 2) Color(0xFF37474F) else it } else Color.White,
            modifier = Modifier
                .scale(1f + 0.04f * fx.glow * (if (n == 1) 1f else 0.4f))
                .background(Brush.horizontalGradient(cols), RoundedCornerShape(50))
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 4.dp),
        )
    } else {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
    }
}

/** 캐릭터: 뒤에서 빛나는 후광 · 주위를 도는 별빛 · 1위 왕관 */
@Composable
private fun CardCritter(p: RankedPlayer, fx: FxCard) {
    val level = fx.level
    val first = p.position == 1
    val sz = if (first) 130.dp else if (level >= 1) 112.dp else 100.dp
    Box(Modifier.size(sz + 40.dp), contentAlignment = Alignment.Center) {
        if (level >= 0) {
            Canvas(Modifier.matchParentSize()) {
                val c = center
                val a = if (level >= 1) 0.35f + level * 0.04f else 0.25f
                drawCircle(Brush.radialGradient(listOf(fx.tc.copy(alpha = a * fx.glow), Color.Transparent), center = c, radius = size.minDimension / 2), size.minDimension / 2, c)
                // 빛 고리 (1~3위)
                if (level >= 8) {
                    drawCircle(
                        Brush.sweepGradient(List(7) { i -> if (first) Color.hsv((fx.hue * 360f + i * 60f) % 360f, 0.5f, 1f) else fx.tc.copy(alpha = if (i % 2 == 0) 0.9f else 0.2f) }, c),
                        size.minDimension * 0.44f, c, style = Stroke(3f * density), alpha = 0.7f,
                    )
                }
                val n = if (level >= 8) 6 else if (level >= 1) 3 else 0
                repeat(n) { k ->
                    val ang = (fx.twinkle + k.toFloat() / n) * 2 * PI.toFloat()
                    val pos = Offset(c.x + cos(ang) * size.width * 0.44f, c.y + sin(ang) * size.height * 0.44f)
                    twinkleStar(pos, (if (first) 6f else 4.5f) * density, (if (first) Color(0xFFFFF59D) else Color.White).copy(alpha = 0.95f))
                }
            }
        }
        Critter(Side.CAT, Modifier.size(sz).offset(y = (fx.bob * 4).dp), skin = p.look, happy = true)
        if (first) {
            Text("👑", fontSize = 40.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-6 + fx.bob * 3).dp).rotate(fx.bob * 8f))
        }
    }
}
