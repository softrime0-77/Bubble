package com.bubble.game.ui

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
            boom.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    val particles = remember { List(18) { Random.nextFloat() to Random.nextFloat() } }

    val click = onClick
    Box(
        Modifier
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
                side == Side.CAT -> Color(0xFFFFB86B)
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
            Text("텅…", color = Ui.text.copy(alpha = 0.45f), fontSize = 14.sp)
        }

        // 펑!
        if (boom.value < 1f) {
            val p = boom.value
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                drawCircle(Color.White.copy(alpha = (1f - p) * 0.9f), r * (0.6f + p * 0.9f), center, style = Stroke(width = (1f - p) * 14.dp.toPx()))
                particles.forEachIndexed { i, (a, s) ->
                    val ang = (i / particles.size.toFloat() + a * 0.1f) * 2 * PI
                    val dist = r * (0.4f + p * (1.0f + s * 0.8f))
                    val pos = center + Offset((cos(ang) * dist).toFloat(), (sin(ang) * dist).toFloat())
                    val color = listOf(Ui.primary, Ui.gold, Ui.accent, Color.White)[i % 4]
                    if (i % 2 == 0) {
                        sparkle(pos, (1f - p) * (6f + s * 8f) * density, color)
                    } else {
                        drawCircle(color.copy(alpha = 1f - p), (1f - p) * (3f + s * 5f) * density, pos)
                    }
                }
            }
            Text(
                "펑!", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Ui.danger,
                modifier = Modifier.scale(0.6f + p * 1.0f).alpha(1f - p),
            )
        }

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
                    .background(if (preview.contains("펑")) Ui.danger else Ui.accent, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Text(
            if (dead) "$title 💀" else "$title · ${count}마리",
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
