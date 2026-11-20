package com.bubble.game.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bubble.game.game.Animal
import com.bubble.game.game.Side
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 플레이 스토어 등록용 이미지. mdpi(1dp = 1px)로 정확한 픽셀 크기를 만듭니다.
 * STORE_ASSET_DIR 환경변수가 있을 때만 파일로 저장합니다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String) {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(700)
        val dir = System.getenv("STORE_ASSET_DIR") ?: return
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w512dp-h512dp-mdpi")
    fun icon512() {
        rule.mainClock.autoAdvance = false
        rule.setContent { StoreIcon() }
        save("icon_512")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h500dp-land-mdpi")
    fun featureKo() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FeatureGraphic(title = "Bubble", subtitle = "냥냥 vs 햄햄 방울 대전", tagline = "익명의 상대와 1:1 방울 터뜨리기!") }
        save("feature_1024x500_ko")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h500dp-land-mdpi")
    fun featureEn() {
        rule.mainClock.autoAdvance = false
        rule.setContent { FeatureGraphic(title = "Bubble", subtitle = "Cats vs Hamsters", tagline = "Pop your rival's bubbles in 1:1 battles!") }
        save("feature_1024x500_en")
    }
}

@Composable
private fun GlossyBubble(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2 * 0.96f
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.9f), tint.copy(alpha = 0.35f), tint.copy(alpha = 0.6f)),
                center = center + Offset(-r * 0.3f, -r * 0.35f),
                radius = r * 1.5f,
            ),
            r, center,
        )
        drawCircle(Color.White, r, center, style = Stroke(width = r * 0.05f))
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.13f, center + Offset(-r * 0.5f, -r * 0.55f))
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.06f, center + Offset(-r * 0.25f, -r * 0.73f))
    }
}

/** 앱 아이콘 512×512: 분홍 배경 + 큰 방울 속 고양이, 옆에서 햄스터가 빼꼼. */
@Composable
private fun StoreIcon() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFFFC2DE), Color(0xFFD8C9FF)))),
        contentAlignment = Alignment.Center,
    ) {
        // 작은 장식 방울
        GlossyBubble(70.dp, Color(0xFFC8A2FF), Modifier.align(Alignment.TopEnd).offset(x = (-40).dp, y = 40.dp))
        GlossyBubble(44.dp, Color(0xFFFFB86B), Modifier.align(Alignment.BottomStart).offset(x = 48.dp, y = (-56).dp))
        Box(contentAlignment = Alignment.Center) {
            GlossyBubble(380.dp, Color(0xFFFFB86B))
            Critter(Side.CAT, Modifier.size(250.dp).offset(y = 6.dp), happy = true)
        }
        Critter(
            Side.HAMSTER,
            Modifier.align(Alignment.BottomEnd).offset(x = (-26).dp, y = (-22).dp).size(150.dp).rotate(-12f),
        )
    }
}

/** 그래픽 이미지 1024×500: 제목 + 방울 속 캐릭터들. */
@Composable
private fun FeatureGraphic(title: String, subtitle: String, tagline: String) {
    BubbleBackground {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(470.dp)) {
                Text(title, fontSize = 110.sp, fontWeight = FontWeight.Black, color = Ui.primaryDark, lineHeight = 110.sp)
                Text(subtitle, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Ui.text)
                Spacer(Modifier.height(14.dp))
                Text(
                    tagline,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier
                        .background(Ui.primary, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(x = (-90).dp, y = 30.dp), contentAlignment = Alignment.Center) {
                    GlossyBubble(270.dp, Color(0xFFFFB86B))
                    Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                        Critter(Side.CAT, Modifier.size(120.dp), happy = true)
                        Critter(Side.CAT, Modifier.size(120.dp), phase = 3)
                    }
                }
                Box(Modifier.offset(x = 130.dp, y = (-60).dp), contentAlignment = Alignment.Center) {
                    GlossyBubble(220.dp, Color(0xFFC8A2FF))
                    Critter(Side.HAMSTER, Modifier.size(140.dp))
                }
                Row(
                    Modifier.offset(x = 110.dp, y = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Critter(Side.CAT, Modifier.size(72.dp), skin = Animal.SHEEP)
                    Critter(Side.CAT, Modifier.size(72.dp), skin = Animal.DOG)
                    Critter(Side.CAT, Modifier.size(72.dp), skin = Animal.GIRAFFE)
                }
            }
        }
    }
}
