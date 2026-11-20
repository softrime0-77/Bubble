package com.bubble.game.ui

import androidx.activity.compose.BackHandler
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
            }
        }
    }
}

// ======================= 로비 (대기방) =======================

@Composable
private fun LobbyScreen(vm: AppViewModel) {
    val ui by vm.lobby.collectAsState()
    var showRules by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleBanner()
        Spacer(Modifier.height(16.dp))

        SoftCard {
            when {
                !ui.onlineAvailable -> Text(
                    "온라인 대전은 Firebase 설정(google-services.json)을 넣고 다시 빌드하면 열려요.\n지금은 AI 연습을 즐겨 주세요!",
                    color = Ui.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                ui.needsLogin && !ui.loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("구글 계정으로 로그인하면\n점수와 전적이 계정에 저장돼요", color = Ui.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text("상대에게는 구글 이름 대신 닉네임만 보여요", fontSize = 12.sp, color = Ui.text.copy(alpha = 0.6f))
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
                        Text("Google로 시작하기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    if (ui.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.error!!, color = Ui.danger, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                ui.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Ui.primary, strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("서버에 접속하는 중…", color = Ui.text)
                }
                ui.profile != null -> {
                    val p = ui.profile!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Critter(Side.CAT, Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ui.text)
                            Text("${p.wins}승 ${p.losses}패", fontSize = 13.sp, color = Ui.text.copy(alpha = 0.7f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${p.score}", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ui.primaryDark)
                            Text("점", fontSize = 12.sp, color = Ui.text.copy(alpha = 0.7f))
                        }
                    }
                    Row {
                        TextButton(onClick = { renaming = true }) { Text("✏️ 닉네임 바꾸기", color = Ui.accent) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::signOut) { Text("로그아웃", color = Ui.text.copy(alpha = 0.6f)) }
                    }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error ?: "접속하지 못했어요", color = Ui.danger, textAlign = TextAlign.Center)
                    TextButton(onClick = vm::connect) { Text("다시 시도") }
                }
            }
            if (ui.error != null && ui.profile != null) {
                Text(ui.error!!, color = Ui.danger, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        BigButton(
            text = "🎲  랜덤 매칭",
            sub = "익명의 상대와 1:1 대결 · 승리 +${OnlineRepository.WIN_POINTS}점",
            enabled = ui.onlineAvailable && ui.profile != null && !ui.needsLogin,
            onClick = vm::startMatching,
        )
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text("🤖 AI와 연습하기", fontWeight = FontWeight.Bold, color = Ui.text)
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
        }
        TextButton(onClick = { showRules = true }) { Text("📖 게임 규칙 보기", color = Ui.text) }

        if (ui.leaderboard.isNotEmpty()) {
            SoftCard {
                Text("🏆 랭킹", fontWeight = FontWeight.Bold, color = Ui.text)
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
                        Text(p.name + if (me) " (나)" else "", modifier = Modifier.weight(1f), color = Ui.text)
                        Text("${p.score}점", color = Ui.primaryDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showRules) RulesDialog { showRules = false }
    if (renaming) {
        var name by remember { mutableStateOf(ui.profile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("닉네임") },
            text = {
                OutlinedTextField(name, { name = it.take(12) }, singleLine = true, label = { Text("최대 12자") })
            },
            confirmButton = { TextButton(onClick = { vm.rename(name); renaming = false }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("취소") } },
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
            Text("냥냥 vs 햄햄 방울 대전", fontSize = 14.sp, color = Ui.text)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("알겠어요!") } },
        title = { Text("게임 규칙") },
        text = {
            Text(
                "• 고양이와 햄스터가 각자 방울(세트) 2개에 1마리씩 들고 시작해요. 고양이가 먼저!\n\n" +
                    "• 내 차례에는 내 방울 하나를 골라요. 그 방울의 마리 수만큼\n" +
                    "   ⚔️ 상대 방울에 더하거나 (공격)\n" +
                    "   💪 내 다른 방울에 더할 수 있어요 (강화)\n\n" +
                    "• 방울이 5마리 이상이 되면 💥 펑! 터져서 0마리가 돼요.\n\n" +
                    "• 0마리 방울은 공격도 강화도 못 해요. 살리는 방법은 분할뿐!\n" +
                    "   ✂️ 분할: 한 방울이 짝수(2·4마리)이고 다른 방울이 0일 때 반씩 나눠요.\n\n" +
                    "• 내 방울 2개가 모두 터지면 져요.\n\n" +
                    "• 온라인 대전은 한 턴에 ${OnlineRepository.TURN_LIMIT_MS / 1000}초! 시간을 넘기면 패배예요.",
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
        Text("상대를 찾고 있어요" + ".".repeat(sec % 4), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ui.text)
        Spacer(Modifier.height(6.dp))
        Text("%d:%02d".format(sec / 60, sec % 60), fontSize = 16.sp, color = Ui.text.copy(alpha = 0.7f))
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) { Text("매칭 취소", color = Ui.text) }
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

    fun previewFor(target: Int) = if (target + power >= GameLogic.EXPLODE_AT) "💥 펑!" else "→ ${target + power}"

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 상단 바
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (g.finished) vm.leaveGame() else confirmLeave = true }) {
                    Text("← 나가기", color = Ui.text)
                }
                Spacer(Modifier.weight(1f))
                if (remain != null && !g.finished) {
                    Text(
                        "⏱ ${remain}초",
                        color = if (remain <= 10) Ui.danger else Ui.text,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            PlayerTag(opp, g.opponentName, g.opponentScore?.let { "${it}점" }, active = g.turn == opp && !g.finished)
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
            PlayerTag(me, if (g.myName == "나") "나" else g.myName + " (나)", null, active = g.myTurn)

            val canSplit = sel != null && GameLogic.canSplit(g.board, me, sel)
            AnimatedVisibility(canSplit, enter = fadeIn() + slideInVertically { it }) {
                Button(
                    onClick = { vm.play(Move.Split(sel!!)); selected = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Ui.accent),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) { Text("✂️ 분할해서 빈 방울 살리기 (${power / 2} + ${power / 2})", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(8.dp))
            g.notice?.let { Text(it, fontSize = 12.sp, color = Ui.danger, textAlign = TextAlign.Center) }
            g.log.take(2).forEachIndexed { i, line ->
                Text(
                    line, fontSize = 12.sp,
                    color = Ui.text.copy(alpha = if (i == 0) 0.9f else 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (g.finished && g.winner != null) ResultOverlay(g, onLobby = vm::leaveGame, onAgain = {
            val lv = AiLevel.entries.firstOrNull { g.opponentName.contains(it.label) } ?: AiLevel.NORMAL
            vm.startAiGame(lv)
        })
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("게임에서 나갈까요?") },
            text = { Text(if (g.online) "지금 나가면 기권패로 처리돼요 (-${OnlineRepository.LOSE_POINTS}점)." else "연습 게임이 끝나요.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveGame() }) { Text("나가기", color = Ui.danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("계속하기") } },
        )
    }
}

@Composable
private fun PlayerTag(side: Side, name: String, extra: String?, active: Boolean) {
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
        Critter(side, Modifier.size(26.dp))
        Spacer(Modifier.width(6.dp))
        Text("${side.label} · $name", color = Ui.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (extra != null) Text("  $extra", color = Ui.primaryDark, fontSize = 13.sp)
    }
}

@Composable
private fun TurnBanner(g: GameUi, selecting: Boolean) {
    val text = when {
        g.finished -> "게임 끝!"
        g.busy && g.turn == g.mySide -> "보내는 중…"
        g.turn != g.mySide -> if (g.online) "상대가 고민 중이에요…" else "AI가 생각 중… 🤔"
        selecting -> "어디에 더할까요? 반짝이는 방울을 눌러요"
        else -> "내 차례! 내 방울을 하나 골라요"
    }
    AnimatedContent(text, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() }, label = "turn") {
        Text(
            it, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (g.turn == g.mySide && !g.finished) Ui.primaryDark else Ui.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ResultOverlay(g: GameUi, onLobby: () -> Unit, onAgain: () -> Unit) {
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
                    if (won) "승리! 🎉" else "아쉬운 패배…",
                    fontSize = 32.sp, fontWeight = FontWeight.Black,
                    color = if (won) Ui.primaryDark else Ui.text,
                )
                val reason = when (g.reason) {
                    "forfeit" -> if (won) "상대가 나갔어요" else "기권했어요"
                    "timeout" -> if (won) "상대가 시간을 넘겼어요" else "시간을 넘겼어요"
                    else -> if (won) "상대 방울을 모두 터뜨렸어요!" else "내 방울이 모두 터졌어요"
                }
                Text(reason, color = Ui.text.copy(alpha = 0.7f))
                if (g.online) {
                    Spacer(Modifier.height(10.dp))
                    val d = g.scoreDelta
                    Text(
                        when {
                            d == null -> "점수 계산 중…"
                            d >= 0 -> "+$d 점"
                            else -> "$d 점"
                        },
                        fontSize = 26.sp, fontWeight = FontWeight.Black,
                        color = if ((d ?: 0) >= 0) Ui.accent else Ui.danger,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!g.online) {
                        OutlinedButton(onClick = onAgain, shape = RoundedCornerShape(18.dp)) { Text("한 판 더") }
                    }
                    Button(
                        onClick = onLobby, shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ui.primary),
                    ) { Text("로비로") }
                }
            }
        }
    }
}
