package com.bubble.game.ui

import com.bubble.game.i18n.tr
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.bubble.game.i18n.Lang

import android.app.Activity
import com.bubble.game.ads.AdManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import com.bubble.game.game.Shop
import com.bubble.game.game.Rank
import com.bubble.game.game.Animal
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.data.OnlineRepository
import com.bubble.game.game.AiLevel
import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import kotlinx.coroutines.delay

@Composable
fun BubbleApp(vm: AppViewModel) {
    val screen by vm.screen.collectAsState()
    BubbleBackground {
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Lobby -> LobbyScreen(vm)
                is Screen.Matching -> MatchingScreen(s.startedAt, onCancel = vm::cancelMatching)
                Screen.Game -> GameScreen(vm)
                Screen.Shop -> ShopScreen(vm)
            }
        }
        val lobby by vm.lobby.collectAsState()
        val rankUp = lobby.rankUpScore
        if (rankUp != null && screen == Screen.Lobby) RankUpCelebration(rankUp, onDismiss = vm::dismissRankUp)
    }
}

// ======================= 로비 (대기방) =======================

@Composable
private fun LobbyScreen(vm: AppViewModel) {
    val ui by vm.lobby.collectAsState()
    var showRules by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var showRanks by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LanguageMenu()
            Spacer(Modifier.weight(1f))
            if (ui.profile != null) ShopIconButton(onClick = vm::openShop)
        }
        TitleBanner()
        Spacer(Modifier.height(16.dp))

        SoftCard {
            when {
                !ui.onlineAvailable -> Text(
                    tr("온라인 대전은 Firebase 설정(google-services.json)을 넣고 다시 빌드하면 열려요.\n지금은 AI 연습을 즐겨 주세요!", "Online play opens once Firebase (google-services.json) is added and the app is rebuilt.\nEnjoy AI practice for now!"),
                    color = Ui.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                ui.needsLogin && !ui.loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("구글 계정으로 로그인하면\n점수와 전적이 계정에 저장돼요", "Sign in with Google to save\nyour score and record"), color = Ui.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(tr("상대에게는 구글 이름 대신 닉네임만 보여요", "Opponents only see your nickname, not your Google name"), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    val activity = LocalContext.current
                    Button(
                        onClick = { vm.signInWithGoogle(activity) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ui.text),
                        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("G", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF4285F4))
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Google로 시작하기", "Continue with Google"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (ui.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.error!!, color = Ui.danger, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                ui.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Ui.primary, strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("서버에 접속하는 중…", "Connecting to server…"), color = Ui.text)
                }
                ui.profile != null -> {
                    val p = ui.profile!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Critter(Side.CAT, Modifier.size(52.dp), skin = p.equipped)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ui.text)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showRanks = true },
                            ) {
                                RankBadge(p.score)
                                Text(tr("  계급표 ›", "  Ranks ›"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ui.accent)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("${p.wins}승 ${p.losses}패 · 💖 부활물약 ${p.potions}개", "${p.wins}W ${p.losses}L · 💖 Potions ${p.potions}"),
                                fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${p.score}", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ui.primaryDark)
                            Text(tr("점", "pts"), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f))
                        }
                    }
                    Row {
                        TextButton(onClick = { renaming = true }) { Text(tr("✏️ 닉네임 바꾸기", "✏️ Change nickname"), color = Ui.accent) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::signOut) { Text(tr("로그아웃", "Sign out"), color = Ui.text.copy(alpha = 0.6f)) }
                    }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error ?: tr("접속하지 못했어요", "Could not connect"), color = Ui.danger, textAlign = TextAlign.Center)
                    TextButton(onClick = vm::connect) { Text(tr("다시 시도", "Retry")) }
                }
            }
            if (ui.error != null && ui.profile != null) {
                Text(ui.error!!, color = Ui.danger, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        BigButton(
            text = tr("🎲  랜덤 매칭", "🎲  Random Match"),
            sub = tr("익명의 상대와 1:1 대결 · 승리 +${OnlineRepository.WIN_POINTS}점", "1:1 vs an anonymous player · Win +${OnlineRepository.WIN_POINTS} pts"),
            enabled = ui.onlineAvailable && ui.profile != null && !ui.needsLogin,
            onClick = vm::startMatching,
        )
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text(if (ui.profile != null) tr("🤖 AI 대전", "🤖 AI Battle") else tr("🤖 AI와 연습하기", "🤖 Practice with AI"), fontWeight = FontWeight.Bold, color = Ui.text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiLevel.entries.forEach { lv ->
                    OutlinedButton(
                        onClick = { vm.startAiGame(lv) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(lv.label, color = Ui.accent) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (ui.profile != null) {
                    AiLevel.entries.joinToString("  ·  ") { tr("${it.label} 승 +${it.winPoints} / 패 ${it.losePoints}", "${it.label} W +${it.winPoints} / L ${it.losePoints}") }
                } else {
                    tr("로그인하면 AI 대전도 점수가 걸려요", "Sign in to earn points in AI battles too")
                },
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ui.text.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = { showRules = true }) { Text(tr("📖 게임 규칙 보기", "📖 How to play"), color = Ui.text) }
        ui.info?.let {
            Text(it, color = Ui.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        val privacyRequired by AdManager.privacyOptionsRequired.collectAsState()
        if (privacyRequired) {
            val activity = LocalContext.current as Activity
            TextButton(onClick = { AdManager.showPrivacyOptions(activity) }) {
                Text(tr("🔒 광고 개인정보 설정", "🔒 Ad privacy settings"), color = Ui.text.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }

        if (ui.leaderboard.isNotEmpty()) {
            SoftCard {
                Text(tr("🏆 랭킹", "🏆 Leaderboard"), fontWeight = FontWeight.Bold, color = Ui.text)
                Spacer(Modifier.height(6.dp))
                ui.leaderboard.forEachIndexed { i, p ->
                    val me = p.uid == ui.profile?.uid
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (me) Ui.primary.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" },
                            modifier = Modifier.width(32.dp), color = Ui.text, fontWeight = FontWeight.Bold,
                        )
                        Text(p.name + if (me) tr(" (나)", " (me)") else "", modifier = Modifier.weight(1f), color = Ui.text)
                        RankBadge(p.score)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("${p.score}점", "${p.score} pts"), color = Ui.primaryDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showRules) RulesDialog { showRules = false }
    if (showRanks) RanksDialog(ui.profile?.score ?: 0) { showRanks = false }
    if (renaming) {
        var name by remember { mutableStateOf(ui.profile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("닉네임", "Nickname")) },
            text = {
                OutlinedTextField(name, { name = it.take(12) }, singleLine = true, label = { Text(tr("최대 12자", "Up to 12 characters")) })
            },
            confirmButton = { TextButton(onClick = { vm.rename(name); renaming = false }) { Text(tr("저장", "Save")) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(tr("취소", "Cancel")) } },
        )
    }
}

@Composable
private fun TitleBanner() {
    val t = rememberInfiniteTransition(label = "title")
    val hop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "hop")
    Row(verticalAlignment = Alignment.Bottom) {
        Critter(Side.CAT, Modifier.size(64.dp).offset(y = (-hop * 10).dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text("Bubble", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
            Text(tr("냥냥 vs 햄햄 방울 대전", "Cats vs Hamsters bubble battle"), fontSize = 14.sp, color = Ui.text)
        }
        Critter(Side.HAMSTER, Modifier.size(64.dp).offset(y = (-(1 - hop) * 10).dp))
    }
}

@Composable
private fun SoftCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Ui.card),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun BigButton(text: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "big")
    val s by t.animateFloat(1f, 1.04f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "s")
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
        modifier = Modifier.fillMaxWidth().height(76.dp).scale(if (enabled) s else 1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("알겠어요!", "Got it!")) } },
        title = { Text(tr("게임 규칙", "How to play"), fontWeight = FontWeight.Black, fontSize = 24.sp) },
        text = {
            Text(
                tr("• 고양이와 햄스터가 각자 방울(세트) 2개에 1마리씩 들고 시작해요. 고양이가 먼저!\n\n", "• Cats and hamsters each start with 2 bubbles (sets) holding 1 critter. Cats go first!\n\n") +
                    tr("• 내 차례에는 내 방울 하나를 골라요. 그 방울의 마리 수만큼\n", "• On your turn, pick one of your bubbles. Add its critter count\n") +
                    tr("   ⚔️ 상대 방울에 더하거나 (공격)\n", "   ⚔️ to an opponent bubble (attack)\n") +
                    tr("   💪 내 다른 방울에 더할 수 있어요 (강화)\n\n", "   💪 or to your other bubble (boost)\n\n") +
                    tr("• 방울이 5마리 이상이 되면 💥 펑! 터져서 0마리가 돼요.\n\n", "• A bubble with 5 or more critters goes 💥 POP! and drops to 0.\n\n") +
                    tr("• 0마리 방울은 공격도 강화도 못 해요. 살리는 방법은 분할뿐!\n", "• An empty bubble can’t be attacked or boosted. Only a split revives it!\n") +
                    tr("   ✂️ 분할: 한 방울이 짝수(2·4마리)이고 다른 방울이 0일 때 반씩 나눠요.\n", "   ✂️ Split: if one bubble has an even count (2 or 4) and the other is empty, halve it.\n") +
                    tr("   ✂️ 분할은 한 게임에 한 사람당 최대 ${GameLogic.MAX_SPLITS}번까지!\n\n", "   ✂️ Up to ${GameLogic.MAX_SPLITS} splits per player per game!\n\n") +
                    tr("• 내 방울 2개가 모두 터지면 져요.\n\n", "• Lose both bubbles and you lose.\n\n") +
                    tr("• 💖 부활물약(상점)이 있으면 터진 방울을 1마리로 살릴 수 있어요. 한 판에 최대 3번!\n\n", "• 💖 Revive potions (shop) bring a popped bubble back with 1 critter. Up to 3 per game!\n\n") +
                    tr("• 온라인 대전은 한 턴에 ${OnlineRepository.TURN_LIMIT_MS / 1000}초! 시간을 넘기면 패배예요.\n", "• Online turns last ${OnlineRepository.TURN_LIMIT_MS / 1000}s. Run out of time and you lose.\n") +
                    tr("   상대가 양·강아지·기린을 장착했다면 내 턴이 20·15·10초로 줄어요.\n\n", "   If your opponent equips a sheep, puppy or giraffe, your turn drops to 20, 15 or 10s.\n\n") +
                    tr("• 계급: 1000점 이하 흙, 이후 100점마다 구리 → 은 → 금 → 다이아", "• Ranks: Dirt up to 1000 pts, then every 100 pts Copper → Silver → Gold → Diamond"),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ui.text,
            )
        },
    )
}

// ======================= 매칭 중 =======================

@Composable
private fun MatchingScreen(startedAt: Long, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    val sec = ((now - startedAt) / 1000).toInt()
    val t = rememberInfiniteTransition(label = "match")
    val swing by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "swing")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Critter(Side.CAT, Modifier.size(110.dp).offset(x = (swing * 14).dp, y = (-kotlin.math.abs(swing) * 12).dp))
            Text("VS", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ui.accent, modifier = Modifier.padding(horizontal = 12.dp))
            Critter(Side.HAMSTER, Modifier.size(110.dp).offset(x = (-swing * 14).dp, y = (-kotlin.math.abs(swing) * 12).dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(tr("상대를 찾고 있어요", "Looking for an opponent") + ".".repeat(sec % 4), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ui.text)
        Spacer(Modifier.height(6.dp))
        Text("%d:%02d".format(sec / 60, sec % 60), fontSize = 16.sp, color = Ui.text.copy(alpha = 0.7f))
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) { Text(tr("매칭 취소", "Cancel"), color = Ui.text) }
    }
}

// ======================= 게임 =======================

@Composable
private fun GameScreen(vm: AppViewModel) {
    val g by vm.game.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    // 차례가 바뀌거나 판이 바뀌면 선택 해제
    LaunchedEffect(g.turn, g.board) {
        val s = selected
        if (!g.myTurn || (s != null && g.board.sets(g.mySide)[s] == 0)) selected = null
    }
    BackHandler { if (g.finished) vm.leaveGame() else confirmLeave = true }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(250); now = System.currentTimeMillis() } }
    val remain = g.turnDeadline?.let { ((it - now) / 1000).coerceAtLeast(0) }

    val me = g.mySide
    val opp = me.opponent
    val mine = g.board.sets(me)
    val theirs = g.board.sets(opp)
    val sel = selected
    val power = sel?.let { mine[it] } ?: 0

    fun previewFor(target: Int) = if (target + power >= GameLogic.EXPLODE_AT) tr("💥 펑!", "💥 POP!") else "→ ${target + power}"

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 상단 바
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (g.finished) vm.leaveGame() else confirmLeave = true }) {
                    Text(tr("← 나가기", "← Leave"), color = Ui.text)
                }
                Spacer(Modifier.weight(1f))
                if (remain != null && !g.finished) {
                    Text(
                        tr("⏱ ${remain}초", "⏱ ${remain}s"),
                        color = if (remain <= 10) Ui.danger else Ui.text,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTag(opp, g.opponentName, g.opponentScore?.let { tr("${it}점", "${it} pts") }, active = g.turn == opp && !g.finished, skin = g.opponentSkin)
                Spacer(Modifier.width(8.dp))
                SplitCounter(g.board.splitsLeft(opp))
                ReviveCounter(g.board.revivesLeft(opp))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until GameLogic.SET_COUNT) {
                    val target = sel != null && theirs[i] > 0
                    SetBubble(
                        side = opp,
                        count = theirs[i],
                        title = GameLogic.setName(i),
                        mode = if (target) BubbleMode.TARGET else BubbleMode.NORMAL,
                        size = 140.dp,
                        skin = g.opponentSkin,
                        preview = if (target) previewFor(theirs[i]) else null,
                        explodeKey = g.explosion?.takeIf { it.side == opp && it.index == i }?.id,
                        onClick = if (target) ({ vm.play(Move.Add(sel!!, true, i)); selected = null }) else null,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            TurnBanner(g, sel != null)
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until GameLogic.SET_COUNT) {
                    val isSel = sel == i
                    val boostTarget = sel != null && sel != i && mine[i] > 0
                    val pickable = g.myTurn && mine[i] > 0
                    SetBubble(
                        side = me,
                        count = mine[i],
                        title = GameLogic.setName(i),
                        mode = when {
                            isSel -> BubbleMode.SELECTED
                            boostTarget -> BubbleMode.TARGET
                            !pickable -> BubbleMode.DISABLED
                            else -> BubbleMode.NORMAL
                        },
                        size = 150.dp,
                        skin = g.mySkin,
                        preview = if (boostTarget) "💪 " + previewFor(mine[i]) else null,
                        explodeKey = g.explosion?.takeIf { it.side == me && it.index == i }?.id,
                        onClick = when {
                            boostTarget -> ({ vm.play(Move.Add(sel!!, false, i)); selected = null })
                            pickable -> ({ selected = if (isSel) null else i })
                            else -> null
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTag(me, if (g.myName == tr("나", "Me")) tr("나", "Me") else g.myName + tr(" (나)", " (me)"), null, active = g.myTurn, skin = g.mySkin)
                Spacer(Modifier.width(8.dp))
                SplitCounter(g.board.splitsLeft(me))
                ReviveCounter(g.board.revivesLeft(me))
            }

            // 분할 모양이 되면 버튼을 보여주고, 횟수를 다 썼으면 비활성화
            val splitsLeft = g.board.splitsLeft(me)
            val splitShape = sel != null && GameLogic.splitShapeOk(g.board, me, sel)
            AnimatedVisibility(splitShape, enter = fadeIn() + slideInVertically { it }) {
                Button(
                    onClick = { vm.play(Move.Split(sel!!)); selected = null },
                    enabled = splitsLeft > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(
                        if (splitsLeft > 0) tr("✂️ 분할하기 (${power / 2} + ${power / 2}) · 남은 ${splitsLeft}회", "✂️ Split (${power / 2} + ${power / 2}) · ${splitsLeft} left")
                        else tr("✂️ 분할 횟수를 모두 썼어요", "✂️ No splits left"),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
            }
            // 부활물약: 터진 방울이 있고 물약이 남았으면 (선택 없이 바로 사용)
            val reviveTarget = (0 until GameLogic.SET_COUNT).firstOrNull { GameLogic.canRevive(g.board, me, it) }
            AnimatedVisibility(g.myTurn && reviveTarget != null, enter = fadeIn() + slideInVertically { it }) {
                Button(
                    onClick = { reviveTarget?.let { vm.play(Move.Revive(it)) }; selected = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C8A)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        tr("💖 부활물약으로 ${GameLogic.setName(reviveTarget ?: 0)} 살리기 · 남은 ${g.board.revivesLeft(me)}개", "💖 Revive ${GameLogic.setName(reviveTarget ?: 0)} · ${g.board.revivesLeft(me)} left"),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            g.notice?.let {
                Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ui.danger, textAlign = TextAlign.Center)
            }
            g.log.take(2).forEachIndexed { i, line ->
                Text(
                    line,
                    fontSize = if (i == 0) 16.sp else 14.sp,
                    fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = Ui.text.copy(alpha = if (i == 0) 1f else 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (g.finished && g.winner != null) {
            val activity = LocalContext.current as Activity
            val rewardedReady by AdManager.rewardedReady.collectAsState()
            var showRewardIntro by remember { mutableStateOf(false) }
            val lostOnline = g.online && g.winner != g.mySide && (g.scoreDelta ?: 0) < 0
            val canOfferReward = lostOnline && rewardedReady && vm.rewardTarget() != null
            ResultOverlay(
                g,
                rewardOffer = if (canOfferReward) minOf(OnlineRepository.AD_REFUND_POINTS, -(g.scoreDelta ?: 0)) else null,
                onLobby = {
                    if (canOfferReward) {
                        showRewardIntro = true
                    } else {
                        AdManager.maybeShowInterstitial(activity) { vm.leaveGame() }
                    }
                },
                onAgain = {
                    val lv = g.aiLevel ?: AiLevel.NORMAL
                    AdManager.maybeShowInterstitial(activity) { vm.startAiGame(lv) }
                },
            )
            if (showRewardIntro) {
                RewardIntro(
                    points = minOf(OnlineRepository.AD_REFUND_POINTS, -(g.scoreDelta ?: 0)),
                    onSkip = { showRewardIntro = false; vm.leaveGame() },
                    onWatch = {
                        showRewardIntro = false
                        val target = vm.rewardTarget()
                        if (target == null) {
                            vm.leaveGame()
                        } else {
                            AdManager.showRewarded(
                                activity, target.first, target.second,
                                onEarned = vm::onAdRewardEarned,
                                onDone = vm::leaveGame,
                            )
                        }
                    },
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(tr("게임에서 나갈까요?", "Leave the game?")) },
            text = {
                Text(
                    when {
                        g.scoredAi -> tr("지금 나가면 패배로 처리돼요 (${g.aiLevel!!.losePoints}점).", "Leaving now counts as a loss (${g.aiLevel!!.losePoints} pts).")
                        g.online -> tr("지금 나가면 기권패로 처리돼요 (-${OnlineRepository.LOSE_POINTS}점).", "Leaving now counts as a forfeit (-${OnlineRepository.LOSE_POINTS} pts).")
                        else -> tr("연습 게임이 끝나요.", "This practice game will end.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveGame() }) { Text(tr("나가기", "Leave"), color = Ui.danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("계속하기", "Keep playing")) } },
        )
    }
}

@Composable
private fun PlayerTag(side: Side, name: String, extra: String?, active: Boolean, skin: Animal? = null) {
    val t = rememberInfiniteTransition(label = "tag")
    val glow by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    Row(
        Modifier
            .background(
                if (active) Ui.gold.copy(alpha = 0.35f + glow * 0.4f) else Color.White.copy(alpha = 0.6f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Critter(side, Modifier.size(26.dp), skin = skin)
        Spacer(Modifier.width(6.dp))
        Text("${skin?.label ?: side.label} · $name", color = Ui.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (extra != null) Text("  $extra", color = Ui.primaryDark, fontSize = 13.sp)
    }
}

@Composable
private fun TurnBanner(g: GameUi, selecting: Boolean) {
    val text = when {
        g.finished -> tr("게임 끝!", "Game over!")
        g.busy && g.turn == g.mySide -> tr("보내는 중…", "Sending…")
        g.turn != g.mySide -> if (g.aiLevel != null) tr("AI가 생각 중… 🤔", "AI is thinking… 🤔") else tr("상대가 고민 중이에요…", "Opponent is thinking…")
        selecting -> tr("어디에 더할까요? 반짝이는 방울을 눌러요", "Where to add? Tap a glowing bubble")
        else -> tr("내 차례! 내 방울을 하나 골라요", "Your turn! Pick one of your bubbles")
    }
    AnimatedContent(text, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() }, label = "turn") {
        Text(
            it, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp,
            color = if (g.turn == g.mySide && !g.finished) Ui.primaryDark else Ui.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ResultOverlay(g: GameUi, rewardOffer: Int?, onLobby: () -> Unit, onAgain: () -> Unit) {
    val won = g.winner == g.mySide
    val t = rememberInfiniteTransition(label = "result")
    val jump by t.animateFloat(0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "jump")
    Box(
        Modifier.fillMaxSize().background(Color(0x88302040)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(32.dp),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Critter(
                    g.mySide, Modifier.size(120.dp).offset(y = (if (won) -jump * 16 else 0f).dp),
                    happy = won,
                )
                Text(
                    if (won) tr("승리! 🎉", "You win! 🎉") else tr("아쉬운 패배…", "So close…"),
                    fontSize = 32.sp, fontWeight = FontWeight.Black,
                    color = if (won) Ui.primaryDark else Ui.text,
                )
                val reason = when (g.reason) {
                    "forfeit" -> if (won) tr("상대가 나갔어요", "Your opponent left") else tr("기권했어요", "You forfeited")
                    "timeout" -> if (won) tr("상대가 시간을 넘겼어요", "Your opponent ran out of time") else tr("시간을 넘겼어요", "You ran out of time")
                    else -> if (won) tr("상대 방울을 모두 터뜨렸어요!", "You popped all their bubbles!") else tr("내 방울이 모두 터졌어요", "All your bubbles popped")
                }
                Text(reason, color = Ui.text.copy(alpha = 0.7f))
                if (g.online) {
                    Spacer(Modifier.height(10.dp))
                    val d = g.scoreDelta
                    Text(
                        when {
                            d == null -> tr("점수 계산 중…", "Calculating score…")
                            d >= 0 -> tr("+$d 점", "+$d pts")
                            else -> tr("$d 점", "$d pts")
                        },
                        fontSize = 26.sp, fontWeight = FontWeight.Black,
                        color = if ((d ?: 0) >= 0) Ui.accent else Ui.danger,
                    )
                    if (g.scoreBonus > 0) {
                        Text(
                            tr("✨ 계급 보너스 +${g.scoreBonus}점 포함", "✨ Includes rank bonus +${g.scoreBonus}"),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.gold.copy(red = 0.85f, green = 0.6f, blue = 0f),
                        )
                    }
                    if (rewardOffer != null) {
                        Text(
                            tr("📺 로비로 갈 때 광고를 보면 ${rewardOffer}점을 돌려받아요", "📺 Watch an ad on the way out to get ${rewardOffer} pts back"),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ui.accent,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (g.aiLevel != null) {
                        OutlinedButton(onClick = onAgain, shape = RoundedCornerShape(18.dp)) { Text(tr("한 판 더", "Play again")) }
                    }
                    Button(
                        onClick = onLobby, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                    ) { Text(tr("로비로", "Lobby")) }
                }
            }
        }
    }
}

/** 남은 분할 횟수. 줄어들면 "-1"이 떠오르며 사라집니다. */
@Composable
private fun SplitCounter(left: Int) {
    var previous by remember { mutableStateOf(left) }
    val drop = remember { Animatable(1f) }
    val bump = remember { Animatable(1f) }
    var lost by remember { mutableStateOf(0) }
    LaunchedEffect(left) {
        if (left < previous) {
            lost = previous - left
            launch {
                bump.snapTo(1.35f)
                bump.animateTo(1f, spring(dampingRatio = 0.35f))
            }
            drop.snapTo(0f)
            drop.animateTo(1f, tween(1_400))
        }
        previous = left
    }
    Box(contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .scale(bump.value)
                .background(
                    if (left == 0) Color(0xFFE0E0E0) else Ui.accent.copy(alpha = 0.18f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✂️", fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            repeat(GameLogic.MAX_SPLITS) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .size(9.dp)
                        .background(if (i < left) Ui.accent else Color(0xFFBDBDBD), RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                "$left/${GameLogic.MAX_SPLITS}",
                fontWeight = FontWeight.Black, fontSize = 14.sp,
                color = if (left == 0) Color(0xFF9E9E9E) else Ui.accent,
            )
        }
        if (drop.value < 1f) {
            Text(
                "-$lost",
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ui.danger,
                modifier = Modifier
                    .offset(y = (-18 - drop.value * 26).dp)
                    .scale(1f + (1f - drop.value) * 0.4f)
                    .alpha(1f - drop.value),
            )
        }
    }
}

/**
 * 보상형 전면 광고 안내 화면 (AdMob 정책: 광고 전에 보상 내용을 알리고 건너뛸 수 있어야 함).
 * 5초 뒤 자동으로 광고가 시작되며, 언제든 건너뛸 수 있습니다.
 */
@Composable
private fun RewardIntro(points: Int, onSkip: () -> Unit, onWatch: () -> Unit) {
    var remain by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (remain > 0) {
            delay(1_000)
            remain--
        }
        onWatch()
    }
    BackHandler(onBack = onSkip)
    Box(
        Modifier.fillMaxSize().background(Color(0xCC201530)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(28.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📺", fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("광고를 보면\n잃은 점수 중 ${points}점을 돌려드려요", "Watch an ad to get\n${points} of your lost points back"),
                    fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ui.text,
                    textAlign = TextAlign.Center, lineHeight = 28.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("${remain}초 후 광고가 시작돼요", "Ad starts in ${remain}s"),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ui.accent,
                )
                Text(
                    tr("광고를 끝까지 봐야 점수가 돌아와요", "Watch to the end to get your points back"),
                    fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onSkip, shape = RoundedCornerShape(18.dp)) {
                        Text(tr("괜찮아요 (건너뛰기)", "No thanks (skip)"), color = Ui.text)
                    }
                    Button(
                        onClick = onWatch, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    ) { Text(tr("지금 보기", "Watch now"), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ======================= 상점 =======================

@Composable
private fun ShopIconButton(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "shopIcon")
    val bob by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "bob")
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ui.gold, contentColor = Ui.text),
        modifier = Modifier.offset(y = bob.dp),
    ) {
        Text(tr("🛒 상점", "🛒 Shop"), fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
private fun ShopScreen(vm: AppViewModel) {
    val ui by vm.lobby.collectAsState()
    val p = ui.profile
    BackHandler(onBack = vm::closeShop)
    var confirm by remember { mutableStateOf<Triple<String, String, Int>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeShop) { Text(tr("← 로비", "← Lobby"), color = Ui.text) }
            Spacer(Modifier.weight(1f))
            if (p != null) {
                Text(tr("내 점수 ", "My score "), color = Ui.text, fontSize = 14.sp)
                Text(tr("${p.score}점", "${p.score} pts"), color = Ui.primaryDark, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
        Text(tr("🛒 상점", "🛒 Shop"), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
        Text(tr("점수로 아이템을 살 수 있어요 (점수가 줄면 계급도 내려갈 수 있어요)", "Spend points on items (your rank can drop if your score does)"), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        ui.info?.let { Text(it, color = Ui.accent, fontWeight = FontWeight.Bold) }
        ui.error?.let { Text(it, color = Ui.danger, fontWeight = FontWeight.Bold) }
        if (p == null) return@Column

        // 부활물약
        ShopCard(
            title = tr("💖 부활물약", "💖 Revive Potion"),
            desc = tr("내 차례에 터진(0마리) 방울 하나를 1마리로 살려요.\n한 판에 최대 ${Shop.MAX_REVIVES_PER_GAME}번 · 쓸 때마다 1개 소모 · 온라인 대전 전용", "On your turn, bring a popped (empty) bubble back with 1 critter.\nUp to ${Shop.MAX_REVIVES_PER_GAME} per game · 1 used each time · online only"),
            price = Shop.POTION_PRICE,
            owned = tr("보유 ${p.potions}개", "Owned: ${p.potions}"),
            art = { PotionArt(Modifier.size(84.dp)) },
        ) {
            Button(
                onClick = { confirm = Triple("POTION", tr("부활물약", "Revive Potion"), Shop.POTION_PRICE) },
                enabled = !ui.shopBusy && p.score >= Shop.POTION_PRICE,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
            ) { Text(tr("${Shop.POTION_PRICE}점에 사기", "Buy for ${Shop.POTION_PRICE} pts"), fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(6.dp))
        Text(tr("🐾 동물 친구들", "🐾 Animal Friends"), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ui.text, modifier = Modifier.fillMaxWidth())
        Text(tr("장착하면 내 캐릭터가 바뀌고, 상대의 턴 시간이 줄어들어요 (다음 판부터)", "Equip to change your character and shorten your opponent’s turn (from the next game)"), fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))

        Animal.entries.forEach { a ->
            val have = a in p.owned
            val on = p.equipped == a
            ShopCard(
                title = a.label,
                desc = tr("⏱ 상대 턴 시간 ${Shop.DEFAULT_TURN_SEC}초 → ${a.opponentTurnSec}초", "⏱ Opponent turn ${Shop.DEFAULT_TURN_SEC}s → ${a.opponentTurnSec}s"),
                price = a.price,
                owned = when {
                    on -> tr("✅ 장착 중", "✅ Equipped")
                    have -> tr("보유 중", "Owned")
                    else -> null
                },
                art = { Critter(Side.CAT, Modifier.size(84.dp), skin = a, happy = on) },
            ) {
                when {
                    on -> OutlinedButton(onClick = { vm.equip(null) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp)) {
                        Text(tr("장착 해제", "Unequip"), color = Ui.text)
                    }
                    have -> Button(
                        onClick = { vm.equip(a) }, enabled = !ui.shopBusy, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    ) { Text(tr("장착하기", "Equip"), fontWeight = FontWeight.Bold) }
                    else -> Button(
                        onClick = { confirm = Triple(a.name, a.label, a.price) },
                        enabled = !ui.shopBusy && p.score >= a.price,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                    ) { Text(tr("${a.price}점에 사기", "Buy for ${a.price} pts"), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    confirm?.let { (item, label, price) ->
        val after = (p?.score ?: 0) - price
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("$label 살까요?", "Buy $label?"), fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text(tr("${price}점을 써요. 남는 점수: ${after}점", "Costs ${price} pts. You’ll have ${after} pts left."), fontWeight = FontWeight.Bold)
                    if (p != null && Rank.of(after) != p.rank) {
                        Spacer(Modifier.height(6.dp))
                        Text(tr("⚠️ 계급이 ${p.rank.label} → ${Rank.of(after).label}(으)로 내려가요", "⚠️ Your rank will drop from ${p.rank.label} to ${Rank.of(after).label}"), color = Ui.danger, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.buy(item, label); confirm = null }) { Text(tr("살게요", "Buy")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("취소", "Cancel")) } },
        )
    }
}

@Composable
private fun ShopCard(
    title: String,
    desc: String,
    price: Int,
    owned: String?,
    art: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp).background(Ui.bgTop, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) { art() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ui.text)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("${price}점", "${price} pts"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ui.primaryDark)
                }
                Text(desc, fontSize = 13.sp, color = Ui.text.copy(alpha = 0.8f), lineHeight = 18.sp)
                if (owned != null) Text(owned, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ui.accent)
                Spacer(Modifier.height(6.dp))
                action()
            }
        }
    }
}

/** 하트 모양 병에 담긴 부활물약. */
@Composable
private fun PotionArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "potion")
    val wobble by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "w")
    val tw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_200)), label = "tw")
    androidx.compose.foundation.Canvas(modifier.graphicsLayer { rotationZ = wobble }) {
        val w = size.width
        val h = size.height
        val c = androidx.compose.ui.geometry.Offset(w / 2, h * 0.6f)
        val r = w * 0.3f
        // 병목, 코르크
        drawRect(Color(0xFFE3F2FD), androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.18f), androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.2f))
        drawRoundRect(Color(0xFFB7794A), androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.1f), androidx.compose.ui.geometry.CornerRadius(6f))
        // 병
        drawCircle(Color(0xFFE3F2FD), r, c)
        // 하트 물약
        val heart = androidx.compose.ui.graphics.Path().apply {
            val hx = c.x
            val hy = c.y - r * 0.15f
            val k = r * 0.7f
            moveTo(hx, hy + k * 0.9f)
            cubicTo(hx - k * 1.3f, hy, hx - k * 0.6f, hy - k * 0.9f, hx, hy - k * 0.3f)
            cubicTo(hx + k * 0.6f, hy - k * 0.9f, hx + k * 1.3f, hy, hx, hy + k * 0.9f)
            close()
        }
        drawPath(heart, Color(0xFFFF5C8A))
        drawCircle(Color.White.copy(alpha = 0.8f), r * 0.14f, c + androidx.compose.ui.geometry.Offset(-r * 0.45f, -r * 0.45f))
        drawCircle(Color.White, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        val a = kotlin.math.sin(tw * Math.PI).toFloat()
        sparkle(c + androidx.compose.ui.geometry.Offset(r * 0.9f, -r * 0.9f), 9f * a + 2f, Ui.gold.copy(alpha = a))
        sparkle(c + androidx.compose.ui.geometry.Offset(-r * 1.0f, r * 0.2f), 6f * (1 - a) + 2f, Color.White.copy(alpha = 1 - a))
    }
}

/** 이번 판에 남은 부활물약 (없으면 표시 안 함). */
@Composable
private fun ReviveCounter(left: Int) {
    if (left <= 0) return
    Spacer(Modifier.width(6.dp))
    Text(
        "💖×$left",
        fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFFE0457B),
        modifier = Modifier
            .background(Color(0x33FF5C8A), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 계급표 팝업: 모든 계급과 점수 범위, 내 계급, 다음 계급까지 남은 점수. */
@Composable
private fun RanksDialog(myScore: Int, onDismiss: () -> Unit) {
    val mine = Rank.of(myScore)
    val next = Rank.entries.getOrNull(mine.ordinal + 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close"), fontWeight = FontWeight.Bold) } },
        title = { Text(tr("🏅 계급표", "🏅 Ranks"), fontWeight = FontWeight.Black, fontSize = 24.sp) },
        text = {
            Column {
                Rank.entries.reversed().forEach { r ->
                    val isMine = r == mine
                    val range = when (r) {
                        Rank.DIRT -> tr("1000점 이하", "Up to 1000 pts")
                        Rank.DIAMOND -> tr("${r.minScore}점 이상", "${r.minScore}+ pts")
                        else -> tr("${r.minScore} ~ ${Rank.entries[r.ordinal + 1].minScore - 1}점", "${r.minScore} – ${Rank.entries[r.ordinal + 1].minScore - 1} pts")
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(
                                if (isMine) Ui.primary.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 배지 폭을 맞춰 표처럼 보이게
                        Box(Modifier.width(112.dp), contentAlignment = Alignment.CenterStart) {
                            RankBadge(if (r == Rank.DIRT) 1000 else r.minScore, large = true)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(range, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ui.text)
                            Text(
                                if (r.bonusPercent > 0) tr("🎁 얻는 점수 +${r.bonusPercent}% 추가", "🎁 +${r.bonusPercent}% bonus on points earned") else tr("보너스 없음", "No bonus"),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (r.bonusPercent > 0) Color(0xFFD08A00) else Ui.text.copy(alpha = 0.5f),
                            )
                            if (isMine) Text(tr("👈 지금 내 계급", "👈 Your rank"), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (next != null) {
                        tr("내 점수 ${myScore}점 · ${next.label}까지 ${next.minScore - myScore}점 남았어요!", "Your score ${myScore} · ${next.minScore - myScore} pts to ${next.label}!")
                    } else {
                        tr("내 점수 ${myScore}점 · 최고 계급이에요! 💎", "Your score ${myScore} · Top rank! 💎")
                    },
                    fontWeight = FontWeight.Black, fontSize = 15.sp, color = Ui.accent,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("🎁 계급 보너스: 이겨서 얻는 점수(랜덤 매칭·AI 대전)에 계급만큼 점수를 더 받아요. ", "🎁 Rank bonus: wins (random match & AI battle) earn extra points based on your rank. ") +
                        tr("예) 다이아가 랜덤 매칭에서 이기면 25점 + 20%(5점) = 30점", "e.g. a Diamond player winning a random match gets 25 + 20% (5) = 30 pts"),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ui.text,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22FFC94D), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("높은 계급일수록 배지가 더 반짝여요 ✨", "Higher ranks get sparklier badges ✨"),
                    fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** 로비 왼쪽 위의 작은 언어 선택 메뉴 (🌐 한국어 / English). */
@Composable
private fun LanguageMenu() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "🌐 ${Lang.current.label} ▾",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Ui.text,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.7f))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Lang.Code.entries.forEach { code ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (code == Lang.current) "✓ " else "   ") + code.label,
                            fontWeight = if (code == Lang.current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        Lang.set(context, code)
                        open = false
                    },
                )
            }
        }
    }
}
