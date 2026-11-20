package com.bubble.game.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 화면 언어. 한국어 + 세계에서 많이 쓰는 언어 10개.
 * - 처음에는 기기 언어를 따르고, 목록에 없으면 영어.
 * - 로비의 언어 메뉴에서 바꾸면 저장되어 다음 실행에도 유지됩니다.
 * Compose 상태라서 바꾸는 즉시 화면이 다시 그려집니다.
 */
object Lang {
    /** 순서가 [pick]에 넘기는 목록 순서와 같아야 합니다. */
    enum class Code(val label: String, val tag: String, val rtl: Boolean = false) {
        KO("한국어", "ko"),
        EN("English", "en"),
        ZH("中文", "zh"),
        HI("हिन्दी", "hi"),
        ES("Español", "es"),
        FR("Français", "fr"),
        AR("العربية", "ar", rtl = true),
        DE("Deutsch", "de"),
        PT("Português", "pt"),
        RU("Русский", "ru"),
        JA("日本語", "ja"),
    }

    private const val PREFS = "bubble_prefs"
    private const val KEY = "lang"

    var current by mutableStateOf(detect())
        private set

    val isKo: Boolean get() = current == Code.KO

    fun detect(): Code {
        val lang = Locale.getDefault().language
        return Code.entries.firstOrNull { it.tag == lang } ?: Code.EN
    }

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

    /** [names]는 [Code] 순서(ko, en, zh, hi, es, fr, ar, de, pt, ru, ja)의 목록. 없으면 영어. */
    fun pick(names: List<String>): String = names.getOrNull(current.ordinal) ?: names[1]

    /** 테스트용 */
    internal fun setForTest(code: Code) {
        current = code
    }
}

/** 현재 언어에 맞는 문구. 빠진 언어는 영어로 보여줍니다. */
fun tr(
    ko: String,
    en: String,
    zh: String = en,
    hi: String = en,
    es: String = en,
    fr: String = en,
    ar: String = en,
    de: String = en,
    pt: String = en,
    ru: String = en,
    ja: String = en,
): String = when (Lang.current) {
    Lang.Code.KO -> ko
    Lang.Code.EN -> en
    Lang.Code.ZH -> zh
    Lang.Code.HI -> hi
    Lang.Code.ES -> es
    Lang.Code.FR -> fr
    Lang.Code.AR -> ar
    Lang.Code.DE -> de
    Lang.Code.PT -> pt
    Lang.Code.RU -> ru
    Lang.Code.JA -> ja
}
