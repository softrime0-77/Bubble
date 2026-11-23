package com.bubble.game.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.game.Board
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import com.bubble.game.i18n.tr

/**
 * 🎓 첫 접속 튜토리얼: 정해진 판에서 손가락 안내를 따라 4수를 두면 끝 (상대는 두지 않음).
 * 1) 공격하면 더해져요 → 2) 내 방울끼리 키우기 → 3) 5가 되면 펑 → 4) 마지막 방울도 펑 = 승리
 * 끝나면 분할 · 부활물약 · 시간 안내 후 보상(💰 +20 · 💖 +1, 서버가 계정당 한 번).
 */
object TutorialScript {
    val board = Board(cat = listOf(1, 2), ham = listOf(1, 3))
    val moves: List<Move.Add> = listOf(
        Move.Add(1, true, 0),   // 2로 상대 1 공격 → 3
        Move.Add(0, false, 1),  // 내 1을 내 2에 더해 → 3
        Move.Add(1, true, 0),   // 3으로 상대 3 공격 → 6 펑!
        Move.Add(1, true, 1),   // 3으로 상대 3 공격 → 펑! 승리
    )
    const val COINS = 20

    /** 단계별 안내 문구: (방울 고르기 전, 고른 뒤) */
    fun text(step: Int, selected: Boolean): String = when (step) {
        0 -> if (!selected) tr("👆 내 방울(2마리)을 콕 눌러 골라요", "👆 Tap your bubble with 2 to pick it", ja = "👆 自分のバブル(2匹)をタップして選ぼう", zh = "👆 点一下自己的泡泡（2只）选中它")
        else tr("👆 이제 상대 방울을 콕! 내 방울 수만큼 더해져요 (1+2=3)", "👆 Now tap a rival bubble! Your count gets added (1+2=3)", ja = "👆 相手のバブルをタップ！数が足されるよ (1+2=3)", zh = "👆 点对手的泡泡！会加上你的数量（1+2=3）")
        1 -> if (!selected) tr("👆 내 방울끼리도 더할 수 있어요. 1마리 방울을 골라요", "👆 You can add to your own bubbles too. Pick the bubble with 1", ja = "👆 自分のバブル同士も足せるよ。1匹のバブルを選んで", zh = "👆 自己的泡泡也能相加。选只有1只的泡泡")
        else tr("👆 옆의 내 방울을 콕! 2+1=3으로 커져요", "👆 Tap your other bubble! 2+1 = 3", ja = "👆 となりの自分のバブルをタップ！2+1=3", zh = "👆 点你的另一个泡泡！2+1=3")
        2 -> if (!selected) tr("👆 3마리 방울을 골라요", "👆 Pick your bubble with 3", ja = "👆 3匹のバブルを選んで", zh = "👆 选3只的泡泡")
        else tr("👆 상대 3마리 방울을 콕! 5 이상이 되면 💥 펑!", "👆 Tap their bubble with 3! 5 or more = 💥 POP!", ja = "👆 相手の3匹のバブルをタップ！5以上で💥パン！", zh = "👆 点对手3只的泡泡！达到5就💥砰！")
        3 -> if (!selected) tr("👆 마지막! 3마리 방울을 다시 골라요", "👆 Last one! Pick your 3 again", ja = "👆 最後！もう一度3匹のバブルを選んで", zh = "👆 最后一步！再选3只的泡泡")
        else tr("👆 남은 상대 방울을 펑! 다 터뜨리면 승리!", "👆 Pop their last bubble to win!", ja = "👆 残った相手のバブルをパン！全部割れば勝ち！", zh = "👆 打爆最后一个泡泡就赢了！")
        else -> ""
    }
}

/** 게임 화면 위 안내: 말풍선 + 가리키는 손가락 + 건너뛰기 */
@Composable
fun TutorialGuide(
    step: Int,
    mySide: Side,
    selected: Int?,
    centers: Map<String, Offset>,
    layerOrigin: Offset,
    onSkip: () -> Unit,
) {
    if (step !in TutorialScript.moves.indices) return
    val m = TutorialScript.moves[step]
    val picked = selected == m.from
    val target = if (!picked) "$mySide${m.from}" else "${if (m.toOpponent) mySide.opponent else mySide}${m.to}"
    val t = rememberInfiniteTransition(label = "tut")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "bob")
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        centers[target]?.let { c ->
            val p = c - layerOrigin
            Text(
                "👆", fontSize = 52.sp,
                modifier = Modifier.offset {
                    IntOffset((p.x - with(density) { 18.dp.toPx() }).toInt(), (p.y + with(density) { (10 + bob * 14).dp.toPx() }).toInt())
                },
            )
        }
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 290.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("🎓 튜토리얼 ${step + 1}/4", "🎓 Tutorial ${step + 1}/4", ja = "🎓 チュートリアル ${step + 1}/4", zh = "🎓 教程 ${step + 1}/4"),
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
                modifier = Modifier.background(Ui.accent, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                TutorialScript.text(step, picked),
                fontSize = 17.sp, fontWeight = FontWeight.Black, color = Ui.text, textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Text(
            tr("건너뛰기 ›", "Skip ›", ja = "スキップ ›", zh = "跳过 ›"),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 22.dp, end = 72.dp)
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                .clickable(onClick = onSkip)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 다 끝나면: 더 알아 둘 것 + 보상 받기 */
@Composable
fun TutorialDone(onClaim: () -> Unit) {
    val t = rememberInfiniteTransition(label = "tutDone")
    val glow by t.animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC302040))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF6EEFF))), RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉", fontSize = 48.sp)
            Text(tr("튜토리얼 완료!", "Tutorial complete!", ja = "チュートリアル完了！", zh = "教程完成！"), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark)
            Spacer(Modifier.height(12.dp))
            listOf(
                tr("✂️ 분할: 한쪽 방울이 터지고 남은 방울이 짝수면 반으로 나눠 다시 두 개로! (한 판 3번)",
                    "✂️ Split: when one bubble is gone and the other is even, split it into two! (3 per game)",
                    ja = "✂️ 分割：片方が割れて残りが偶数なら半分に分けて2つに！（1試合3回）", zh = "✂️ 分裂：一个泡泡没了、剩下的是偶数时可以一分为二！（每局3次）"),
                tr("💖 부활물약: 터진 방울을 1마리로 되살려요 (상점·출석에서 얻어요)",
                    "💖 Revive potion: bring a popped bubble back with 1 (from the shop or check-ins)",
                    ja = "💖 復活薬：割れたバブルを1匹で復活（ショップや出席で入手）", zh = "💖 复活药水：让破掉的泡泡以1只复活（商店或签到获得）"),
                tr("⏱ 온라인 대전은 한 턴 20초! 시간을 넘기면 패배예요",
                    "⏱ Online turns are 20s. Run out of time and you lose!",
                    ja = "⏱ オンライン対戦は1ターン20秒！時間切れで負け", zh = "⏱ 在线对战每回合20秒，超时判负！"),
            ).forEach { line ->
                Text(line, fontSize = 14.sp, color = Ui.text, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Ui.primary, Ui.accent)), RoundedCornerShape(50))
                    .clickable(onClick = onClaim)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr("🎁 보상 받기 · 💰 +${TutorialScript.COINS} · 💖 +1", "🎁 Claim · 💰 +${TutorialScript.COINS} · 💖 +1", ja = "🎁 報酬を受け取る · 💰 +${TutorialScript.COINS} · 💖 +1", zh = "🎁 领取奖励 · 💰 +${TutorialScript.COINS} · 💖 +1"),
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = (17 * glow).sp,
                )
            }
        }
    }
}

/** 로비: 처음 한 번 튜토리얼 권하기 */
@Composable
fun TutorialPrompt(onStart: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(tr("🎓 1분 튜토리얼", "🎓 1-minute tutorial", ja = "🎓 1分チュートリアル", zh = "🎓 1分钟教程"), fontWeight = FontWeight.Black, fontSize = 22.sp) },
        text = {
            Text(
                tr("방울 대전, 처음이세요? 따라 하면서 금방 배워요!\n끝까지 하면 💰 +${TutorialScript.COINS} · 💖 부활물약 +1 선물!",
                    "New to bubble battles? Learn by playing along!\nFinish it for 💰 +${TutorialScript.COINS} and a 💖 revive potion!",
                    ja = "バブル対戦は初めて？やりながらすぐ覚えられるよ！\n最後までやると 💰 +${TutorialScript.COINS} と 💖 復活薬をプレゼント！",
                    zh = "第一次玩泡泡对战？跟着做马上就会！\n完成可得 💰 +${TutorialScript.COINS} 和 💖 复活药水！"),
                fontSize = 15.sp, color = Ui.text,
            )
        },
        confirmButton = { TextButton(onClick = onStart) { Text(tr("시작하기!", "Start!", ja = "はじめる！", zh = "开始！"), fontWeight = FontWeight.Black, color = Ui.primaryDark) } },
        dismissButton = { TextButton(onClick = onLater) { Text(tr("나중에", "Later", ja = "あとで", zh = "以后再说"), color = Ui.text.copy(alpha = 0.6f)) } },
    )
}
