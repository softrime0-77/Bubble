import AuthenticationServices
import BubbleCore
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseFunctions
import GoogleSignIn
import SwiftUI
import UIKit

/// 프로필 (users/{uid})
struct Profile: Equatable {
    var uid: String
    var name: String
    var score: Int
    var wins: Int
    var losses: Int
    var potions: Int = 0
    var coins: Int = 0
    var admin: Bool = false
    var boosts: Int = 0
    var boostOn: Bool = false
    var animals: [Animal: Int] = [:]
    var equipped: Animal? = nil
    var refCode: String? = nil
    var referrals: Int = 0
    var referred: Bool = false
    /// 나라 (ISO 2글자, 랭킹 국기용)
    var country: String? = nil
    /// 산 유료 스킨들
    var skins: Set<Animal> = []
    /// 광고 제거를 샀는지 (전면 광고 안 나옴)
    var noAds = false
    /// 첫 포인트 구매를 했는지 (안 했으면 첫 구매 2배)
    var firstBuyDone = false
    /// 가입 시각 (ms). 첫 구매 2배는 가입 후 7일 동안만
    var createdAt: Int? = nil
    /// 💰 복냥이로 오늘 받은 포인트 (luckyDay가 오늘일 때만 유효)
    var luckyDay: String? = nil
    var luckyCoins = 0
    /// 🐉 아기 용으로 오늘 받은 포인트, 🐰 연승
    var dragonDay: String? = nil
    var dragonCoins = 0
    /// 🐰 연승 토끼로 오늘 받은 포인트 (rabbitDay가 오늘일 때만 유효)
    var rabbitDay: String? = nil
    var rabbitCoins = 0
    /// 🦄 유니콘 무지개 축복으로 오늘 받은 포인트 (unicornDay가 오늘일 때만 유효)
    var unicornDay: String? = nil
    var unicornCoins = 0
    var winStreak = 0
    /// ✏️ 닉네임: 첫 무료 변경을 썼는지, 가진 변경권 수
    var freeRenameUsed = false
    var renameTickets = 0
    /// 🎓 튜토리얼: 보상까지 받았는지 / 한 번 봤는지(건너뛰기 포함)
    var tutorialDone = false
    var tutorialSeen = false
    /// 출석: 마지막으로 받은 날(한국 시간)과 연속 일수
    var checkinDay: String? = nil
    var checkinStreak = 0
    /// 일일 미션 진행도 (missionDay가 오늘일 때만 유효)
    var missionDay: String? = nil
    var mPlay = 0
    var mWin = 0
    var mRandom = 0
    var mClaimed: Set<String> = []
    /// 광고로 받은 부활물약 (오늘 몇 번)
    var potionAdDay: String? = nil
    var potionAds = 0
    /// 주간 랭킹 보상 (아직 안 봤으면 팝업)
    var weeklyReward: WeeklyReward? = nil
    var rank: Rank { Rank.of(score) }
    func animalCount(_ a: Animal) -> Int { animals[a] ?? 0 }
}

/// 주간 랭킹 보상 안내
struct WeeklyReward: Equatable {
    var week: String
    var rank: Int
    var coins: Int
    var seen: Bool
}

/// 랭킹 한 줄 (내 주변 순위)
struct RankRow: Identifiable, Equatable {
    var uid: String
    var rank: Int
    var name: String
    var score: Int
    var country: String?
    var wins: Int? = nil
    var losses: Int? = nil
    /// 지금 보이는 캐릭터 (nil이면 기본 고양이)
    var look: Animal? = nil
    var id: String { uid }
}

/// 게임 문서 (games/{id}) — 안드로이드 OnlineRepository.toGame과 같은 필드
struct OnlineGameDoc {
    var id: String
    var catUid: String
    var hamUid: String
    var catName: String
    var hamName: String
    var catScore: Int
    var hamScore: Int
    var board: Board
    var turn: Side
    var finished: Bool
    var winner: Side?
    var reason: String
    var lastText: String
    var explodedSide: Side?
    var explodedIndex: Int
    var moveNo: Int
    var turnStartedAt: Date
    var catTurnMs: Int
    var hamTurnMs: Int
    var catDelta: Int?
    var hamDelta: Int?
    var catBonus: Int
    var hamBonus: Int
    var catSkin: Animal?
    var hamSkin: Animal?
    var catCountry: String?
    var hamCountry: String?
    var vsAi: AiLevel?
    var catBoost: Bool
    var hamBoost: Bool
    var vsFriend: Bool = false
    var rated: Bool = false
    /// 마지막 수와 둔 쪽 (방울이 날아가는 효과용)
    var lastMove: Move? = nil
    var lastBy: Side? = nil
    /// 마지막 공격에서 💰 복냥이 효과가 터졌는지
    var lastLucky: Bool = false
    /// 이번 수로 받은 💰 (복냥이 1 · 아기 용 2), 🐰 시작할 때 연승 리본 수
    var lastCoins: Int = 0
    var catStreak: Int = 0
    var hamStreak: Int = 0
    /// 🐰 이긴 쪽이 이 판으로 된 연승과 받은 💰 (연승 토끼일 때만)
    var rabbitStreak: Int = 0
    var rabbitCoins: Int = 0
    /// 🦄 이긴 쪽이 유니콘 무지개 축복으로 받은 💰
    var unicornCoins: Int = 0
    /// 이번 판에 캐릭터(복냥이·아기 용)로 받은 💰 합계
    var catCoins: Int = 0
    var hamCoins: Int = 0

    func side(of uid: String) -> Side? { uid == catUid ? .cat : uid == hamUid ? .hamster : nil }
    func name(_ s: Side) -> String { s == .cat ? catName : hamName }
    func score(_ s: Side) -> Int { s == .cat ? catScore : hamScore }
    func turnMs(_ s: Side) -> Int { s == .cat ? catTurnMs : hamTurnMs }
    func delta(_ s: Side) -> Int? { s == .cat ? catDelta : hamDelta }
    func bonus(_ s: Side) -> Int { s == .cat ? catBonus : hamBonus }
    func skin(_ s: Side) -> Animal? { s == .cat ? catSkin : hamSkin }
    func country(_ s: Side) -> String? { s == .cat ? catCountry : hamCountry }
    func boost(_ s: Side) -> Bool { s == .cat ? catBoost : hamBoost }
}

struct ServerError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Firebase 연결: 구글 로그인, 프로필, 매칭, 대전. 점수·판정은 모두 서버(Cloud Functions)가 함.
@MainActor
final class OnlineService: ObservableObject {
    static let shared = OnlineService()

    static let startScore = 1000
    static let turnLimitMs = 20_000
    static let timeoutGraceMs = 2_500
    static let matchTimeout: TimeInterval = 30
    private static let pollInterval: UInt64 = 2_500_000_000

    /// GoogleService-Info.plist가 있어야 온라인 기능이 켜짐
    let available: Bool

    @Published private(set) var uid: String? = nil
    @Published private(set) var profile: Profile? = nil
    @Published private(set) var leaderboard: [Profile] = []
    /// TOP 100 밖이면 내 순위와 내 위아래 10명
    @Published private(set) var myRank: Int? = nil
    @Published private(set) var aroundMe: [RankRow] = []
    /// 친구가 보낸 대전 링크의 코드 (로그인돼 있고 로비면 RootView가 바로 들어감)
    @Published var pendingBattle: String? = nil
    static let leaderboardSize = 100
    /// 친구를 기다리는 최대 시간 (서버 FRIEND_ROOM_TTL_MS와 같게)
    static let friendWait: TimeInterval = 600
    private var rankedAtScore: Int? = nil
    private var rankTask: Task<Void, Never>? = nil
    @Published private(set) var loading = false
    @Published var error: String? = nil
    @Published var welcome: (kind: String, score: Int)? = nil
    @Published private(set) var onlineCount: Int? = nil
    /// 상점·초대 등 버튼 처리 중
    @Published private(set) var busy = false
    /// 잠깐 보여주는 안내 (구매 완료 등)
    @Published var info: String? = nil
    /// 승급 축하 화면 (새 점수)
    @Published var rankUpScore: Int? = nil
    /// 계급이 내려갔을 때 안내 팝업: (예전 계급, 지금 점수)
    @Published var rankDown: (from: Rank, score: Int)? = nil
    /// 출석 완료 축하 팝업 (몇 일째, 💰, 💖 물약)
    @Published var checkin: CheckinReward? = nil
    /// 미션 완료 축하 팝업 (미션 이름, 💰)
    @Published var mission: MissionReward? = nil
    /// 🎓 튜토리얼 끝에 상점으로 들어왔으면 상점 맨 위에 캐릭터 안내
    @Published var shopTour = false

    private var appleNonce: String? = nil
    private var infoTask: Task<Void, Never>? = nil
    private static let pendingInviteKey = "pending_invite"

    private var listeners: [ListenerRegistration] = []
    private var sessionTasks: [Task<Void, Never>] = []

    private lazy var db = Firestore.firestore()
    private lazy var functions = Functions.functions(region: "asia-northeast3")

    var needsLogin: Bool { available && uid == nil && !loading }

    private init() {
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            available = true
        } else {
            available = false
        }
    }

    func start() {
        guard available else { return }
        if let user = Auth.auth().currentUser, !user.isAnonymous {
            startSession(user.uid)
        }
    }

    // MARK: 로그인

    func signInWithGoogle() async {
        guard available, let clientID = FirebaseApp.app()?.options.clientID,
              let root = UIApplication.shared.connectedScenes
                .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first
        else { return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        loading = true
        error = nil
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: root)
            guard let idToken = result.user.idToken?.tokenString else { throw ServerError(message: "no token") }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
            let auth = try await Auth.auth().signIn(with: credential)
            startSession(auth.user.uid)
        } catch {
            loading = false
            let code = (error as NSError).code
            if code != GIDSignInError.Code.canceled.rawValue {
                self.error = tr("구글 로그인에 실패했어요: \(error.localizedDescription)", "Google sign-in failed: \(error.localizedDescription)")
            }
        }
    }

    /// Sign in with Apple 버튼이 요청을 만들 때
    func prepareAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = AppleNonce.make()
        appleNonce = nonce
        request.requestedScopes = [.email]
        request.nonce = AppleNonce.sha256(nonce)
    }

    /// Sign in with Apple 결과 → Firebase 로그인
    func finishAppleSignIn(_ result: Result<ASAuthorization, Error>) async {
        error = nil
        switch result {
        case let .failure(e):
            if (e as? ASAuthorizationError)?.code != .canceled { self.error = appleFailed(e.localizedDescription) }
        case let .success(auth):
            guard available,
                  let cred = auth.credential as? ASAuthorizationAppleIDCredential,
                  let data = cred.identityToken, let token = String(data: data, encoding: .utf8),
                  let nonce = appleNonce
            else {
                error = appleFailed("no token")
                return
            }
            loading = true
            do {
                let credential = OAuthProvider.appleCredential(withIDToken: token, rawNonce: nonce, fullName: nil)
                let r = try await Auth.auth().signIn(with: credential)
                startSession(r.user.uid)
            } catch {
                loading = false
                self.error = appleFailed(error.localizedDescription)
            }
        }
    }

    private func appleFailed(_ why: String) -> String {
        tr("Apple 로그인에 실패했어요: \(why)", "Apple sign-in failed: \(why)", zh: "Apple 登录失败：\(why)",
           es: "Error al iniciar sesión con Apple: \(why)", fr: "Échec de la connexion Apple : \(why)",
           de: "Apple-Anmeldung fehlgeschlagen: \(why)", pt: "Falha ao entrar com a Apple: \(why)",
           ru: "Не удалось войти через Apple: \(why)", ja: "Appleでのログインに失敗しました：\(why)")
    }

    /// Apple로 로그인한 계정인지 (계정 삭제 때 토큰 취소 필요)
    var signedInWithApple: Bool {
        Auth.auth().currentUser?.providerData.contains { $0.providerID == "apple.com" } ?? false
    }

    /// 계정 삭제: Apple 계정이면 Apple 토큰을 먼저 취소하고, 서버가 대전 기권·데이터·로그인 계정을 지움
    func deleteAccount() async {
        guard available, uid != nil, !busy else { return }
        busy = true
        defer { busy = false }
        do {
            if signedInWithApple {
                let nonce = AppleNonce.make()
                let cred = try await AppleAuthorizer().authorize(nonce: nonce)
                if let data = cred.authorizationCode, let code = String(data: data, encoding: .utf8) {
                    try await Auth.auth().revokeToken(withAuthorizationCode: code)
                }
            }
            _ = try await call("deleteAccount")
            signOut()
            show(t("계정이 삭제됐어요. 이용해 주셔서 고마워요 🐾"))
        } catch {
            if (error as? ASAuthorizationError)?.code == .canceled { return }
            self.error = t("계정을 삭제하지 못했어요") + ": " + error.localizedDescription
        }
    }

    // MARK: 닉네임 · 상점 · 초대

    /// 🛠 관리자: 닉네임(정확히)이나 uid로 찾기. 빈 칸이면 나
    func adminFind(_ query: String) async -> [AdminUser] {
        do {
            let r = try await call("adminFindUser", ["query": query.trimmingCharacters(in: .whitespaces)])
            let list = r["users"] as? [[String: Any]] ?? []
            return list.compactMap { m in
                guard let uid = m["uid"] as? String else { return nil }
                return AdminUser(uid: uid, name: m["name"] as? String ?? "", score: Self.int(m["score"]) ?? 0,
                                 coins: Self.int(m["coins"]) ?? 0, potions: Self.int(m["potions"]) ?? 0)
            }
        } catch {
            self.error = error.localizedDescription
            return []
        }
    }

    /// 🛠 관리자: 점수 · 💰 · 💖 를 넣은 값으로 바꿈
    func adminSet(_ u: AdminUser, score: Int, coins: Int, potions: Int) async {
        await shopAction("🛠 \(u.name) 수정 완료 (점수 \(score) · 💰 \(coins) · 💖 \(potions))") {
            _ = try await call("adminSetPoints", ["uid": u.uid, "score": score, "coins": coins, "potions": potions])
        }
    }

    /// 닉네임 바꾸기 (서버): 첫 변경은 무료, 그다음은 ✏️ 변경권 1장
    func rename(_ raw: String) {
        let name = String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(12))
        guard uid != nil, !name.isEmpty else { return }
        Task {
            await shopAction(tr("✏️ 닉네임을 바꿨어요!", "✏️ Nickname changed!", zh: "✏️ 昵称已更改！", es: "✏️ ¡Apodo cambiado!", fr: "✏️ Pseudo modifié !",
                                de: "✏️ Spitzname geändert!", pt: "✏️ Apelido alterado!", ru: "✏️ Ник изменён!", ja: "✏️ ニックネームを変更しました！")) {
                _ = try await call("rename", ["name": name])
            }
        }
    }

    /// 잠깐 보여주는 안내
    func show(_ message: String) {
        info = message
        infoTask?.cancel()
        infoTask = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled { info = nil }
        }
    }

    /// 상점 동작. 성공하면 [sound] 효과음 (🛒 구매 · ✨ 장착)
    private func shopAction(_ success: String, sound: Music.Sfx? = nil, _ action: () async throws -> Void) async {
        guard !busy else { return }
        busy = true
        error = nil
        defer { busy = false }
        do {
            try await action()
            if let sound { Music.shared.sfx(sound) }
            if !success.isEmpty { show(success) }
        } catch {
            self.error = error.localizedDescription
        }
    }

    func buy(_ item: String, label: String, qty: Int = 1) async {
        await shopAction(tf("{0} 구매 완료! 🎉", label), sound: .buy) { _ = try await call("buyItem", ["item": item, "qty": qty]) }
    }

    func equip(_ animal: Animal?) async {
        await shopAction(animal.map { tf("{0} 장착!", $0.label) } ?? t("기본 캐릭터로 바꿨어요"), sound: .equip) {
            _ = try await call("equipAnimal", ["animal": animal.map { $0.rawValue as Any } ?? NSNull()])
        }
    }

    func setBoost(_ on: Bool) async {
        await shopAction(on ? t("×3 부스터를 켰어요! 다음 게임에서 1개 써요") : t("×3 부스터를 껐어요")) {
            _ = try await call("setBoost", ["on": on])
        }
    }

    /// 내 추천 코드 만들기 (서버가 프로필 refCode에 저장)
    func loadReferralCode() {
        guard profile?.refCode == nil else { return }
        Task { _ = try? await call("getReferralCode") }
    }

    func redeemReferral(_ raw: String) async {
        guard let code = Self.normalizeCode(raw) else { return }
        await shopAction(t("코드를 입력했어요! 나도 친구도 +30점 🎁")) {
            _ = try await call("redeemReferral", ["code": code])
        }
    }

    /// 초대 링크로 앱이 열렸을 때: bubble://invite?code=… 또는 https://softrime0-77.github.io/Bubble/invite.html?code=…
    func receiveInvite(_ url: URL) {
        guard let c = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }
        let isApp = c.scheme == "bubble" && c.host == "invite"
        let isWeb = c.scheme == "https" && c.host == "softrime0-77.github.io" && c.path.hasPrefix("/Bubble/invite")
        guard isApp || isWeb else { return }
        // 친구 대전 링크: …?battle=ABC234
        if let battle = Self.normalizeCode(c.queryItems?.first { $0.name == "battle" }?.value) {
            pendingBattle = battle
            if profile == nil { show(tr("로그인하면 친구와 대전을 시작해요 ⚔️", "Sign in to start the battle with your friend ⚔️", zh: "登录后即可开始与朋友对战 ⚔️", es: "Inicia sesión para empezar la batalla con tu amigo ⚔️", fr: "Connecte-toi pour lancer le duel avec ton ami ⚔️", de: "Melde dich an, um gegen deinen Freund zu spielen ⚔️", pt: "Entre para começar a batalha com seu amigo ⚔️", ru: "Войдите, чтобы начать бой с другом ⚔️", ja: "ログインすると友だちとの対戦が始まります ⚔️")) }
            return
        }
        guard let code = Self.normalizeCode(c.queryItems?.first { $0.name == "code" }?.value) else { return }
        UserDefaults.standard.set(code, forKey: Self.pendingInviteKey)
        if let p = profile { redeemPendingInvite(p) }
    }

    /// 로그인 후 받아 둔 초대 코드를 자동으로 입력
    private func redeemPendingInvite(_ p: Profile) {
        guard let code = UserDefaults.standard.string(forKey: Self.pendingInviteKey) else { return }
        UserDefaults.standard.removeObject(forKey: Self.pendingInviteKey)
        guard !p.referred, code != p.refCode else { return }
        Task {
            do {
                _ = try await call("redeemReferral", ["code": code])
                show(tf("초대 코드 {0}가 입력됐어요! 나도 친구도 +30점 🎁", code))
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    static func normalizeCode(_ raw: String?) -> String? {
        let c = (raw ?? "").uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
        return c.count == 6 ? c : nil
    }

    static func inviteLink(_ code: String) -> String { "https://softrime0-77.github.io/Bubble/invite.html?code=\(code)" }
    static func battleLink(_ code: String) -> String { "https://softrime0-77.github.io/Bubble/invite.html?battle=\(code)" }

    /// 기기의 나라 (VS 화면·랭킹 국기)
    static var deviceCountry: String { Locale.current.region?.identifier ?? "" }

    private var leaderboardAt = Date.distantPast

    /// 랭킹 TOP 100 새로고침 (10초 안에 다시 부르면 건너뜀, force면 바로)
    func refreshLeaderboard(force: Bool = false) async {
        guard uid != nil, force || Date().timeIntervalSince(leaderboardAt) > 10 else { return }
        leaderboardAt = Date()
        guard let snap = try? await db.collection("users").order(by: "score", descending: true)
            .limit(to: Self.leaderboardSize).getDocuments() else { return }
        leaderboard = snap.documents.compactMap(Self.profile(from:))
        refreshMyRanking()
    }

    /// TOP 100 밖이면 내 순위와 위아래 10명을 서버에서 불러옴 (내 점수가 바뀔 때만)
    private func refreshMyRanking() {
        guard let p = profile else { return }
        if leaderboard.contains(where: { $0.uid == p.uid }) {
            rankedAtScore = nil
            myRank = nil
            aroundMe = []
            return
        }
        guard leaderboard.count >= Self.leaderboardSize, rankedAtScore != p.score, rankTask == nil else { return }
        rankedAtScore = p.score
        rankTask = Task {
            defer { rankTask = nil }
            guard let r = try? await call("myRanking") else { rankedAtScore = nil; return }
            myRank = Self.int(r["rank"])
            aroundMe = ((r["rows"] as? [[String: Any]]) ?? []).compactMap { d in
                guard let uid = d["uid"] as? String, let rank = Self.int(d["rank"]) else { return nil }
                return RankRow(uid: uid, rank: rank, name: d["name"] as? String ?? "Player",
                               score: Self.int(d["score"]) ?? Self.startScore, country: d["country"] as? String,
                               wins: Self.int(d["wins"]), losses: Self.int(d["losses"]),
                               look: (d["look"] as? String).flatMap { Animal(rawValue: $0) })
            }
        }
    }

    // MARK: 출석 · 일일 미션 · 주간 보상

    /// 출석 7일 보상 💰 (functions/src/rewards.ts CHECKIN_COINS와 같게). 7일째는 💖도
    static let checkinCoins = [20, 20, 30, 30, 50, 50, 100]
    static let potionAdsPerDay = 3

    /// 한국 시간 날짜 (서버 dayKey와 같게)
    static func today(_ date: Date = Date()) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }

    func claimDaily() async {
        await shopAction("") {
            let r = try await call("claimDaily")
            let day = Self.int(r["day"]) ?? 1, coins = Self.int(r["coins"]) ?? 0
            let potion = r["potion"] as? Bool ?? false
            checkin = CheckinReward(day: day, coins: coins, potion: potion, panda: Self.int(r["panda"]) ?? 0)
        }
    }

    /// 미션 보상 받기 → 축하 팝업 ([label]은 팝업에 보여줄 미션 이름)
    /// 🎓 튜토리얼 끝(또는 건너뛰기). 처음 끝까지 했으면 💰 +20 · 💖 +1 (서버가 계정당 한 번)
    func finishTutorial(completed: Bool) async {
        guard uid != nil, profile != nil else { return }
        guard let r = try? await call("finishTutorial", ["completed": completed]) else { return }
        if r["reward"] as? Bool == true {
            mission = MissionReward(label: tr("🎓 튜토리얼 완료! 💖 부활물약 +1도 받았어요", "🎓 Tutorial done! You also got a 💖 revive potion",
                                              zh: "🎓 教程完成！还获得 💖 复活药水 1 个", ja: "🎓 チュートリアル完了！💖 復活薬も1つ"),
                                    coins: TutorialScript.coins)
        }
    }

    func claimMission(_ id: String, label: String) async {
        await shopAction("") {
            let r = try await call("claimMission", ["id": id])
            let coins = Self.int(r["coins"]) ?? 0
            mission = MissionReward(label: label, coins: coins)
        }
    }

    /// 첫 구매 2배 기간: 가입 후 7일 (functions/src/catalog.ts FIRST_BUY_WINDOW_MS와 같게)
    static let firstBuyWindowMs = 7 * 86_400_000

    /// 첫 구매 2배가 끝나기까지 남은 초. 이미 샀거나 기간이 지났으면 nil
    static func firstBuyLeft(_ p: Profile?, now: Date = Date()) -> TimeInterval? {
        guard let p, !p.firstBuyDone, let joined = p.createdAt else { return nil }
        let left = Double(joined + firstBuyWindowMs) / 1000 - now.timeIntervalSince1970
        return left > 0 ? left : nil
    }

    func ackWeeklyReward() {
        Task { _ = try? await call("ackWeeklyReward") }
    }

    // MARK: 친구 대전

    func createFriendRoom() async throws -> String {
        guard let code = try await call("createFriendRoom", ["country": Self.deviceCountry])["code"] as? String else {
            throw ServerError(message: "no code")
        }
        return code
    }

    /// 친구가 들어왔으면 gameId, 방이 끝났으면 expired
    func pollFriendRoom(_ code: String) async throws -> (gameId: String?, expired: Bool) {
        let r = try await call("pollFriendRoom", ["code": code])
        return (r["gameId"] as? String, r["expired"] as? Bool ?? false)
    }

    func cancelFriendRoom(_ code: String) async {
        _ = try? await call("cancelFriendRoom", ["code": code])
    }

    func joinFriendRoom(_ code: String) async throws -> String {
        guard let id = try await call("joinFriendRoom", ["code": code, "country": Self.deviceCountry])["gameId"] as? String else {
            throw ServerError(message: "no game")
        }
        return id
    }

    func signOut() {
        stopSession()
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        uid = nil
        profile = nil
        leaderboard = []
        myRank = nil
        aroundMe = []
        rankedAtScore = nil
        onlineCount = nil
    }

    private func stopSession() {
        listeners.forEach { $0.remove() }
        listeners = []
        sessionTasks.forEach { $0.cancel() }
        sessionTasks = []
    }

    private func startSession(_ id: String) {
        stopSession()
        uid = id
        loading = true
        sessionTasks.append(Task {
            do {
                let r = try await call("ensureProfile", ["name": Self.randomNickname(), "country": Self.deviceCountry])
                if let kind = r["kind"] as? String {
                    welcome = (kind, (r["score"] as? Int) ?? Self.startScore)
                }
            } catch {
                self.error = tr("서버에 연결하지 못했어요: \(error.localizedDescription)", "Could not connect to the server: \(error.localizedDescription)")
            }
            loading = false
        })
        listeners.append(db.collection("users").document(id).addSnapshotListener { [weak self] snap, _ in
            Task { @MainActor in self?.updateProfile(snap.flatMap(Self.profile(from:))) }
        })
        // 랭킹: 실시간 구독 대신 1분마다 새로고침 (상위권 점수가 바뀔 때마다 모두에게 다시 보내지 않게, 비용 절약)
        // 로비로 돌아올 때도 RootView가 refreshLeaderboard()를 부름
        sessionTasks.append(Task { [weak self] in
            while !Task.isCancelled {
                await self?.refreshLeaderboard(force: true)
                try? await Task.sleep(nanoseconds: 60_000_000_000)
            }
        })
        // 1분마다 접속 신호, 관리자는 30초마다 동시 접속자 수
        sessionTasks.append(Task {
            while !Task.isCancelled {
                try? await db.collection("presence").document(id).setData(["at": Int(Date().timeIntervalSince1970 * 1000)])
                try? await Task.sleep(nanoseconds: 60_000_000_000)
            }
        })
        sessionTasks.append(Task {
            while !Task.isCancelled {
                if profile?.admin == true, let r = try? await call("onlineCount") {
                    onlineCount = r["online"] as? Int
                }
                try? await Task.sleep(nanoseconds: 30_000_000_000)
            }
        })
    }

    private func updateProfile(_ p: Profile?) {
        let old = profile
        profile = p
        guard let p else { return }
        Ads.shared.noAds = p.noAds
        // 같은 계정에서 계급이 올라가면 축하 화면
        if let old, old.uid == p.uid, p.rank.rawValue > old.rank.rawValue { rankUpScore = p.score; rankDown = nil }
        // 계급이 내려가면 응원 팝업
        if let old, old.uid == p.uid, p.rank.rawValue < old.rank.rawValue { rankDown = (old.rank, p.score) }
        if old == nil { redeemPendingInvite(p) }
        refreshMyRanking()
    }

    /// CI 스크린샷용: 로그인한 것처럼 프로필만 채움 (서버에는 연결 안 함)
    func setDemoProfile(_ p: Profile) {
        uid = p.uid
        profile = p
        leaderboard = [p, Profile(uid: "b", name: "보송햄찌42", score: 2140, wins: 30, losses: 12),
                       Profile(uid: "c", name: "SleepyCat17", score: 1320, wins: 11, losses: 9)]
            .sorted { $0.score > $1.score }
    }

    // MARK: 서버 함수

    func call(_ name: String, _ data: [String: Any] = [:]) async throws -> [String: Any] {
        do {
            let result = try await functions.httpsCallable(name).call(data)
            return result.data as? [String: Any] ?? [:]
        } catch {
            // 서버는 오류 문구를 한국어(message)와 영어(details.en)로 보냄
            let ns = error as NSError
            let details = ns.userInfo[FunctionsErrorDetailsKey] as? [String: Any]
            let en = details?["en"] as? String
            let msg = LangStore.shared.code == .ko || en == nil ? ns.localizedDescription : en!
            throw ServerError(message: msg)
        }
    }

    /// 랜덤 매칭. 30초 안에 사람이 없으면 nil (→ AI 어려움으로)
    func findMatch() async throws -> String? {
        let data: [String: Any] = ["country": Self.deviceCountry]
        let deadline = Date().addingTimeInterval(Self.matchTimeout)
        do {
            while Date() < deadline {
                if let id = try await call("matchmake", data)["gameId"] as? String { return id }
                try await Task.sleep(nanoseconds: Self.pollInterval)
            }
            if let id = try await call("matchmake", data)["gameId"] as? String { return id }
            _ = try? await call("leaveQueue")
            return nil
        } catch is CancellationError {
            _ = try? await call("leaveQueue")
            throw CancellationError()
        }
    }

    func startAiGame(_ level: AiLevel) async throws -> String {
        let r = try await call("startAiGame", ["level": level.rawValue, "country": Self.deviceCountry])
        guard let id = r["gameId"] as? String else { throw ServerError(message: t("AI 대전을 시작하지 못했어요")) }
        return id
    }

    func listenGame(_ id: String, _ onChange: @escaping (OnlineGameDoc) -> Void) -> ListenerRegistration {
        db.collection("games").document(id).addSnapshotListener { snap, _ in
            guard let snap, snap.exists, let g = Self.game(from: snap) else { return }
            Task { @MainActor in onChange(g) }
        }
    }

    // MARK: 변환

    private static func int(_ v: Any?) -> Int? { (v as? NSNumber)?.intValue }

    private static func profile(from snap: DocumentSnapshot) -> Profile? {
        guard let d = snap.data() else { return nil }
        return Profile(
            uid: snap.documentID,
            name: d["name"] as? String ?? t("익명"),
            score: int(d["score"]) ?? startScore,
            wins: int(d["wins"]) ?? 0,
            losses: int(d["losses"]) ?? 0,
            potions: int(d["potions"]) ?? 0,
            coins: int(d["coins"]) ?? 0,
            admin: d["admin"] as? Bool ?? false,
            boosts: int(d["boosts"]) ?? 0,
            boostOn: d["boostOn"] as? Bool ?? false,
            animals: Dictionary(uniqueKeysWithValues: ((d["animals"] as? [String: Any]) ?? [:]).compactMap { pair in
                Animal(rawValue: pair.key).map { ($0, int(pair.value) ?? 0) }
            }),
            equipped: (d["equipped"] as? String).flatMap(Animal.init(rawValue:)),
            refCode: d["refCode"] as? String,
            referrals: int(d["referrals"]) ?? 0,
            referred: d["referredBy"] != nil,
            country: d["country"] as? String,
            skins: Set(((d["skins"] as? [Any]) ?? []).compactMap { ($0 as? String).flatMap(Animal.init(rawValue:)) }),
            noAds: d["noAds"] as? Bool ?? false,
            firstBuyDone: d["firstBuyDone"] as? Bool ?? false,
            createdAt: int(d["createdAt"]),
            luckyDay: d["luckyDay"] as? String,
            luckyCoins: int(d["luckyCoins"]) ?? 0,
            dragonDay: d["dragonDay"] as? String,
            dragonCoins: int(d["dragonCoins"]) ?? 0,
            rabbitDay: d["rabbitDay"] as? String,
            rabbitCoins: int(d["rabbitCoins"]) ?? 0,
            unicornDay: d["unicornDay"] as? String,
            unicornCoins: int(d["unicornCoins"]) ?? 0,
            winStreak: int(d["winStreak"]) ?? 0,
            freeRenameUsed: d["freeRenameUsed"] as? Bool ?? false,
            renameTickets: int(d["renameTickets"]) ?? 0,
            tutorialDone: d["tutorialDone"] as? Bool ?? false,
            tutorialSeen: d["tutorialSeen"] as? Bool ?? false,
            checkinDay: d["checkinDay"] as? String,
            checkinStreak: int(d["checkinStreak"]) ?? 0,
            missionDay: d["missionDay"] as? String,
            mPlay: int(d["mPlay"]) ?? 0,
            mWin: int(d["mWin"]) ?? 0,
            mRandom: int(d["mRandom"]) ?? 0,
            mClaimed: Set(((d["mClaimed"] as? [Any]) ?? []).compactMap { $0 as? String }),
            potionAdDay: d["potionAdDay"] as? String,
            potionAds: int(d["potionAds"]) ?? 0,
            weeklyReward: (d["weeklyReward"] as? [String: Any]).map { w in
                WeeklyReward(week: w["week"] as? String ?? "", rank: int(w["rank"]) ?? 0, coins: int(w["coins"]) ?? 0, seen: w["seen"] as? Bool ?? false)
            }
        )
    }

    private static func game(from snap: DocumentSnapshot) -> OnlineGameDoc? {
        guard let d = snap.data() else { return nil }
        func sets(_ k: String) -> [Int] { (d[k] as? [Any])?.compactMap { int($0) } ?? [1, 1] }
        func side(_ k: String) -> Side? { (d[k] as? String).flatMap(Side.init(rawValue:)) }
        let board = Board(
            cat: sets("cat"), ham: sets("ham"),
            catSplits: int(d["catSplits"]) ?? GameLogic.maxSplits, hamSplits: int(d["hamSplits"]) ?? GameLogic.maxSplits,
            catRevives: int(d["catRevives"]) ?? 0, hamRevives: int(d["hamRevives"]) ?? 0
        )
        // 서버가 보낸 수 정보로 화면 언어 설명을 만듦
        var text = d["lastDesc"] as? String ?? ""
        if let mv = (d["lastMove"] as? String).flatMap(Move.decode), let by = side("lastBy") {
            text = MoveText.describe(MoveInfo(
                side: by, move: mv, power: int(d["lastPower"]) ?? 0, sum: int(d["lastSum"]) ?? 0,
                boom: d["lastBoom"] as? Bool ?? false, splitsLeft: int(d["lastLeft"]) ?? 0))
        }
        return OnlineGameDoc(
            id: snap.documentID,
            catUid: d["catUid"] as? String ?? "", hamUid: d["hamUid"] as? String ?? "",
            catName: d["catName"] as? String ?? t("고양이"), hamName: d["hamName"] as? String ?? t("햄스터"),
            catScore: int(d["catScore"]) ?? 0, hamScore: int(d["hamScore"]) ?? 0,
            board: board, turn: side("turn") ?? .cat,
            finished: d["status"] as? String == "finished", winner: side("winner"), reason: d["reason"] as? String ?? "",
            lastText: text, explodedSide: side("explodedSide"), explodedIndex: int(d["explodedIndex"]) ?? -1,
            moveNo: int(d["moveNo"]) ?? 0,
            turnStartedAt: Date(timeIntervalSince1970: Double(int(d["turnStartedAt"]) ?? 0) / 1000),
            catTurnMs: int(d["catTurnMs"]) ?? turnLimitMs, hamTurnMs: int(d["hamTurnMs"]) ?? turnLimitMs,
            catDelta: int(d["catDelta"]), hamDelta: int(d["hamDelta"]),
            catBonus: int(d["catBonus"]) ?? 0, hamBonus: int(d["hamBonus"]) ?? 0,
            catSkin: (d["catSkin"] as? String).flatMap(Animal.init(rawValue:)),
            hamSkin: (d["hamSkin"] as? String).flatMap(Animal.init(rawValue:)),
            catCountry: d["catCountry"] as? String, hamCountry: d["hamCountry"] as? String,
            vsAi: (d["vsAi"] as? String).flatMap(AiLevel.init(rawValue:)),
            catBoost: d["catBoost"] as? Bool ?? false,
            hamBoost: d["hamBoost"] as? Bool ?? false,
            vsFriend: d["vsFriend"] as? Bool ?? false,
            rated: d["rated"] as? Bool ?? false,
            lastMove: (d["lastMove"] as? String).flatMap(Move.decode),
            lastBy: side("lastBy"),
            lastLucky: d["lastLucky"] as? Bool ?? false,
            lastCoins: int(d["lastCoins"]) ?? 0,
            catStreak: int(d["catStreak"]) ?? 0,
            hamStreak: int(d["hamStreak"]) ?? 0,
            rabbitStreak: int(d["rabbitStreak"]) ?? 0,
            rabbitCoins: int(d["rabbitCoins"]) ?? 0,
            unicornCoins: int(d["unicornCoins"]) ?? 0,
            catCoins: int(d["catCoins"]) ?? 0,
            hamCoins: int(d["hamCoins"]) ?? 0
        )
    }

    private static func randomNickname() -> String {
        if LangStore.shared.code == .ko {
            return ["말랑", "몽글", "졸린", "배고픈", "용감한", "수줍은", "통통", "보송", "새침한", "씩씩한"].randomElement()!
                + ["고양이", "햄스터", "냥이", "햄찌", "치즈냥", "골든햄"].randomElement()! + String(Int.random(in: 10..<100))
        }
        return ["Fluffy", "Sleepy", "Hungry", "Brave", "Shy", "Chubby", "Bouncy", "Sassy", "Cozy", "Zippy"].randomElement()!
            + ["Cat", "Hammy", "Kitty", "Paws", "Whisker", "Nibbles"].randomElement()! + String(Int.random(in: 10..<100))
    }
}

/// 출석 보상 받은 결과 (축하 팝업용)
struct CheckinReward: Equatable {
    let day: Int
    let coins: Int
    let potion: Bool
    var panda: Int = 0
}

/// 미션 보상 받은 결과 (축하 팝업용)
struct MissionReward: Equatable {
    let label: String
    let coins: Int
}

/// 🛠 관리자 화면에서 고칠 사용자
struct AdminUser: Identifiable, Equatable {
    var id: String { uid }
    let uid: String
    let name: String
    let score: Int
    let coins: Int
    let potions: Int
}
