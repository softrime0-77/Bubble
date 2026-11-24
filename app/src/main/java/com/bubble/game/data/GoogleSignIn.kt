package com.bubble.game.data

import com.bubble.game.i18n.tr

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Credential Manager로 구글 계정을 고르게 하고, Firebase 로그인에 쓸 ID 토큰을 받습니다. */
object GoogleSignIn {

    /**
     * google-services 플러그인이 만들어 주는 웹 클라이언트 ID.
     * Firebase 콘솔에서 Google 로그인을 켠 뒤 받은 google-services.json에만 들어 있습니다.
     */
    fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }

    /** [activityContext]는 계정 선택 창을 띄울 Activity여야 합니다. */
    suspend fun requestIdToken(activityContext: Context, webClientId: String): String {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error(tr("구글 계정 정보를 받지 못했어요", "Could not get your Google account", zh = "无法获取你的 Google 账号", hi = "आपका Google खाता नहीं मिल सका", es = "No se pudo obtener tu cuenta de Google", fr = "Impossible de récupérer ton compte Google", ar = "تعذّر الحصول على حسابك في Google", de = "Dein Google-Konto konnte nicht abgerufen werden", pt = "Não foi possível obter sua conta Google", ru = "Не удалось получить аккаунт Google", ja = "Googleアカウントを取得できませんでした"))
    }

    /**
     * 예전 방식 구글 로그인 창 (Credential Manager가 "[16] Account reauth failed" 등으로 실패할 때 대체).
     * 실패하면 상태 코드(10 = 앱 서명/OAuth 설정, 12500 = 동의 화면 등)를 알 수 있습니다.
     */
    @Suppress("DEPRECATION")
    private fun legacyClient(context: Context, webClientId: String) =
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(webClientId).requestEmail().build(),
        )

    fun legacyIntent(context: Context, webClientId: String): Intent = legacyClient(context, webClientId).signInIntent

    /** 로그아웃할 때 예전 로그인 창도 계정 기억을 지움 (다음에 계정을 다시 고를 수 있게) */
    fun legacySignOut(context: Context, webClientId: String) {
        runCatching { legacyClient(context, webClientId).signOut() }
    }

    /** 예전 로그인 창 결과 → ID 토큰. 실패하면 상태 코드가 담긴 예외 */
    @Suppress("DEPRECATION")
    fun legacyToken(data: Intent?): String {
        val account = try {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            throw LegacySignInException(e.statusCode)
        }
        return account?.idToken ?: error(tr("구글 계정 정보를 받지 못했어요", "Could not get your Google account"))
    }

    /** 예전 로그인 창 실패: 12501 = 사용자가 닫음 */
    class LegacySignInException(val code: Int) : Exception("code $code")
}
