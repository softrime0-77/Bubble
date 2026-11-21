import Foundation
import GoogleMobileAds
import UIKit
import UserMessagingPlatform

/// AdMob 광고 (안드로이드 AdManager와 같은 규칙).
/// - 광고 동의(UMP)를 먼저 받고, 광고를 요청해도 될 때만 SDK를 시작
/// - 게임 중에는 띄우지 않고, 게임이 끝나 로비로 돌아갈 때만
/// - 일반 전면 광고는 2판마다, 최소 60초 간격
/// - 보상형 전면 광고는 보상 안내 화면(건너뛰기 가능)을 먼저 보여준 뒤에만 (화면 쪽에서 처리)
/// 광고 단위 ID는 Info.plist(project.yml)의 BubbleInterstitialId / BubbleRewardedId (실제 AdMob 광고 단위).
@MainActor
final class Ads: NSObject, ObservableObject, GADFullScreenContentDelegate {
    static let shared = Ads()

    private static let everyNGames = 2
    private static let minGap: TimeInterval = 60

    @Published private(set) var rewardedReady = false
    /// 광고 제거를 산 계정이면 true (전면 광고를 띄우지 않음. 보상형 광고는 원할 때만)
    var noAds = false
    @Published private(set) var privacyOptionsRequired = false

    private var started = false
    private var interstitial: GADInterstitialAd? = nil
    private var rewarded: GADRewardedInterstitialAd? = nil
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var gamesSinceInterstitial = 0
    private var lastFullScreenAt = Date.distantPast
    private var onDismiss: (() -> Void)? = nil

    private var interstitialId: String { Bundle.main.object(forInfoDictionaryKey: "BubbleInterstitialId") as? String ?? "" }
    private var rewardedId: String { Bundle.main.object(forInfoDictionaryKey: "BubbleRewardedId") as? String ?? "" }

    /// 앱 시작 때 한 번: 동의 확인 → 필요하면 동의 창 → 광고 SDK 시작
    func start() {
        let consent = UMPConsentInformation.sharedInstance
        consent.requestConsentInfoUpdate(with: UMPRequestParameters()) { [weak self] _ in
            Task { @MainActor in
                guard let self, let root = Self.rootViewController() else { return }
                UMPConsentForm.loadAndPresentIfRequired(from: root) { _ in
                    Task { @MainActor in
                        self.updatePrivacyFlag()
                        if consent.canRequestAds { self.startSdk() }
                    }
                }
            }
        }
        // 이전에 받은 동의로 바로 시작할 수 있으면 기다리지 않음
        if consent.canRequestAds { startSdk() }
    }

    func showPrivacyOptions() {
        guard let root = Self.rootViewController() else { return }
        UMPConsentForm.presentPrivacyOptionsForm(from: root) { [weak self] _ in
            Task { @MainActor in self?.updatePrivacyFlag() }
        }
    }

    private func updatePrivacyFlag() {
        privacyOptionsRequired = UMPConsentInformation.sharedInstance.privacyOptionsRequirementStatus == .required
    }

    private func startSdk() {
        guard !started else { return }
        started = true
        GADMobileAds.sharedInstance().start { [weak self] _ in
            Task { @MainActor in
                self?.loadInterstitial()
                self?.loadRewarded()
            }
        }
    }

    private func loadInterstitial() {
        guard started, interstitial == nil, !loadingInterstitial, !interstitialId.isEmpty else { return }
        loadingInterstitial = true
        GADInterstitialAd.load(withAdUnitID: interstitialId, request: GADRequest()) { [weak self] ad, _ in
            Task { @MainActor in
                self?.interstitial = ad
                self?.loadingInterstitial = false
            }
        }
    }

    private func loadRewarded() {
        guard started, rewarded == nil, !loadingRewarded, !rewardedId.isEmpty else { return }
        loadingRewarded = true
        GADRewardedInterstitialAd.load(withAdUnitID: rewardedId, request: GADRequest()) { [weak self] ad, _ in
            Task { @MainActor in
                self?.rewarded = ad
                self?.loadingRewarded = false
                self?.rewardedReady = ad != nil
            }
        }
    }

    /// 게임이 끝나 로비로 갈 때. 빈도 제한을 통과하면 전면 광고, 닫히면(또는 안 띄우면 바로) done
    func maybeShowInterstitial(_ done: @escaping () -> Void) {
        if noAds { done(); return }
        gamesSinceInterstitial += 1
        let allowed = gamesSinceInterstitial >= Self.everyNGames && Date().timeIntervalSince(lastFullScreenAt) >= Self.minGap
        guard allowed, let ad = interstitial, let root = Self.rootViewController() else {
            done()
            loadInterstitial()
            return
        }
        interstitial = nil
        gamesSinceInterstitial = 0
        lastFullScreenAt = Date()
        onDismiss = done
        ad.fullScreenContentDelegate = self
        ad.present(fromRootViewController: root)
    }

    /// 보상형 전면 광고. 시청 확인은 AdMob 서버가 우리 서버(admobSsv)로 직접 보냄 (uid, gameId를 실어 보냄).
    /// earned는 화면 안내용이고, 점수는 서버가 확인 후 돌려줌.
    func showRewarded(uid: String, gameId: String, earned: @escaping () -> Void, done: @escaping () -> Void) {
        guard let ad = rewarded, let root = Self.rootViewController() else {
            done()
            loadRewarded()
            return
        }
        rewarded = nil
        rewardedReady = false
        let ssv = GADServerSideVerificationOptions()
        ssv.userIdentifier = uid
        ssv.customRewardString = gameId
        ad.serverSideVerificationOptions = ssv
        // 보상형 광고를 본 판에는 일반 전면 광고를 이어서 띄우지 않음
        gamesSinceInterstitial = 0
        lastFullScreenAt = Date()
        onDismiss = done
        ad.fullScreenContentDelegate = self
        ad.present(fromRootViewController: root) { earned() }
    }

    nonisolated func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        Task { @MainActor in self.finishFullScreen() }
    }

    nonisolated func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        Task { @MainActor in self.finishFullScreen() }
    }

    private func finishFullScreen() {
        let done = onDismiss
        onDismiss = nil
        done?()
        loadInterstitial()
        loadRewarded()
    }

    static func rootViewController() -> UIViewController? {
        var vc = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }
}
