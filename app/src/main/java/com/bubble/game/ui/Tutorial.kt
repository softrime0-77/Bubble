package com.bubble.game.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import com.bubble.game.game.Animal
import com.bubble.game.game.Board
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import com.bubble.game.i18n.tr

/**
 * 🎓 첫 접속 튜토리얼: 정해진 판에서 손가락 안내를 따라 5수를 둬요. 내가 두면 상대(연습 친구)도 정해진 수로 공격해요.
 * 1) 공격하면 더해져요 → (상대도 공격) 2) 커진 방울로 펑 → (상대가 내 방울을 펑) 3) ✂️ 분할 → (또 펑)
 * 4) 💖 부활물약 → (또 펑) 5) 마지막 1마리로 상대 4를 펑 = 승리
 * 끝나면 대전 방법(랜덤·친구·AI) · 랭킹 · 상점 캐릭터 안내 후 보상(💰 +20 · 💖 +1, 서버가 계정당 한 번).
 */
object TutorialScript {
    val board = Board(cat = listOf(1, 2), ham = listOf(1, 4), catRevives = 1)
    /** 내가 둘 수 (손가락 안내) */
    val moves: List<Move> = listOf(
        Move.Add(1, true, 0),   // 2로 상대 1 공격 → 3
        Move.Add(0, true, 0),   // (상대 공격으로 4가 된) 4로 상대 3 공격 → 7 펑!
        Move.Split(1),          // 내 1번이 터짐 → 남은 2를 1 + 1로 분할
        Move.Revive(0),         // 또 터짐 → 💖 부활물약으로 1마리 살리기
        Move.Add(0, true, 1),   // 1로 상대 4 공격 → 5 펑! 승리
    )
    /** 내 수 다음에 상대가 두는 수 (null이면 안 둠) */
    val replies: List<Move?> = listOf(
        Move.Add(0, true, 0),   // 상대 3으로 내 1 공격 → 4
        Move.Add(1, true, 0),   // 상대 4로 내 4 공격 → 펑
        Move.Add(1, true, 0),   // 상대 4로 내 1 공격 → 펑
        Move.Add(1, true, 1),   // 상대 4로 내 1 공격 → 펑
        null,
    )
    const val COINS = 20

    /** 이 단계에서 💖 부활 버튼을 보여 줄지 (다른 단계에선 헷갈리지 않게 숨김) */
    fun allowsRevive(step: Int?): Boolean = step == null || moves.getOrNull(step) is Move.Revive

    /** 단계별 안내 문구: (방울 고르기 전, 고른 뒤) */
    fun text(step: Int, selected: Boolean): String = when (step) {
        0 -> if (!selected) tr("👆 내 방울(2마리)을 콕 눌러 골라요", "👆 Tap your bubble with 2 to pick it", ja = "👆 自分のバブル(2匹)をタップして選ぼう", zh = "👆 点一下自己的泡泡（2只）选中它")
        else tr("👆 이제 상대 방울을 콕! 내 방울 수만큼 더해져요 (1+2=3)", "👆 Now tap a rival bubble! Your count gets added (1+2=3)", ja = "👆 相手のバブルをタップ！数が足されるよ (1+2=3)", zh = "👆 点对手的泡泡！会加上你的数量（1+2=3）")
        1 -> if (!selected) tr("😮 상대도 공격했어요! 내 방울이 1+3=4로 커졌어요.\n👆 4마리 방울을 골라요", "😮 They attacked back! Your bubble grew to 1+3=4.\n👆 Pick your bubble with 4", ja = "😮 相手も攻撃してきた！自分のバブルが1+3=4に。\n👆 4匹のバブルを選んで", zh = "😮 对手也攻击了！你的泡泡变成 1+3=4。\n👆 选4只的泡泡")
        else tr("👆 상대 3마리 방울을 콕! 3+4=7, 5 이상이면 💥 펑!", "👆 Tap their bubble with 3! 3+4=7, 5 or more = 💥 POP!", ja = "👆 相手の3匹のバブルをタップ！3+4=7、5以上で💥パン！", zh = "👆 点对手3只的泡泡！3+4=7，达到5就💥砰！")
        2 -> if (!selected) tr("💥 앗, 상대가 내 방울을 터뜨렸어요!\n👆 남은 2마리 방울을 골라요", "💥 Oops, they popped your bubble!\n👆 Pick your remaining bubble with 2", ja = "💥 あっ、自分のバブルが割られた！\n👆 残った2匹のバブルを選んで", zh = "💥 哎呀，你的泡泡被打爆了！\n👆 选剩下的2只泡泡")
        else tr("✂️ 짝수 방울 + 빈 방울이면 반으로 나눌 수 있어요!\n👆 아래 ✂️ 분할하기 버튼을 눌러요", "✂️ Even bubble + empty bubble = you can split!\n👆 Tap the ✂️ Split button below", ja = "✂️ 偶数のバブル＋空のバブルなら半分に分けられる！\n👆 下の✂️分割ボタンをタップ", zh = "✂️ 偶数泡泡＋空泡泡就能一分为二！\n👆 点下面的 ✂️ 分裂按钮")
        3 -> tr("😱 또 터졌어요! 💖 부활물약이면 터진 방울을 1마리로 살려요.\n👆 아래 💖 버튼을 눌러요", "😱 Popped again! A 💖 revive potion brings it back with 1.\n👆 Tap the 💖 button below", ja = "😱 また割られた！💖 復活薬で1匹に戻せるよ。\n👆 下の💖ボタンをタップ", zh = "😱 又被打爆了！💖 复活药水能让它以1只复活。\n👆 点下面的 💖 按钮")
        4 -> if (!selected) tr("😤 또 터졌지만 괜찮아요! 상대도 4마리 하나뿐.\n👆 남은 1마리 방울을 골라요", "😤 Popped again, but it's fine! They only have one bubble of 4.\n👆 Pick your bubble with 1", ja = "😤 また割られたけど大丈夫！相手も4匹のバブル1つだけ。\n👆 残った1匹のバブルを選んで", zh = "😤 又被打爆了，没关系！对手也只剩一个4只的泡泡。\n👆 选剩下的1只泡泡")
        else tr("👆 상대 4마리 방울을 콕! 4+1=5 💥 펑! 다 터뜨리면 승리!", "👆 Tap their bubble with 4! 4+1=5 💥 POP! Pop them all to win!", ja = "👆 相手の4匹のバブルをタップ！4+1=5 💥パン！全部割れば勝ち！", zh = "👆 点对手4只的泡泡！4+1=5 💥砰！全部打爆就赢了！")
        else -> ""
    }

    /** 상대 차례에 보여 줄 말 */
    fun opponentText(): String = tr("🤔 이번엔 상대 차례! 상대도 나를 공격해요…", "🤔 Their turn now! They attack you too…", ja = "🤔 今度は相手の番！相手も攻撃してくるよ…", zh = "🤔 轮到对手了！对手也会攻击你…")
}

/**
 * 게임 화면 위 안내: 말풍선 + 가리키는 손가락 + 건너뛰기.
 * [centers]의 키: 방울은 "CAT0" 처럼, 버튼은 "split" · "revive".
 */
@Composable
fun TutorialGuide(
    step: Int,
    mySide: Side,
    myTurn: Boolean,
    selected: Int?,
    centers: Map<String, Offset>,
    layerOrigin: Offset,
    onSkip: () -> Unit,
) {
    if (step !in TutorialScript.moves.indices) return
    val m = TutorialScript.moves[step]
    val picked = when (m) {
        is Move.Add -> selected == m.from
        is Move.Split -> selected == m.from
        is Move.Revive -> false
    }
    val target: String? = when {
        !myTurn -> null
        m is Move.Add -> if (!picked) "$mySide${m.from}" else "${if (m.toOpponent) mySide.opponent else mySide}${m.to}"
        m is Move.Split -> if (!picked) "$mySide${m.from}" else "split"
        else -> "revive"
    }
    val t = rememberInfiniteTransition(label = "tut")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "bob")
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        target?.let { centers[it] }?.let { c ->
            val p = c - layerOrigin
            // 버튼은 작으니 손가락을 조금 더 가까이
            val gap = if (target == "split" || target == "revive") 2 else 10
            Text(
                "👆", fontSize = 52.sp,
                modifier = Modifier.offset {
                    IntOffset((p.x - with(density) { 18.dp.toPx() }).toInt(), (p.y + with(density) { (gap + bob * 14).dp.toPx() }).toInt())
                },
            )
        }
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 290.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("🎓 튜토리얼 ${step + 1}/${TutorialScript.moves.size}", "🎓 Tutorial ${step + 1}/${TutorialScript.moves.size}", ja = "🎓 チュートリアル ${step + 1}/${TutorialScript.moves.size}", zh = "🎓 教程 ${step + 1}/${TutorialScript.moves.size}"),
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
                modifier = Modifier.background(Ui.accent, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (myTurn) TutorialScript.text(step, picked) else TutorialScript.opponentText(),
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

/** 끝난 뒤 안내 한 장: 제목 + 줄들 (+ 그림) */
private class TutorialPage(val emoji: String, val title: String, val lines: List<String>, val art: (@Composable () -> Unit)? = null)

private fun tutorialPages(): List<TutorialPage> = listOf(
    TutorialPage(
        "🎉", tr("승리! 기본은 끝!", "Victory! You know the basics!", ja = "勝利！基本はばっちり！", zh = "胜利！基础学会了！"),
        listOf(
            tr("⚔️ 공격하면 상대 방울에 더해져요. 상대도 똑같이 나를 공격해요",
                "⚔️ Attacking adds to their bubble. They attack you the same way",
                ja = "⚔️ 攻撃すると相手のバブルに足される。相手も同じように攻撃してくるよ", zh = "⚔️ 攻击会加到对手的泡泡上，对手也会这样攻击你"),
            tr("💥 5마리 이상이 되면 펑! 방울 2개가 다 터지면 져요",
                "💥 5 or more = POP! Lose both bubbles and you lose",
                ja = "💥 5匹以上でパン！バブルが2つとも割れたら負け", zh = "💥 达到5只就砰！两个泡泡都破了就输了"),
            tr("✂️ 분할: 한쪽이 비고 남은 방울이 짝수면 반으로! (한 판 ${com.bubble.game.game.GameLogic.MAX_SPLITS}번)",
                "✂️ Split: one side empty and the other even? Halve it! (${com.bubble.game.game.GameLogic.MAX_SPLITS} per game)",
                ja = "✂️ 分割：片方が空で残りが偶数なら半分に！（1試合${com.bubble.game.game.GameLogic.MAX_SPLITS}回）", zh = "✂️ 分裂：一边空了、另一边是偶数就能对半分！（每局${com.bubble.game.game.GameLogic.MAX_SPLITS}次）"),
            tr("💖 부활물약: 터진 방울을 1마리로 살려요. 상점·출석에서 얻고, 한 판 최대 ${com.bubble.game.game.Shop.MAX_REVIVES_PER_GAME}번 (랜덤 매칭 1번)",
                "💖 Revive potion: brings a popped bubble back with 1. Get them in the shop or check-ins. Up to ${com.bubble.game.game.Shop.MAX_REVIVES_PER_GAME} per game (1 in random matches)",
                ja = "💖 復活薬：割れたバブルを1匹で復活。ショップや出席で入手、1試合最大${com.bubble.game.game.Shop.MAX_REVIVES_PER_GAME}回（ランダムは1回）", zh = "💖 复活药水：让破掉的泡泡以1只复活。商店或签到获得，每局最多${com.bubble.game.game.Shop.MAX_REVIVES_PER_GAME}次（随机匹配1次）"),
            tr("⏱ 온라인 대전은 한 턴 ${com.bubble.game.data.OnlineRepository.TURN_LIMIT_MS / 1000}초! 시간을 넘기면 패배예요",
                "⏱ Online turns are ${com.bubble.game.data.OnlineRepository.TURN_LIMIT_MS / 1000}s. Run out of time and you lose!",
                ja = "⏱ オンライン対戦は1ターン${com.bubble.game.data.OnlineRepository.TURN_LIMIT_MS / 1000}秒！時間切れで負け", zh = "⏱ 在线对战每回合${com.bubble.game.data.OnlineRepository.TURN_LIMIT_MS / 1000}秒，超时判负！"),
        ),
    ),
    TutorialPage(
        "🎮", tr("이렇게 대전해요", "Ways to play", ja = "対戦のしかた", zh = "对战方式"),
        listOf(
            tr("🎲 랜덤 매칭: 전 세계의 모르는 상대와 1:1! 이기면 +${com.bubble.game.data.OnlineRepository.WIN_POINTS}점, 지면 -${com.bubble.game.data.OnlineRepository.LOSE_POINTS}점",
                "🎲 Random Match: 1:1 with a stranger anywhere in the world! Win +${com.bubble.game.data.OnlineRepository.WIN_POINTS}, lose -${com.bubble.game.data.OnlineRepository.LOSE_POINTS}",
                ja = "🎲 ランダムマッチ：世界のだれかと1対1！勝ち +${com.bubble.game.data.OnlineRepository.WIN_POINTS}点、負け -${com.bubble.game.data.OnlineRepository.LOSE_POINTS}点", zh = "🎲 随机匹配：和世界各地的陌生人 1:1！胜 +${com.bubble.game.data.OnlineRepository.WIN_POINTS}，负 -${com.bubble.game.data.OnlineRepository.LOSE_POINTS}"),
            tr("👫 친구와 대전: 링크를 보내면 친구가 눌러 바로 1:1! (+${com.bubble.game.data.OnlineRepository.FRIEND_WIN_POINTS} / -${com.bubble.game.data.OnlineRepository.FRIEND_LOSE_POINTS}점)",
                "👫 Battle a Friend: send a link, they tap it, and you're 1:1! (+${com.bubble.game.data.OnlineRepository.FRIEND_WIN_POINTS} / -${com.bubble.game.data.OnlineRepository.FRIEND_LOSE_POINTS})",
                ja = "👫 友だちと対戦：リンクを送ればタップですぐ1対1！（+${com.bubble.game.data.OnlineRepository.FRIEND_WIN_POINTS} / -${com.bubble.game.data.OnlineRepository.FRIEND_LOSE_POINTS}点）", zh = "👫 好友对战：发送链接，好友点开马上 1:1！（+${com.bubble.game.data.OnlineRepository.FRIEND_WIN_POINTS} / -${com.bubble.game.data.OnlineRepository.FRIEND_LOSE_POINTS}）"),
            tr("🤖 AI 대전: ${com.bubble.game.game.AiLevel.entries.joinToString(" · ") { it.label }} 중에 골라 언제든! 로그인하면 점수도 걸려요",
                "🤖 AI Battle: pick ${com.bubble.game.game.AiLevel.entries.joinToString(" · ") { it.label }} and play anytime! Sign in to earn points too",
                ja = "🤖 AI対戦：${com.bubble.game.game.AiLevel.entries.joinToString(" · ") { it.label }} から選んでいつでも！ログインすればポイントも", zh = "🤖 AI 对战：从 ${com.bubble.game.game.AiLevel.entries.joinToString(" · ") { it.label }} 中选择，随时开打！登录后还能得分"),
            tr("💡 처음엔 AI 쉬움으로 연습하고, 자신 있으면 랜덤 매칭에 도전해 보세요!",
                "💡 Warm up against the Easy AI, then try a Random Match when you're ready!",
                ja = "💡 まずはAIのやさしいで練習して、自信がついたらランダムマッチへ！", zh = "💡 先和简单 AI 练习，有信心了再挑战随机匹配！"),
        ),
    ),
    TutorialPage(
        "🏆", tr("랭킹과 계급", "Rankings & ranks", ja = "ランキングとランク", zh = "排行榜与等级"),
        listOf(
            tr("📈 이기면 점수가 올라 로비의 🏆 랭킹에 올라가요 (1분마다 갱신)",
                "📈 Wins raise your score and your spot on the lobby 🏆 leaderboard (updates every minute)",
                ja = "📈 勝つとスコアが上がり、ロビーの🏆ランキングに載るよ（1分ごとに更新）", zh = "📈 获胜会提升分数，登上大厅的 🏆 排行榜（每分钟更新）"),
            tr("🎁 매주 월요일 0시 TOP 100에게 💰 보상! 1위 1000 · 2위 700 · 3위 500",
                "🎁 Every Monday 00:00 (KST) the TOP 100 get 💰! #1 1000 · #2 700 · #3 500",
                ja = "🎁 毎週月曜0時（韓国時間）TOP100に💰！1位1000 · 2位700 · 3位500", zh = "🎁 每周一 0 点（韩国时间）TOP 100 获得 💰！第1名 1000 · 第2名 700 · 第3名 500"),
            tr("🎖 점수에 따라 계급이 올라가요. 계급이 높을수록 이길 때 보너스 점수가 더 붙어요!",
                "🎖 Your rank goes up with your score. Higher ranks earn bonus points on every win!",
                ja = "🎖 スコアでランクが上がる。ランクが高いほど勝った時のボーナスが増えるよ！", zh = "🎖 分数越高等级越高，等级越高获胜时的奖励分越多！"),
            com.bubble.game.game.Rank.entries.joinToString(" → ") { it.emoji },
        ),
    ),
    TutorialPage(
        "🛒", tr("상점과 캐릭터", "Shop & characters", ja = "ショップとキャラ", zh = "商店与角色"),
        listOf(
            tr("💖 부활물약 · 🚀 ×3 부스터 · ✏️ 닉네임 변경권을 💰 상점 포인트나 점수로 사요",
                "💖 Revive potions · 🚀 x3 boosters · ✏️ rename tickets, bought with 💰 shop points or score",
                ja = "💖 復活薬 · 🚀 ×3ブースター · ✏️ ニックネーム変更券を💰ポイントやスコアで買えるよ", zh = "💖 复活药水 · 🚀 ×3 加成 · ✏️ 改名券，可用 💰 商店积分或分数购买"),
            tr("🐑🐶🦒 소모품 동물: 랜덤 매칭에서 상대의 턴 시간을 줄여요 (1판에 1개)",
                "🐑🐶🦒 Consumable animals: shorten your opponent's turn in random matches (1 per game)",
                ja = "🐑🐶🦒 消耗品どうぶつ：ランダムマッチで相手のターン時間を短く（1試合1個）", zh = "🐑🐶🦒 消耗品动物：随机匹配中缩短对手的回合时间（每局1个）"),
            tr("✨ 스킨은 한 번 사면 계속! 💰 복냥이 · 🐼 판다 · 🐉 아기 용은 포인트 보너스, 🐰 연승 토끼는 연승 리본",
                "✨ Skins are yours forever! 💰 Lucky Cat · 🐼 Panda · 🐉 Dragon give point bonuses, 🐰 Streak Bunny wears win-streak ribbons",
                ja = "✨ スキンは買い切り！💰 まねきネコ · 🐼 パンダ · 🐉 ドラゴンはポイントボーナス、🐰 れんしょうウサギは連勝リボン", zh = "✨ 皮肤买一次永久拥有！💰 招财猫 · 🐼 熊猫 · 🐉 小龙有积分奖励，🐰 连胜兔戴连胜丝带"),
            tr("🦄 유니콘은 팔지 않아요. 다이아 계급이 되면 무료!",
                "🦄 The Unicorn isn't for sale. Reach Diamond rank to get it free!",
                ja = "🦄 ユニコーンは非売品。ダイヤランクになれば無料！", zh = "🦄 独角兽不出售，达到钻石等级即可免费获得！"),
            tr("👆 산 캐릭터는 로비에서 내 캐릭터를 눌러 바로 바꿔요",
                "👆 Tap your character in the lobby to switch to one you own",
                ja = "👆 買ったキャラはロビーで自分のキャラをタップしてすぐ変更", zh = "👆 在大厅点你的角色即可换成已拥有的角色"),
        ),
        art = {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                listOf(Animal.LUCKY_CAT, Animal.PANDA, Animal.DRAGON, Animal.RABBIT, Animal.UNICORN).forEach { a ->
                    Critter(if (a.looksLikeCat) Side.CAT else Side.HAMSTER, Modifier.size(52.dp), skin = a, ribbons = if (a == Animal.RABBIT) 3 else 0)
                }
            }
        },
    ),
)

/**
 * 다 끝나면: 대전 방법 · 랭킹 · 상점 안내를 한 장씩 넘겨 본 뒤 보상 받기.
 * [canShop]이면 마지막 장에서 상점으로 바로 들어가 볼 수 있어요 ([onClaim]의 인자 true).
 */
@Composable
fun TutorialDone(alreadyRewarded: Boolean = false, canShop: Boolean = true, onClaim: (toShop: Boolean) -> Unit) {
    val t = rememberInfiniteTransition(label = "tutDone")
    val glow by t.animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    val pages = remember { tutorialPages() }
    var page by remember { mutableIntStateOf(0) }
    val last = page == pages.lastIndex
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC302040))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF6EEFF))), RoundedCornerShape(28.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = pages[page]
            Text(p.emoji, fontSize = 44.sp)
            Text(p.title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            p.art?.let { it(); Spacer(Modifier.height(6.dp)) }
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                p.lines.forEach { line ->
                    Text(line, fontSize = 14.sp, color = Ui.text, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            // 몇 번째 장인지 점으로
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    Box(Modifier.size(8.dp).background(if (i == page) Ui.primary else Ui.text.copy(alpha = 0.2f), RoundedCornerShape(50)))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!last) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page > 0) {
                        Box(
                            Modifier
                                .weight(1f)
                                .background(Color(0x14000000), RoundedCornerShape(50))
                                .clickable { page-- }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(tr("‹ 이전", "‹ Back", ja = "‹ 戻る", zh = "‹ 上一页"), color = Ui.text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                    Box(
                        Modifier
                            .weight(2f)
                            .background(Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)), RoundedCornerShape(50))
                            .clickable { page++ }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("다음 ›", "Next ›", ja = "次へ ›", zh = "下一页 ›"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                }
            } else {
                if (alreadyRewarded) {
                    // 이미 보상을 받은 계정: 보상 버튼은 비활성
                    Text(
                        tr("✅ 보상은 이미 받았어요", "✅ Reward already claimed", ja = "✅ 報酬は受け取り済み", zh = "✅ 奖励已领取"),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ui.text.copy(alpha = 0.45f), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(Color(0x14000000), RoundedCornerShape(50)).padding(vertical = 12.dp),
                    )
                } else {
                    Text(
                        tr("🎁 보상: 💰 +${TutorialScript.COINS} · 💖 +1", "🎁 Reward: 💰 +${TutorialScript.COINS} · 💖 +1", ja = "🎁 報酬：💰 +${TutorialScript.COINS} · 💖 +1", zh = "🎁 奖励：💰 +${TutorialScript.COINS} · 💖 +1"),
                        fontSize = (16 * glow).sp, fontWeight = FontWeight.Black, color = Color(0xFFC88A00),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val claim = if (alreadyRewarded) "" else tr("보상 받고 ", "Claim & ", ja = "報酬を受け取って", zh = "领取奖励并")
                if (canShop) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFB300), Ui.primary, Ui.accent)), RoundedCornerShape(50))
                            .clickable { onClaim(true) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "🛒 " + claim + tr("상점 들어가 보기", "visit the shop", ja = "ショップをのぞく", zh = "逛逛商店"),
                            color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (canShop) Modifier.background(Color(0x14000000), RoundedCornerShape(50))
                            else Modifier.background(Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)), RoundedCornerShape(50)),
                        )
                        .clickable { onClaim(false) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        claim + tr("로비로", "back to lobby", ja = "ロビーへ", zh = "返回大厅"),
                        color = if (canShop) Ui.text else Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** 🛒 튜토리얼에서 상점으로 들어오면 맨 위에 보여 주는 캐릭터 안내 */
@Composable
fun ShopTourCard(onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF4D6), Color(0xFFF6EEFF))), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Text(tr("🎓 상점 둘러보기", "🎓 Shop tour", ja = "🎓 ショップ案内", zh = "🎓 商店导览"), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Ui.primaryDark)
        Spacer(Modifier.height(6.dp))
        listOf(
            tr("1️⃣ 위쪽: 💖 부활물약 · ✏️ 닉네임 변경권 · 🚀 ×3 부스터", "1️⃣ Up top: 💖 revive potions · ✏️ rename tickets · 🚀 x3 boosters",
                ja = "1️⃣ 上：💖 復活薬 · ✏️ ニックネーム変更券 · 🚀 ×3ブースター", zh = "1️⃣ 上方：💖 复活药水 · ✏️ 改名券 · 🚀 ×3 加成"),
            tr("2️⃣ 아래로 내리면 캐릭터들! 🐹 햄스터는 무료, ✨ 스킨은 한 번 사면 계속", "2️⃣ Scroll down for characters! 🐹 Hamster is free, ✨ skins are yours forever",
                ja = "2️⃣ 下にスクロールするとキャラ！🐹 ハムスターは無料、✨ スキンは買い切り", zh = "2️⃣ 往下滑就是角色！🐹 仓鼠免费，✨ 皮肤买一次永久拥有"),
            tr("3️⃣ 카드마다 능력(💰 보너스·리본 등)과 맞은 방울에 남는 흔적이 적혀 있어요", "3️⃣ Each card lists its power (💰 bonuses, ribbons…) and the mark it leaves on bubbles",
                ja = "3️⃣ カードごとに能力（💰ボーナス・リボンなど）と残る跡が書いてあるよ", zh = "3️⃣ 每张卡片都写着能力（💰 奖励、丝带等）和留在泡泡上的痕迹"),
            tr("4️⃣ 🐑🐶🦒 소모품 동물은 랜덤 매칭에서 상대 턴을 줄여요", "4️⃣ 🐑🐶🦒 consumable animals shorten your opponent's turn in random matches",
                ja = "4️⃣ 🐑🐶🦒 消耗品どうぶつはランダムマッチで相手のターンを短く", zh = "4️⃣ 🐑🐶🦒 消耗品动物在随机匹配中缩短对手回合"),
            tr("5️⃣ 산 캐릭터는 로비에서 내 캐릭터를 눌러 바꿀 수 있어요", "5️⃣ Tap your character in the lobby to switch to one you own",
                ja = "5️⃣ 買ったキャラはロビーで自分のキャラをタップして変更", zh = "5️⃣ 在大厅点你的角色即可更换已拥有的角色"),
        ).forEach { Text(it, fontSize = 13.sp, color = Ui.text, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(
            tr("알겠어요!", "Got it!", ja = "わかった！", zh = "知道了！"),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Ui.primary, Ui.accent)), RoundedCornerShape(50))
                .clickable(onClick = onDismiss)
                .padding(vertical = 10.dp),
        )
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
