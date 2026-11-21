package com.bubble.game.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/**
 * 승리 결과 공유 카드: 1080×1080 이미지를 그려서 메시지(추천 링크)와 함께 공유 창을 엽니다.
 * 이미지는 cache/shared 에 저장하고 FileProvider로 다른 앱에 넘깁니다 (res/xml/file_paths.xml).
 */
object ShareCard {

    data class Win(
        /** 큰 제목 (예: "승리!") */
        val title: String,
        /** 예: "말랑고양이12  vs  보송햄찌42" */
        val matchLine: String,
        /** 예: "+33점 · 🟡 금" */
        val scoreLine: String,
        /** 내 캐릭터 이모지 (🐱/🐹) */
        val emoji: String,
        /** 카드 아래 안내 (예: "추천 코드 ABC234 · 둘 다 +30점") */
        val footer: String,
        /** 공유 메시지 (링크 포함) */
        val message: String,
    )

    private const val SIZE = 1080

    fun share(context: Context, win: Win) {
        val bmp = draw(win)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "bubble_win.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, win.message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun draw(win: Win): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = SIZE.toFloat()
        // 분홍 → 보라 배경
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s, s, 0xFFFFD6EA.toInt(), 0xFFD9D2FF.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, s, s, bg)
        // 반짝이 점들
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
        listOf(0.12f to 0.15f, 0.85f to 0.12f, 0.9f to 0.55f, 0.08f to 0.62f, 0.2f to 0.9f, 0.8f to 0.88f).forEach { (x, y) ->
            c.drawCircle(x * s, y * s, 26f, dot)
        }
        // 흰 카드
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        c.drawRoundRect(RectF(80f, 110f, s - 80f, s - 110f), 72f, 72f, card)

        fun text(t: String, y: Float, size: Float, color: Int, bold: Boolean = true) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            // 너무 길면 카드 안에 들어갈 때까지 줄임
            while (p.measureText(t) > s - 220f && p.textSize > 24f) p.textSize -= 2f
            c.drawText(t, s / 2, y, p)
        }
        text("🏆 ${win.emoji}", 330f, 190f, 0xFF000000.toInt())
        text(win.title, 480f, 120f, 0xFFE0518F.toInt())
        text(win.matchLine, 580f, 52f, 0xFF4A3B5C.toInt(), bold = false)
        text(win.scoreLine, 690f, 76f, 0xFF7C6CF2.toInt())
        text("Bubble 🐱🫧🐹", 830f, 56f, 0xFF4A3B5C.toInt())
        text(win.footer, 910f, 42f, 0xFFE0518F.toInt())
        return bmp
    }
}
