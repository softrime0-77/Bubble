package com.bubble.game.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 화면 언어 (한국어 / 영어).
 * - 처음에는 기기 언어가 한국어면 한국어, 그 외에는 영어.
 * - 로비의 언어 메뉴에서 바꾸면 저장되어 다음 실행에도 유지됩니다.
 * Compose 상태라서 바꾸는 즉시 화면이 다시 그려집니다.
 */
object Lang {
    enum class Code(val label: String) { KO("한국어"), EN("English") }

    private const val PREFS = "bubble_prefs"
    private const val KEY = "lang"

    var current by mutableStateOf(detect())
        private set

    val isKo: Boolean get() = current == Code.KO

    fun detect(): Code = if (Locale.getDefault().language == "ko") Code.KO else Code.EN

    /** 앱 시작 시 저장된 선택을 불러옵니다. */
    fun init(context: Context) {
        val saved = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        current = Code.entries.firstOrNull { it.name == saved } ?: detect()
    }

    fun set(context: Context, code: Code) {
        current = code
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code.name).apply()
        }
    }

    /** 테스트용 */
    internal fun setForTest(code: Code) {
        current = code
    }
}

/** 현재 언어에 맞는 문구. */
fun tr(ko: String, en: String): String = if (Lang.isKo) ko else en
