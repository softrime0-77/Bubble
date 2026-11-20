package com.bubble.game.data

import android.content.Context
import android.telephony.TelephonyManager
import com.bubble.game.i18n.Lang
import java.util.Locale

/** 상대에게 보여줄 내 나라. 통신사/SIM 국가 → 기기 지역 설정 순서 (위치 권한 필요 없음). */
object Country {
    fun detect(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOf(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        )
        return candidates.firstNotNullOfOrNull { c -> c?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } } }
    }

    /** "KR" → 🇰🇷 */
    fun flag(code: String?): String {
        val c = code?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } } ?: return "🌐"
        return c.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    /** "KR" → 화면 언어로 된 나라 이름 (예: 대한민국 / South Korea) */
    fun name(code: String?): String {
        val c = code?.uppercase() ?: return ""
        return runCatching { Locale("", c).getDisplayCountry(Locale(Lang.current.tag)) }.getOrDefault(c)
    }
}
