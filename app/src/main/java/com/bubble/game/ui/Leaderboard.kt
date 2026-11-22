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

/** 랭킹 줄을 누르면 뜨는 작은 카드: 캐릭터 · 전체 닉네임 · 나라 · 계급 · 점수 · 전적 */
@Composable
fun PlayerCardDialog(p: RankedPlayer, me: Boolean, onDismiss: () -> Unit) {
    val level = rankFxLevel(p.position)
    val t = rememberInfiniteTransition(label = "card")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "bob")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "tw")
    val hue by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "hue")
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    val stars = remember(level) {
        val r = Random(p.position * 7 + 3)
        List(if (level >= 1) 10 + level * 4 else if (level == 0) 8 else 0) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // 1~10위: 뒤에서 도는 빛 줄기 (1위가 가장 많고 진함)
            if (level >= 1) {
                Canvas(Modifier.size(420.dp).rotate(spin).alpha(pop.value.coerceIn(0f, 1f))) {
                    val rays = 6 + level
                    val c = when (p.position) { 1 -> Color(0xFFFFD54F); 2 -> Color(0xFFE3EAF0); 3 -> Color(0xFFFFB27A); else -> Color(0xFFFFB3D6) }
                    repeat(rays) { i ->
                        val a0 = (i.toFloat() / rays) * 2 * PI.toFloat()
                        val w = 0.11f
                        val r = size.minDimension / 2
                        val path = Path().apply {
                            moveTo(center.x, center.y)
                            lineTo(center.x + cos(a0 - w) * r, center.y + sin(a0 - w) * r)
                            lineTo(center.x + cos(a0 + w) * r, center.y + sin(a0 + w) * r)
                            close()
                        }
                        drawPath(path, Brush.radialGradient(listOf(c.copy(alpha = 0.25f + level * 0.04f), Color.Transparent), center = center, radius = r))
                    }
                }
            }
            // 반짝이 (11~30위도 조금)
            if (stars.isNotEmpty()) {
                Canvas(Modifier.size(360.dp)) {
                    stars.forEachIndexed { i, (sx, sy, ph) ->
                        val q = (twinkle + ph) % 1f
                        val a = sin(q * PI).toFloat()
                        val color = if (p.position == 1) Color.hsv((hue * 360f + i * 40f) % 360f, 0.4f, 1f) else if (i % 2 == 0) Color.White else Color(0xFFFFE27A)
                        twinkleStar(Offset(sx * size.width, sy * size.height), (3f + level.coerceAtLeast(0) * 0.4f) * density * (0.4f + a), color.copy(alpha = a))
                    }
                }
            }
            CardBody(p, me, level, bob, hue, Modifier.scale(0.7f + 0.3f * pop.value).alpha(pop.value.coerceIn(0f, 1f)), onDismiss)
        }
    }
}

@Composable
private fun CardBody(p: RankedPlayer, me: Boolean, level: Int, bob: Float, hue: Float, modifier: Modifier, onDismiss: () -> Unit) {
    val bg = when (p.position) {
        1 -> listOf(Color(0xFFFFFBE6), Color(0xFFFFE9A6))
        2 -> listOf(Color(0xFFFFFFFF), Color(0xFFE6ECF2))
        3 -> listOf(Color(0xFFFFF6EE), Color(0xFFFFDDBF))
        else -> listOf(Color(0xFFFFFFFF), Color(0xFFF6EEFF))
    }
    val border = when {
        p.position == 1 -> Brush.sweepGradient(List(7) { i -> Color.hsv((hue * 360f + i * 60f) % 360f, 0.55f, 1f) })
        level >= 1 -> Brush.linearGradient(rowColors(p.position, hue).drop(1))
        else -> Brush.linearGradient(listOf(Ui.primary.copy(alpha = 0.4f), Ui.accent.copy(alpha = 0.4f)))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(36.dp)
            .width(280.dp)
            .background(Brush.verticalGradient(bg), RoundedCornerShape(28.dp))
            .border(if (p.position == 1) 3.dp else 2.dp, border, RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // 순위
        Text(
            if (p.position <= 3) "${medal(p.position)} " + tr("${p.position}위", "#${p.position}", zh = "第${p.position}名", ja = "${p.position}位")
            else tr("${p.position}위", "#${p.position}", zh = "第${p.position}名", ja = "${p.position}位"),
            fontSize = if (p.position == 1) 24.sp else 18.sp, fontWeight = FontWeight.Black,
            color = if (p.position == 1) Color(0xFFD68A00) else Ui.primaryDark,
        )
        Spacer(Modifier.height(6.dp))
        Box(contentAlignment = Alignment.TopCenter) {
            Critter(Side.CAT, Modifier.size(if (p.position == 1) 120.dp else 100.dp).offset(y = (bob * 4).dp), skin = p.look, happy = true)
            if (p.position == 1) {
                Text("👑", fontSize = 34.sp, modifier = Modifier.offset(y = (-20 + bob * 3).dp).rotate(bob * 8f))
            }
        }
        Spacer(Modifier.height(6.dp))
        // 닉네임 전체 (잘리지 않게 줄바꿈)
        Text(
            p.name + if (me) tr(" (나)", " (me)", zh = " (我)", es = " (yo)", fr = " (moi)", de = " (ich)", pt = " (eu)", ru = " (я)", ja = " (自分)") else "",
            fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ui.text, textAlign = TextAlign.Center, maxLines = 3,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${Country.flag(p.country)} ${Country.name(p.country).ifBlank { tr("알 수 없음", "Unknown", zh = "未知", es = "Desconocido", fr = "Inconnu", de = "Unbekannt", pt = "Desconhecido", ru = "Неизвестно", ja = "不明") }}",
            fontSize = 15.sp, color = Ui.text.copy(alpha = 0.8f), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "🐾 " + (p.look?.label ?: tr("기본 고양이", "Basic Cat", zh = "基础猫咪", es = "Gato básico", fr = "Chat de base", de = "Standardkatze", pt = "Gato básico", ru = "Обычный котик", ja = "ふつうのネコ")),
            fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        RankBadge(p.score, large = true)
        Spacer(Modifier.height(8.dp))
        Text(
            tr("${p.score}점", "${p.score} pts", zh = "${p.score} 分", es = "${p.score} pts", fr = "${p.score} pts", de = "${p.score} Pkt", pt = "${p.score} pts", ru = "${p.score} очк.", ja = "${p.score}点"),
            fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark,
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
                .background(Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)), RoundedCornerShape(50))
                .clickable(onClick = onDismiss)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tr("닫기", "Close", zh = "关闭", es = "Cerrar", fr = "Fermer", de = "Schließen", pt = "Fechar", ru = "Закрыть", ja = "閉じる"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}
