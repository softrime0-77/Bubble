package com.bubble.game.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.bubble.game.game.Board
import com.bubble.game.game.Side
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

    private fun shot(name: String, advanceMs: Long = 600) {
        rule.mainClock.advanceTimeBy(advanceMs)
        val dir = System.getenv("SCREENSHOT_DIR") ?: return
        val view = rule.activity.window.decorView.rootView
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
        owned = setOf(com.bubble.game.game.Animal.SHEEP), equipped = com.bubble.game.game.Animal.SHEEP,
    )

    @Test
    fun lobbyWithRanks() {
        rule.mainClock.autoAdvance = false
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.debugSetLobby {
            it.copy(
                onlineAvailable = true, loading = false, profile = richProfile(1215),
                leaderboard = listOf(1420, 1215, 1150, 1040, 980).mapIndexed { i, sc ->
                    com.bubble.game.data.Profile("u$i", listOf("다이아냥", "말랑고양이42", "은빛햄찌", "구리댕댕", "흙수저양")[i], sc, 0, 0)
                }.let { l -> l.toMutableList().also { it[1] = richProfile(1215) } },
            )
        }
        rule.setContent { BubbleApp(vm) }
        shot("lobby_ranks", 900)
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
        vm.debugSetLobby { it.copy(onlineAvailable = true, loading = false, profile = richProfile(1310), rankUpScore = 1310) }
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
                mySkin = com.bubble.game.game.Animal.GIRAFFE, opponentSkin = com.bubble.game.game.Animal.DOG,
                log = listOf("햄스터: 2번 세트(3)로 상대 2번 세트 공격 → 💥 펑!", "고양이: 1번 세트 분할 → 2마리, 2마리 ✂️ (분할 -1, 남은 1회)"),
            )
        }
        shot("game", 1200)
    }
}
