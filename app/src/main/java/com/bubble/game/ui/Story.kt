package com.bubble.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.R
import com.bubble.game.i18n.tr
import kotlin.math.PI
import kotlin.math.sin

/*
 * 📜 세계관: 거대한 고양이 왕국에 살던 햄스터들이 "우리 땅!"을 외치며 독립을 선언했고,
 * 그래서 고양이와 햄스터의 방울 대결이 시작됐다. 💰 대신 점수를 모아 🚩 땅을 사면 독립 엔딩.
 */

/** 꿈꾸는 밤하늘 배경 (파스텔 보라 → 분홍) + 반짝이는 별 */
@Composable
private fun DreamSky(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "sky")
    val tw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400)), label = "tw")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF8F86D9), Color(0xFFC6A8E8), Color(0xFFF6C9DE))))
            // 뒤에 있는 화면을 누르지 않게
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 40) {
                val x = ((i * 73 + 17) % 101) / 101f * size.width
                val y = ((i * 37 + 11) % 97) / 97f * size.height * 0.75f
                val a = 0.35f + 0.65f * ((sin((tw + i * 0.13f) * 2 * PI) + 1) / 2).toFloat()
                drawCircle(Color.White.copy(alpha = a), radius = if (i % 5 == 0) 4f else 2.2f, center = Offset(x, y))
            }
            // 아래쪽 구름
            val cloud = Color.White.copy(alpha = 0.55f)
            for (i in 0..6) {
                drawCircle(cloud, size.width * 0.16f, Offset(size.width * (i / 6f), size.height * 1.02f))
            }
        }
        content()
    }
}

@Composable
private fun Art(id: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(size))
}

/** 살짝 둥실둥실 */
@Composable
private fun floatY(period: Int = 1_800, amp: Float = 8f): Float {
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(-amp, amp, infiniteRepeatable(tween(period, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    return v
}

private fun prologueText(page: Int): String = when (page) {
    0 -> tr(
        "구름 위 거대한 고양이 왕국.\n작은 햄스터들도 그곳에서\n고양이들과 함께 살았어요.",
        "High above the clouds lies the great Cat Kingdom.\nLittle hamsters lived there too, alongside the cats.",
        zh = "云朵之上，有一个巨大的猫咪王国。\n小仓鼠们也和猫咪们一起住在那里。",
        ja = "雲の上の大きなネコ王国。\n小さなハムスターたちも、ネコといっしょに暮らしていました。",
    )
    1 -> tr(
        "어느 날, 햄스터들이 외쳤어요.\n\"우리도 우리 땅을 갖고 싶다! 독립이다!\"",
        "One day, the hamsters cried out:\n\"We want a land of our own! Independence!\"",
        zh = "有一天，仓鼠们大喊：\n“我们也想要自己的土地！我们要独立！”",
        ja = "ある日、ハムスターたちがさけびました。\n「ぼくらも自分の土地がほしい！独立だ！」",
    )
    2 -> tr(
        "깜짝 놀란 고양이 왕국!\n그렇게 고양이와 햄스터의\n💥 방울 대결이 시작됐어요.",
        "The Cat Kingdom was shocked!\nAnd so the 💥 bubble battle between cats and hamsters began.",
        zh = "猫咪王国大吃一惊！\n猫咪和仓鼠的 💥 泡泡对决就此开始。",
        ja = "びっくりしたネコ王国！\nこうしてネコとハムスターの💥バブル対決がはじまりました。",
    )
    else -> tr(
        "꼬마 햄스터야, 고양이들을 이기고\n점수를 모아 🚩 우리 땅을 되찾자!",
        "Little hamster, beat the cats, collect points,\nand win back 🚩 our own land!",
        zh = "小仓鼠，打败猫咪、收集分数，\n夺回 🚩 我们的土地吧！",
        ja = "ちびハムスター、ネコたちに勝ってスコアを集めて、\n🚩 ぼくらの土地を取りもどそう！",
    )
}

private const val PROLOGUE_PAGES = 4

/** 📜 프롤로그 4컷. 화면을 누르면 다음, 건너뛰기 가능. */
@Composable
fun PrologueScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onDone)
    DreamSky {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (page < PROLOGUE_PAGES - 1) page++ else onDone()
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 몇 번째 컷인지 점으로
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(PROLOGUE_PAGES) { i ->
                        Box(
                            Modifier
                                .size(if (i == page) 12.dp else 8.dp)
                                .background(Color.White.copy(alpha = if (i <= page) 1f else 0.45f), CircleShape),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) {
                    Text(tr("건너뛰기 ▶▶", "Skip ▶▶", zh = "跳过 ▶▶", ja = "スキップ ▶▶"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(250)) },
                modifier = Modifier.weight(1f),
                label = "prologue",
            ) { p ->
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                        PrologueArt(p)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        prologueText(p),
                        color = Color(0xFF4A3466),
                        fontSize = 19.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
            Button(
                onClick = { if (page < PROLOGUE_PAGES - 1) page++ else onDone() },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    if (page < PROLOGUE_PAGES - 1) tr("다음 ▶", "Next ▶", zh = "下一页 ▶", ja = "つぎ ▶")
                    else tr("🐹 독립하러 가자!", "🐹 Let's go!", zh = "🐹 出发吧！", ja = "🐹 独立しにいこう！"),
                    fontSize = 18.sp, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun PrologueArt(page: Int) {
    val y = floatY()
    when (page) {
        // 1컷: 커다란 고양이 왕과 그 앞의 작은 햄스터들
        0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("👑", fontSize = 40.sp, modifier = Modifier.offset(y = (-138).dp + y.dp))
            Art(R.drawable.art_cat_idle, 250.dp, Modifier.offset(y = (-10).dp + y.dp))
            Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                Art(R.drawable.art_ham_idle, 74.dp, Modifier.offset(y = (-y * 0.6f).dp))
                Art(R.drawable.art_ham_idle, 84.dp, Modifier.offset(y = (y * 0.6f).dp))
                Art(R.drawable.art_ham_idle, 74.dp, Modifier.offset(y = (-y * 0.6f).dp))
            }
        }
        // 2컷: 햄스터들의 외침 + 깃발
        1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy((-30).dp), verticalAlignment = Alignment.Bottom) {
                Art(R.drawable.art_ham_happy, 120.dp, Modifier.offset(y = (y * 0.7f).dp))
                Art(R.drawable.art_ham_surprised, 170.dp, Modifier.offset(y = (-y).dp))
                Art(R.drawable.art_ham_happy, 120.dp, Modifier.offset(y = (y * 0.7f).dp))
            }
            WavingFlag(Modifier.size(90.dp).align(Alignment.TopCenter).offset(x = 70.dp, y = 6.dp))
        }
        // 3컷: 고양이 vs 햄스터, 💥
        2 -> {
            val t = rememberInfiniteTransition(label = "clash")
            val pulse by t.animateFloat(0.85f, 1.2f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "p")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(R.drawable.art_cat_surprised, 160.dp, Modifier.offset(x = 10.dp, y = y.dp))
                    Art(R.drawable.art_ham_surprised, 160.dp, Modifier.offset(x = (-10).dp, y = (-y).dp))
                }
                Text("💥", fontSize = 56.sp, modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse })
            }
        }
        // 4컷: 플레이어 햄스터
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Art(R.drawable.art_ham_happy, 260.dp, Modifier.offset(y = y.dp))
            WavingFlag(Modifier.size(84.dp).align(Alignment.TopEnd).offset(x = (-24).dp, y = 10.dp))
        }
    }
}

/** 🚩 펄럭이는 햄스터 깃발 (분홍 천에 노란 별) */
@Composable
fun WavingFlag(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "flag")
    val ph by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1_100)), label = "ph")
    Canvas(modifier) { drawFlag(ph) }
}

private fun DrawScope.drawFlag(phase: Float) {
    val w = size.width
    val h = size.height
    val poleX = w * 0.18f
    // 깃대
    drawRoundRect(Color(0xFF9C6B3E), Offset(poleX - w * 0.03f, h * 0.06f), Size(w * 0.06f, h * 0.92f), androidx.compose.ui.geometry.CornerRadius(6f))
    drawCircle(Color(0xFFFFD34D), w * 0.06f, Offset(poleX, h * 0.06f))
    // 천: 물결치게
    val top = h * 0.1f
    val fh = h * 0.42f
    val fw = w * 0.74f
    val path = Path()
    val steps = 16
    path.moveTo(poleX, top)
    for (i in 1..steps) {
        val x = poleX + fw * i / steps
        val wave = sin(phase + i * 0.55f) * h * 0.035f * (i / steps.toFloat())
        path.lineTo(x, top + wave)
    }
    for (i in steps downTo 0) {
        val x = poleX + fw * i / steps
        val wave = sin(phase + i * 0.55f) * h * 0.035f * (i / steps.toFloat())
        path.lineTo(x, top + fh + wave)
    }
    path.close()
    drawPath(path, Brush.horizontalGradient(listOf(Color(0xFFFF8FB8), Color(0xFFFFB3CF)), startX = poleX, endX = poleX + fw))
    // 별
    val cx = poleX + fw * 0.5f
    val cy = top + fh * 0.5f + sin(phase + 8 * 0.55f) * h * 0.017f
    val star = Path()
    val ro = fh * 0.3f
    val ri = ro * 0.45f
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) ro else ri
        val a = -PI / 2 + k * PI / 5
        val px = cx + (r * kotlin.math.cos(a)).toFloat()
        val py = cy + (r * sin(a)).toFloat()
        if (k == 0) star.moveTo(px, py) else star.lineTo(px, py)
    }
    star.close()
    drawPath(star, Color(0xFFFFE066))
}

/** 🚩 땅을 사면 나오는 독립 엔딩 컷. 누르면 닫힘. */
@Composable
fun LandEnding(name: String?, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    // 깃대가 땅에 쿵 꽂히고, 글자가 차례로 나타남
    val plant = remember { Animatable(-300f) }
    val text1 = remember { Animatable(0f) }
    val text2 = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        plant.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f))
        text1.animateTo(1f, tween(600))
        text2.animateTo(1f, tween(600))
    }
    val y = floatY(amp = 5f)
    DreamSky {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                tr("🎉 THE END 🎉", "🎉 THE END 🎉"),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.alpha(text2.value),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(320.dp), contentAlignment = Alignment.BottomCenter) {
                // 작은 땅 언덕
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    drawOval(Color(0xFF9BD58A), Offset(0f, size.height * 0.25f), Size(size.width, size.height * 1.3f))
                    drawOval(Color(0xFFB9E7A5), Offset(size.width * 0.08f, size.height * 0.3f), Size(size.width * 0.84f, size.height * 0.6f))
                    // 작은 꽃들
                    val flowers = listOf(0.15f to 0.62f, 0.3f to 0.48f, 0.72f to 0.52f, 0.86f to 0.66f)
                    for ((fx, fy) in flowers) {
                        drawCircle(Color(0xFFFFB3CF), 7f, Offset(size.width * fx, size.height * fy))
                        drawCircle(Color(0xFFFFE066), 3f, Offset(size.width * fx, size.height * fy))
                    }
                }
                WavingFlag(
                    Modifier
                        .size(150.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = (plant.value + 10).dp),
                )
                Art(R.drawable.art_ham_happy, 220.dp, Modifier.align(Alignment.BottomStart).offset(x = 4.dp, y = (-26 + y).dp))
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tr("드디어… 우리 땅이다!", "At last… our very own land!", zh = "终于……这是我们的土地！", ja = "ついに…ぼくらの土地だ！"),
                    fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF4A3466),
                    modifier = Modifier.alpha(text1.value), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "${name ?: "꼬마 햄스터"}의 활약으로\n햄스터들이 고양이 왕국에서 독립했어요! 🚩\n칭호 '독립 햄스터'를 받았고,\n랭킹에 깃발이 휘날려요.",
                        "Thanks to ${name ?: "you"}, the hamsters are free from the Cat Kingdom! 🚩\nYou earned the title 'Free Hamster', and your flag now flies on the ranking.",
                        zh = "多亏了${name ?: "你"}，仓鼠们从猫咪王国独立了！🚩\n你获得了称号“独立仓鼠”，排行榜上也会飘扬你的旗帜。",
                        ja = "${name ?: "きみ"}の活躍で、ハムスターたちはネコ王国から独立しました！🚩\n称号「独立ハムスター」をゲット、ランキングに旗がはためきます。",
                    ),
                    fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFF4A3466), textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(text2.value),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("…그런데 고양이들이 가만히 있을까? 🐱", "…but will the cats just let it go? 🐱", zh = "……不过猫咪们会就此罢休吗？🐱", ja = "…でも、ネコたちがだまっているかな？🐱"),
                    fontSize = 13.sp, color = Color(0xFF8A6FA8), modifier = Modifier.alpha(text2.value),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(tr("화면을 누르면 닫혀요", "Tap to close", zh = "点击关闭", ja = "タップでとじる"), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/** "🚩 독립 햄스터" 칭호 배지 */
@Composable
fun IndependentBadge(modifier: Modifier = Modifier) {
    Text(
        tr("🚩 독립 햄스터", "🚩 Free Hamster", zh = "🚩 独立仓鼠", ja = "🚩 独立ハムスター"),
        fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
        modifier = modifier
            .background(Brush.horizontalGradient(listOf(Color(0xFFFF7FAE), Color(0xFFB58CFF))), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 상점의 땅 그림: 작은 언덕 위 깃발 */
@Composable
fun LandArt(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            drawOval(Color(0xFF9BD58A), Offset(0f, size.height * 0.2f), Size(size.width, size.height * 1.4f))
        }
        WavingFlag(Modifier.size(64.dp).align(Alignment.TopCenter).offset(x = 8.dp, y = (-2).dp))
        Art(R.drawable.art_ham_idle, 44.dp, Modifier.align(Alignment.BottomStart).offset(x = 2.dp, y = (-4).dp))
    }
}
