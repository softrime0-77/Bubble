package com.bubble.game.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.bubble.game.game.Board
import com.bubble.game.game.Side
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 화면 확인용 스크린샷. SCREENSHOT_DIR 환경변수가 있을 때만 파일로 저장합니다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before
    fun korean() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.KO)
        // 다른 화면 스크린샷이 📜 프롤로그에 가려지지 않게 (프롤로그 테스트는 직접 띄움)
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("bubble_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("prologueSeen", true).commit()
    }

    @Test
    fun prologue() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent { BubbleApp(vm) }
        vm.showPrologue()
        for (page in 1..4) {
            shot("prologue_$page", 1_200)
            if (page < 4) rule.onNodeWithText("다음 ▶").performClick()
        }
    }

    @Test
    fun landEnding() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215).copy(land = true, tutorialDone = true, tutorialSeen = true), landEnding = true) }
        rule.setContent { BubbleApp(vm) }
        shot("land_ending", 2_500)
    }

    @Test
    fun landShopAndBadge() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(100_500)) }
        rule.setContent { BubbleApp(vm) }
        shot("land_lobby_before", 900)
        vm.openShop()
        rule.mainClock.advanceTimeBy(900)
        // 상점 맨 아래(🚩 땅)까지 쓸어 올리기
        repeat(8) {
            rule.onNodeWithText("🛒 상점", useUnmergedTree = true).performTouchInput { swipeUp(startY = bottom + 1500f, endY = top - 1500f) }
            rule.mainClock.advanceTimeBy(400)
        }
        shot("land_shop", 900)
        vm.debugSetLobby { it.copy(profile = richProfile(500).copy(land = true)) }
        vm.closeShop()
        shot("land_lobby_badge", 900)
    }

    @Test
    fun shopSkinArt() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent { BubbleApp(vm) }
        vm.openShop()
        rule.mainClock.advanceTimeBy(900)
        // 상점을 조금씩 쓸어 올리며 캐릭터 카드들 찍기
        for (i in 1..4) {
            repeat(2) {
                rule.onNodeWithText("🛒 상점", useUnmergedTree = true).performTouchInput { swipeUp(startY = bottom + 1400f, endY = top - 600f) }
                rule.mainClock.advanceTimeBy(300)
            }
            shot("shop_skins_$i", 600)
        }
    }

    @Test
    fun landRankingRows() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White).padding(16.dp),
            ) { rankedSample().take(6).mapIndexed { i, r -> r.copy(land = i % 2 == 1) }.forEach { RankingRow(it, me = false) {} } }
        }
        shot("land_ranking_rows", 700)
    }

    /** 다이얼로그는 별도 창이라, 가장 위에 떠 있는 창을 찍습니다. */
    private fun topWindowView(): android.view.View? = runCatching {
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val inst = wmg.getMethod("getInstance").invoke(null)
        val f = wmg.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (f.get(inst) as List<android.view.View>).lastOrNull { it.isShown && it.width > 0 }
    }.getOrNull()

    private fun shot(name: String, advanceMs: Long = 600) {
        rule.mainClock.advanceTimeBy(advanceMs)
        val dir = System.getenv("SCREENSHOT_DIR") ?: return
        val view = topWindowView() ?: rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun lobby() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        shot("lobby")
    }

    private fun richProfile(score: Int) = com.bubble.game.data.Profile(
        uid = "me", name = "말랑고양이42", score = score, wins = 12, losses = 5, potions = 2,
        animals = mapOf(com.bubble.game.game.Animal.SHEEP to 3, com.bubble.game.game.Animal.GIRAFFE to 1), equipped = com.bubble.game.game.Animal.SHEEP,
        refCode = "K7Q2MX", referrals = 3, coins = 1200, boosts = 2, boostOn = true,
    )

    private fun rankedSample() = listOf(1, 2, 3, 4, 5, 7, 10, 11, 20, 30, 31, 45).mapIndexed { i, pos ->
        RankedPlayer(pos, "u$pos", if (pos == 1) "엄청나게긴닉네임을가진다이아냥이123" else "플레이어$pos", if (i % 2 == 0) "KR" else "JP",
            20000 - pos * 300, 40 - i, i, if (pos == 1 || pos == 4 || pos == 20) com.bubble.game.game.Animal.UNICORN else if (pos == 2) com.bubble.game.game.Animal.GOLD_HAMSTER else null)
    }

    @Test
    fun rabbitRibbons() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                listOf(2, 4, 5).forEach { n ->
                    com.bubble.game.ui.Critter(Side.HAMSTER, androidx.compose.ui.Modifier.size(110.dp), skin = com.bubble.game.game.Animal.RABBIT, ribbons = n)
                }
            }
        }
        shot("rabbit_ribbons", 500)
    }

    @Test
    fun rankingRows() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color.White).padding(16.dp),
            ) { rankedSample().forEach { RankingRow(it, me = it.position == 5) {} } }
        }
        shot("ranking_rows", 700)
    }

    @Test
    fun rankingCardFirst() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerCardDialog(rankedSample()[0], me = false) {} }
        repeat(5) { rule.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        shot("ranking_card_1", 300)
    }

    @Test
    fun rankingCardTwenty() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerCardDialog(rankedSample()[8], me = false) {} }
        repeat(5) { rule.mainClock.advanceTimeBy(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        shot("ranking_card_20", 300)
    }

    @Test
    fun lobbyAdmin() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(28510).copy(admin = true, tutorialSeen = true), onlineCount = 1) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_admin", 900)
    }

    @Test
    fun lobbyWithRanks() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby {
            it.copy(
                onlineAvailable = true, loading = false, profile = richProfile(1215),
                onlineCount = 12,
                leaderboard = listOf(12400, 7300, 5200, 2600, 1600, 1100, 980).mapIndexed { i, sc ->
                    com.bubble.game.data.Profile("u$i", listOf("다이아냥", "말랑고양이42", "루비햄찌", "금빛냥", "은빛햄찌", "구리댕댕", "흙수저양")[i], sc, 0, 0)
                }.let { l -> l.toMutableList().also { it[1] = richProfile(7300) } },
            )
        }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_ranks", 900)
    }

    @Test
    fun ranksDialog() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(7300), onlineCount = 12) }
        rule.setContent { BubbleApp(vm) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("  계급표 ›").performClick()
        repeat(5) {
            rule.mainClock.advanceTimeBy(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shot("ranks", 300)
    }

    @Test
    fun lobbyEnglish() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.EN)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_en", 900)
    }

    @Test
    fun lobbyLang_ru() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.RU)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215).copy(tutorialSeen = true)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_ru", 900)
    }

    @Test
    fun lobbyLang_de() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.DE)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215).copy(tutorialSeen = true)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_de", 900)
    }

    @Test
    fun lobbyLang_hi() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.HI)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215).copy(tutorialSeen = true)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_hi", 900)
    }

    @Test
    fun lobbyLang_fr() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.FR)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215).copy(tutorialSeen = true)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_fr", 900)
    }

    @Test
    fun lobbyJapanese() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.JA)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_ja", 900)
    }

    @Test
    fun gameArabic() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.AR)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.HARD)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(4, 0), ham = listOf(2, 3), catSplits = 1, hamSplits = 3), mySide = Side.CAT, turn = Side.CAT, busy = false)
        }
        shot("game_ar", 1200)
    }

    @Test
    fun gameEnglish() {
        com.bubble.game.i18n.Lang.setForTest(com.bubble.game.i18n.Lang.Code.EN)
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.HARD)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(4, 0), ham = listOf(2, 3), catSplits = 1, hamSplits = 3),
                mySide = Side.CAT, turn = Side.CAT, busy = false,
                log = listOf("Hamster: Set 2 (3) attacks their Set 2 → 💥 POP!"),
            )
        }
        shot("game_en", 1200)
    }

    @Test
    fun gameRibbons() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(2, 1), ham = listOf(1, 3), catSplits = 3, hamSplits = 3),
                mySide = Side.CAT, turn = Side.CAT, busy = false,
                mySkin = com.bubble.game.game.Animal.RABBIT, myRibbons = 3,
                opponentSkin = com.bubble.game.game.Animal.RABBIT, opponentRibbons = 5,
            )
        }
        shot("game_ribbons", 1200)
    }

    @Test
    fun welcome() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1000), welcome = "new" to 1000) }
        rule.setContent { BubbleApp(vm) }
        repeat(5) {
            rule.mainClock.advanceTimeBy(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shot("welcome", 300)
    }

    @Test
    fun vsIntro() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.HARD)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                online = true, aiLevel = null, gameId = "g1", myName = "말랑고양이42", opponentName = "SleepyCat77",
                myCountry = "KR", opponentCountry = "JP", myScore = 7300, opponentScore = 2400,
                mySide = Side.CAT, turn = Side.CAT, busy = false, mySkin = com.bubble.game.game.Animal.SHEEP, opponentSkin = com.bubble.game.game.Animal.GIRAFFE,
            )
        }
        shot("vs", 600)
    }

    /** 작은 폰(360×640dp)에서 계급표가 잘리지 않는지 (스크롤로 끝까지 보임) */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun ranksDialogSmallPhone() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(7300)) }
        rule.setContent { BubbleApp(vm) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("  계급표 ›").performClick()
        repeat(5) {
            rule.mainClock.advanceTimeBy(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shot("ranks_small_top", 300)
        // 위로 쓸어 올려 맨 아래(흙)까지 스크롤
        rule.onNodeWithText("보너스 없음").performTouchInput { swipeUp(startY = bottom, endY = top) }
        rule.onNodeWithText("7001 ~ 10000점").performTouchInput { swipeUp(startY = bottom, endY = top - 2000f) }
        repeat(3) {
            rule.mainClock.advanceTimeBy(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shot("ranks_small_bottom", 300)
    }

    @Test
    fun shop() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent { BubbleApp(vm) }
        vm.openShop()
        shot("shop", 900)
    }

    @Test
    fun rankUp() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(10200), rankUpScore = 10200) }
        rule.setContent { BubbleApp(vm) }
        shot("rankup", 700)
    }

    @Test
    fun login() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, needsLogin = true) }
        rule.setContent { BubbleApp(vm) }
        shot("login")
    }

    @Test
    fun gameDefaultArt() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(1, 3), ham = listOf(4, 2), catSplits = 3, hamSplits = 2),
                // 실제 진영은 고양이여도 화면에서는 내가 햄스터로 보여야 함
                mySide = Side.CAT, turn = Side.CAT, busy = false,
                turnSec = 20, turnDeadline = System.currentTimeMillis() + 14_000,
                mySkin = null, opponentSkin = null,
                log = listOf(com.bubble.game.game.MoveText.describe(com.bubble.game.game.MoveInfo(Side.HAMSTER, com.bubble.game.game.Move.Add(0, true, 1), 1, 3, false, 3))),
            )
        }
        shot("game_default_art", 1200)
    }

    @Test
    fun game() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(4, 0), ham = listOf(2, 3), catSplits = 1, hamSplits = 3, catRevives = 2),
                mySide = Side.CAT, turn = Side.CAT, busy = false,
                turnSec = 15, turnDeadline = System.currentTimeMillis() + 9_400,
                mySkin = com.bubble.game.game.Animal.GIRAFFE, opponentSkin = com.bubble.game.game.Animal.DOG,
                log = listOf("햄스터: 2번 세트(3)로 상대 2번 세트 공격 → 💥 펑!", "고양이: 1번 세트 분할 → 2마리, 2마리 ✂️ (분할 -1, 남은 1회)"),
            )
        }
        shot("game", 1200)
    }

    @Test
    fun checkin() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215), checkin = CheckinReward(7, 100, true)) }
        rule.setContent { BubbleApp(vm) }
        shot("checkin_stamp", 560)
        shot("checkin", 1400)
    }

    /** 상대 방울이 날아와 내 방울을 치는 효과 + 캐릭터별 터짐 */
    @Test
    fun shotFx() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(2, 3), ham = listOf(4, 2)),
                mySide = Side.CAT, turn = Side.HAMSTER, busy = false, showIntro = false,
                mySkin = com.bubble.game.game.Animal.PINK_CAT, opponentSkin = com.bubble.game.game.Animal.SNOW_HAMSTER,
            )
        }
        rule.mainClock.advanceTimeBy(500)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(0, 3), ham = listOf(4, 2)), turn = Side.CAT,
                lastShot = Shot(Side.HAMSTER, 0, true, 0, 1),
                explosion = Explosion(Side.CAT, 0, 99),
            )
        }
        shot("shot_fly", 330)
        shot("shot_hit", 330)
        shot("shot_pop", 200)
    }

    /** 맞은 방울 흔적 13가지 */
    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
    fun hitMarks() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF6EEFF))) {
                val d = androidx.compose.ui.platform.LocalDensity.current
                MarkKind.entries.forEachIndexed { i, k ->
                    val cx = with(d) { (70 + (i % 3) * 130).dp.toPx() }
                    val cy = with(d) { (80 + (i / 3) * 160).dp.toPx() }
                    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
                        drawCircle(androidx.compose.ui.graphics.Color(0xFFE9D8FF), 56.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy))
                    }
                    HitMarkFx(HitMark(i.toLong() + 1, androidx.compose.ui.geometry.Offset(cx, cy), k)) {}
                }
            }
        }
        shot("hit_marks", 520)
    }

    /** 🎓 튜토리얼: 첫 단계 안내, 끝 화면 */
    @Test
    fun tutorial() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startTutorial()
        rule.mainClock.advanceTimeBy(900)
        shot("tutorial_1", 300)
        vm.play(com.bubble.game.game.Move.Add(1, true, 0))
        rule.mainClock.advanceTimeBy(1500)
        shot("tutorial_2", 300)
        vm.play(com.bubble.game.game.Move.Add(0, false, 1))
        rule.mainClock.advanceTimeBy(1500)
        vm.play(com.bubble.game.game.Move.Add(1, true, 0))
        rule.mainClock.advanceTimeBy(1500)
        vm.play(com.bubble.game.game.Move.Add(1, true, 1))
        rule.mainClock.advanceTimeBy(3000)
        shot("tutorial_done", 300)
    }

    @Test
    fun tutorialDoneRewarded() {
        rule.mainClock.autoAdvance = false
        rule.setContent { TutorialDone(alreadyRewarded = true) {} }
        shot("tutorial_done_rewarded", 500)
    }

    @Test
    fun selectedSparkles() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(2, 3), ham = listOf(1, 2)),
                mySide = Side.CAT, turn = Side.CAT, busy = false, showIntro = false,
                mySkin = com.bubble.game.game.Animal.BLACK_CAT, opponentSkin = com.bubble.game.game.Animal.GOLD_HAMSTER,
            )
        }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("1번 세트 · 2마리").performClick()
        shot("selected", 700)
    }

    @Test
    fun missionDone() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215), mission = MissionReward("2판 이기기", 30)) }
        rule.setContent { BubbleApp(vm) }
        shot("mission_done", 1400)
    }

    @Test
    fun shopFirstBuy() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby {
            it.copy(onlineAvailable = true, loading = false,
                profile = richProfile(1215).copy(firstBuyDone = false, createdAt = System.currentTimeMillis() - 2 * 86_400_000L - 3_600_000L))
        }
        rule.setContent { BubbleApp(vm) }
        vm.openShop()
        shot("shop_firstbuy", 800)
    }

    @Test
    fun shopLucky() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby {
            it.copy(onlineAvailable = true, loading = false,
                profile = richProfile(1215).copy(skins = setOf(com.bubble.game.game.Animal.LUCKY_CAT), equipped = com.bubble.game.game.Animal.LUCKY_CAT,
                    luckyDay = com.bubble.game.data.OnlineRepository.today(), luckyCoins = 7))
        }
        rule.setContent { BubbleApp(vm) }
        vm.openShop()
        rule.mainClock.advanceTimeBy(500)
        shot("shop_lucky", 800)
    }

    /** 복냥이로 공격 → 맞은 방울에서 💰 +1 */
    @Test
    fun luckyCoin() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(
                board = Board(cat = listOf(2, 3), ham = listOf(2, 2)),
                mySide = Side.CAT, turn = Side.HAMSTER, busy = false, showIntro = false,
                mySkin = com.bubble.game.game.Animal.LUCKY_CAT, opponentSkin = com.bubble.game.game.Animal.HAMSTER,
            )
        }
        rule.mainClock.advanceTimeBy(500)
        vm.debugSetGame { it.copy(board = Board(cat = listOf(2, 3), ham = listOf(4, 2)), lucky = LuckyHit(Side.CAT, 0, 7)) }
        shot("lucky_coin", 350)
        shot("lucky_coin2", 450)
    }

    /** 분할: 가위로 싹둑 → 절반이 옆 방울로 */
    @Test
    fun splitFx() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(4, 0), ham = listOf(2, 2)), mySide = Side.CAT, turn = Side.CAT, busy = false, showIntro = false,
                mySkin = com.bubble.game.game.Animal.DOG)
        }
        rule.mainClock.advanceTimeBy(500)
        vm.debugSetGame { it.copy(board = Board(cat = listOf(2, 2), ham = listOf(2, 2), catSplits = 2), turn = Side.HAMSTER,
            lastShot = Shot(Side.CAT, 0, false, 1, 5, ShotKind.SPLIT)) }
        shot("split_slash", 160)
        shot("split_fly", 220)
    }

    /** 부활: 빛기둥 + 물약 + 하트 */
    @Test
    fun reviveFx() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(0, 3), ham = listOf(2, 2), catRevives = 2), mySide = Side.CAT, turn = Side.CAT, busy = false, showIntro = false)
        }
        rule.mainClock.advanceTimeBy(500)
        vm.debugSetGame { it.copy(board = Board(cat = listOf(1, 3), ham = listOf(2, 2), catRevives = 1), turn = Side.HAMSTER,
            lastShot = Shot(Side.CAT, 0, false, 0, 6, ShotKind.REVIVE)) }
        shot("revive_drop", 300)
        shot("revive_burst", 500)
    }

    /** 새 캐릭터 6마리 그림 확인 */
    @Test
    fun newCritters() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize().background(Ui.bgTop).padding(16.dp),
            ) {
                listOf(
                    listOf(com.bubble.game.game.Animal.OCTOPUS, com.bubble.game.game.Animal.PENGUIN, com.bubble.game.game.Animal.PANDA),
                    listOf(com.bubble.game.game.Animal.DRAGON, com.bubble.game.game.Animal.RABBIT, com.bubble.game.game.Animal.UNICORN),
                ).forEach { row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEach { a ->
                            Critter(Side.CAT, androidx.compose.ui.Modifier.size(120.dp), skin = a, ribbons = if (a == com.bubble.game.game.Animal.RABBIT) 5 else 0)
                        }
                    }
                }
                SetBubble(Side.CAT, 3, "1번 세트", BubbleMode.NORMAL, 150.dp, skin = com.bubble.game.game.Animal.RABBIT, ribbons = 2)
            }
        }
        shot("new_critters", 300)
    }

    /** 긴 화면으로 상점 전체 (묶음 할인 버튼 확인) */
    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h4800dp-xhdpi")
    fun shopTall() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent { BubbleApp(vm) }
        vm.openShop()
        shot("shop_tall", 900)
    }

    /** 황금 햄스터로 이기면 금가루 폭죽 + 금화 비 */
    @Test
    fun winGold() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 0), ham = listOf(0, 0)), mySide = Side.CAT, finished = true, winner = Side.CAT,
                reason = "normal", busy = false, showIntro = false, mySkin = com.bubble.game.game.Animal.GOLD_HAMSTER)
        }
        shot("win_gold", 900)
    }

    /** 눈송이 햄스터: 내 시간 게이지가 얼음 테마 */
    @Test
    fun timerSnow() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 3), ham = listOf(1, 2)), mySide = Side.CAT, turn = Side.CAT, busy = false, showIntro = false,
                mySkin = com.bubble.game.game.Animal.SNOW_HAMSTER, turnSec = 15, turnDeadline = System.currentTimeMillis() + 11_000)
        }
        shot("timer_snow", 600)
    }

    /** 눈송이 햄스터로 이기면 눈보라 */
    @Test
    fun winSnow() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 0), ham = listOf(0, 0)), mySide = Side.CAT, finished = true, winner = Side.CAT,
                reason = "normal", busy = false, showIntro = false, mySkin = com.bubble.game.game.Animal.SNOW_HAMSTER)
        }
        shot("win_snow", 900)
    }

    @Test
    fun winRabbitRibbons() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 0), ham = listOf(0, 0)), mySide = Side.CAT, finished = true, winner = Side.CAT,
                reason = "normal", busy = false, showIntro = false, online = true, mySkin = com.bubble.game.game.Animal.RABBIT,
                myRibbons = 2, rabbitStreak = 3, rabbitCoins = 5)
        }
        shot("win_rabbit_ribbons", 2500)
    }

    @Test
    fun lobbyRabbit() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215).copy(equipped = com.bubble.game.game.Animal.RABBIT, winStreak = 4, tutorialSeen = true)) }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_rabbit", 900)
    }

    private fun winShot(name: String, skin: com.bubble.game.game.Animal?) {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 0), ham = listOf(0, 0)), mySide = Side.CAT, finished = true, winner = Side.CAT,
                reason = "normal", busy = false, showIntro = false, mySkin = skin)
        }
        shot(name, 1500)
    }

    /** 💰 결과 화면: 져도 이번 판 캐릭터 효과로 받은 💰 */
    @Test
    fun loseLuckyCoins() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(0, 0), ham = listOf(2, 0)), mySide = Side.CAT, finished = true, winner = Side.HAMSTER,
                reason = "normal", busy = false, showIntro = false, online = true, scoreDelta = -10,
                mySkin = com.bubble.game.game.Animal.LUCKY_CAT, gameCoins = 7)
        }
        shot("lose_lucky_coins", 900)
    }

    /** 💰 결과 화면: 이기면 캐릭터 효과 + 승리 보상 합계 */
    @Test
    fun winCoinsTotal() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 0), ham = listOf(0, 0)), mySide = Side.CAT, finished = true, winner = Side.CAT,
                reason = "normal", busy = false, showIntro = false, online = true, scoreDelta = 36,
                mySkin = com.bubble.game.game.Animal.UNICORN, gameCoins = 4, unicornCoins = 3)
        }
        shot("win_coins_total", 900)
    }

    @Test fun winPenguin() = winShot("win_penguin", com.bubble.game.game.Animal.PENGUIN)
    @Test fun winUnicorn() = winShot("win_unicorn", com.bubble.game.game.Animal.UNICORN)
    @Test fun winDragon() = winShot("win_dragon", com.bubble.game.game.Animal.DRAGON)
    @Test fun winOctopus() = winShot("win_octopus", com.bubble.game.game.Animal.OCTOPUS)
    @Test fun winBlackCat() = winShot("win_blackcat", com.bubble.game.game.Animal.BLACK_CAT)
    @Test fun winCat() = winShot("win_cat", null)

    /** 펭귄 공격: 썰매를 타고 날아감 */
    @Test
    fun penguinShot() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { BubbleApp(vm) }
        vm.startAiGame(com.bubble.game.game.AiLevel.NORMAL)
        rule.mainClock.advanceTimeBy(800)
        vm.debugSetGame {
            it.copy(board = Board(cat = listOf(2, 3), ham = listOf(2, 2)), mySide = Side.CAT, turn = Side.HAMSTER, busy = false, showIntro = false,
                opponentSkin = com.bubble.game.game.Animal.PENGUIN)
        }
        rule.mainClock.advanceTimeBy(500)
        vm.debugSetGame { it.copy(board = Board(cat = listOf(4, 3), ham = listOf(2, 2)), turn = Side.CAT, lastShot = Shot(Side.HAMSTER, 0, true, 0, 1)) }
        shot("penguin_fly", 360)
    }

    @Test
    fun rankDown() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(3990), rankDown = com.bubble.game.game.Rank.RUBY to 3990) }
        rule.setContent { BubbleApp(vm) }
        shot("rank_down", 1200)
    }

    /** 글자 크기 1.3배 폰에서도 묶음 가격이 잘리지 않는지 */
    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h3200dp-xhdpi")
    fun shopBigFont() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1215)) }
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1.3f)) { BubbleApp(vm) }
        }
        vm.openShop()
        shot("shop_bigfont", 900)
    }
}
