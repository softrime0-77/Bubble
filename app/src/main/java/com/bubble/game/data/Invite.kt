package com.bubble.game.data

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * 친구 초대 링크.
 * - 링크: https://softrime0-77.github.io/Bubble/invite.html?code=ABC234 (초대 페이지)
 * - 앱 열기: bubble://invite?code=ABC234
 * - Play 설치: 스토어 링크의 referrer=code=ABC234 를 첫 실행 때 Install Referrer로 읽음
 * 받은 코드는 로그인할 때까지 저장해 두었다가 자동으로 입력합니다.
 */
object Invite {
    const val PAGE = "https://softrime0-77.github.io/Bubble/invite.html"
    private const val PREFS = "bubble_prefs"
    private const val KEY_PENDING = "pending_invite"
    private const val KEY_REFERRER_CHECKED = "referrer_checked"

    fun link(code: String): String = "$PAGE?code=$code"

    /** 코드 정리: 영문 대문자/숫자 6자리만 */
    fun normalize(raw: String?): String? {
        val c = raw?.trim()?.uppercase()?.filter { it.isLetterOrDigit() } ?: return null
        return c.takeIf { it.length == 6 }
    }

    /** 초대 링크(앱 링크·웹 링크)에서 코드 꺼내기 */
    fun fromUri(uri: Uri?): String? {
        uri ?: return null
        val isApp = uri.scheme == "bubble" && uri.host == "invite"
        val isWeb = uri.scheme == "https" && uri.host == "softrime0-77.github.io" && uri.path?.startsWith("/Bubble/invite") == true
        return if (isApp || isWeb) normalize(uri.getQueryParameter("code")) else null
    }

    fun savePending(context: Context, code: String) {
        prefs(context).edit().putString(KEY_PENDING, code).apply()
    }

    fun pending(context: Context): String? = prefs(context).getString(KEY_PENDING, null)

    fun clearPending(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }

    /** 첫 실행 때 한 번: Play 스토어 설치 정보에서 초대 코드 읽기 */
    fun checkInstallReferrer(context: Context, onCode: (String) -> Unit) {
        val p = prefs(context)
        if (p.getBoolean(KEY_REFERRER_CHECKED, false)) return
        val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val referrer = runCatching { client.installReferrer.installReferrer }.getOrNull()
                        // referrer 형식: "code=ABC234" (스토어 링크의 referrer 파라미터)
                        val found = referrer?.split("&")
                            ?.firstOrNull { it.startsWith("code=") }
                            ?.removePrefix("code=")
                            ?.let { normalize(it) }
                        if (found != null) onCode(found)
                    }
                    p.edit().putBoolean(KEY_REFERRER_CHECKED, true).apply()
                    runCatching { client.endConnection() }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
