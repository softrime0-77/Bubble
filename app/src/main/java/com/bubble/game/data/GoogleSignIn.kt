package com.bubble.game.data

import com.bubble.game.i18n.tr

import android.content.Context
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
}
