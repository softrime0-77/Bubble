package com.bubble.game.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.bubble.game.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob 광고 관리.
 *
 * 정책 지키기:
 * - 광고 동의(UMP)를 먼저 받고, 동의 결과상 광고를 요청할 수 있을 때만 SDK를 시작합니다.
 * - 게임 중에는 광고를 띄우지 않고, 게임이 끝나 로비로 돌아갈 때만 띄웁니다.
 * - 일반 전면 광고는 [INTERSTITIAL_EVERY_N_GAMES]판마다, 최소 [INTERSTITIAL_MIN_GAP_MS] 간격으로만.
 * - 보상형 전면 광고는 보상 안내 화면(건너뛰기 가능)을 먼저 보여준 뒤에만 띄웁니다 (화면 쪽에서 처리).
 * - 디버그 빌드는 구글 테스트 광고 단위를 씁니다 (build.gradle.kts).
 */
object AdManager {

    private const val INTERSTITIAL_EVERY_N_GAMES = 2
    private const val INTERSTITIAL_MIN_GAP_MS = 60_000L

    private val started = AtomicBoolean(false)
    private lateinit var consent: ConsentInformation

    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var rewarded: RewardedInterstitialAd? = null
    private var loadingRewarded = false

    private var gamesSinceInterstitial = 0
    private var lastFullScreenAdAt = 0L

    private val _rewardedReady = MutableStateFlow(false)
    /** 보상형 광고가 준비됐는지 (결과 화면에서 "8점 돌려받기" 안내를 보여줄지 결정). */
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** 지역상 "광고 개인정보 설정" 버튼을 보여줘야 하는지 (EEA/영국 등). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** 앱 시작 시 한 번 호출: 동의 확인 → 필요하면 동의 팝업 → 광고 SDK 시작. */
    fun start(activity: Activity) {
        consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updatePrivacyFlag()
                    if (consent.canRequestAds()) initSdk(activity)
                }
            },
            { /* 동의 정보 갱신 실패: 이전 동의로 광고를 요청할 수 있으면 진행 */ },
        )
        // 이전 세션에서 받은 동의로 바로 시작할 수 있으면 기다리지 않음
        if (consent.canRequestAds()) initSdk(activity)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updatePrivacyFlag() }
    }

    private fun updatePrivacyFlag() {
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initSdk(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        MobileAds.initialize(app) {
            loadInterstitial(app)
            loadRewarded(app)
        }
    }

    private fun loadInterstitial(context: Context) {
        if (!started.get() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            context, BuildConfig.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    loadingInterstitial = false
                }
            },
        )
    }

    private fun loadRewarded(context: Context) {
        if (!started.get() || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedInterstitialAd.load(
            context, BuildConfig.REWARDED_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewarded = ad
                    loadingRewarded = false
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    loadingRewarded = false
                    _rewardedReady.value = false
                }
            },
        )
    }

    /**
     * 게임이 끝나고 로비로 갈 때 호출. 빈도 제한을 통과하면 전면 광고를 띄우고,
     * 광고가 닫힌 뒤(또는 띄우지 않으면 바로) [onDone]을 부릅니다.
     */
    fun maybeShowInterstitial(activity: Activity, onDone: () -> Unit) {
        gamesSinceInterstitial++
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val allowed = gamesSinceInterstitial >= INTERSTITIAL_EVERY_N_GAMES &&
            now - lastFullScreenAdAt >= INTERSTITIAL_MIN_GAP_MS
        if (ad == null || !allowed) {
            onDone()
            loadInterstitial(activity)
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDone()
                loadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDone()
                loadInterstitial(activity)
            }
        }
        gamesSinceInterstitial = 0
        lastFullScreenAdAt = now
        ad.show(activity)
    }

    /**
     * 보상형 전면 광고. 시청 확인은 AdMob 서버가 우리 서버(admobSsv)로 직접 알려주며,
     * [uid]/[gameId]를 그 요청에 실어 보내 어떤 패배에 대한 보상인지 서버가 알 수 있게 합니다.
     * [onEarned]는 화면 안내용일 뿐, 점수는 서버가 검증 후 반영합니다.
     */
    fun showRewarded(activity: Activity, uid: String, gameId: String, onEarned: () -> Unit, onDone: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            onDone()
            loadRewarded(activity)
            return
        }
        rewarded = null
        _rewardedReady.value = false
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder().setUserId(uid).setCustomData(gameId).build(),
        )
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDone()
                loadRewarded(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDone()
                loadRewarded(activity)
            }
        }
        // 보상형 광고를 본 판에는 일반 전면 광고를 이어서 띄우지 않음
        gamesSinceInterstitial = 0
        lastFullScreenAdAt = SystemClock.elapsedRealtime()
        ad.show(activity) { onEarned() }
    }
}
