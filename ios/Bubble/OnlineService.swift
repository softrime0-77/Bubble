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
    var rank: Rank { Rank.of(score) }
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

    func side(of uid: String) -> Side? { uid == catUid ? .cat : uid == hamUid ? .hamster : nil }
    func name(_ s: Side) -> String { s == .cat ? catName : hamName }
    func score(_ s: Side) -> Int { s == .cat ? catScore : hamScore }
    func turnMs(_ s: Side) -> Int { s == .cat ? catTurnMs : hamTurnMs }
    func delta(_ s: Side) -> Int? { s == .cat ? catDelta : hamDelta }
    func bonus(_ s: Side) -> Int { s == .cat ? catBonus : hamBonus }
    func skin(_ s: Side) -> Animal? { s == .cat ? catSkin : hamSkin }
    func country(_ s: Side) -> String? { s == .cat ? catCountry : hamCountry }
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
    @Published private(set) var loading = false
    @Published var error: String? = nil
    @Published var welcome: (kind: String, score: Int)? = nil
    @Published private(set) var onlineCount: Int? = nil

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

    func signOut() {
        stopSession()
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        uid = nil
        profile = nil
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
                let r = try await call("ensureProfile", ["name": Self.randomNickname()])
                if let kind = r["kind"] as? String {
                    welcome = (kind, (r["score"] as? Int) ?? Self.startScore)
                }
            } catch {
                self.error = tr("서버에 연결하지 못했어요: \(error.localizedDescription)", "Could not connect to the server: \(error.localizedDescription)")
            }
            loading = false
        })
        listeners.append(db.collection("users").document(id).addSnapshotListener { [weak self] snap, _ in
            Task { @MainActor in self?.profile = snap.flatMap(Self.profile(from:)) }
        })
        listeners.append(db.collection("users").order(by: "score", descending: true).limit(to: 30).addSnapshotListener { [weak self] snap, _ in
            let list = snap?.documents.compactMap(Self.profile(from:)) ?? []
            Task { @MainActor in self?.leaderboard = list }
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
        let data: [String: Any] = ["country": Locale.current.region?.identifier ?? ""]
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
        let r = try await call("startAiGame", ["level": level.rawValue, "country": Locale.current.region?.identifier ?? ""])
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
            admin: d["admin"] as? Bool ?? false
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
            vsAi: (d["vsAi"] as? String).flatMap(AiLevel.init(rawValue:))
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
