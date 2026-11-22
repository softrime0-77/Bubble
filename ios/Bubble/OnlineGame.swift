import BubbleCore
import FirebaseFirestore
import Foundation

/// 서버에서 진행하는 대전 (랜덤 매칭, 또는 점수가 걸린 AI 대전). 판정·점수는 서버가 함.
@MainActor
final class OnlineGame: GameModel {
    let gameId: String
    let fallbackAi: Bool
    private let myUid: String

    @Published private(set) var board = Board()
    @Published private(set) var mySide: Side = .cat
    @Published private(set) var turn: Side = .cat
    @Published private(set) var finished = false
    @Published private(set) var winner: Side? = nil
    @Published private(set) var reason = ""
    @Published private(set) var log: [String] = []
    @Published private(set) var explosion: (Explosion, Int)? = nil
    @Published private(set) var lastShot: ShotInfo? = nil
    @Published private(set) var lucky: LuckyHit? = nil
    @Published private(set) var myRibbons = 0
    @Published private(set) var opponentRibbons = 0
    @Published private(set) var rabbitStreak = 0
    @Published private(set) var rabbitCoins = 0
    @Published private(set) var deadline: Date? = nil
    @Published private(set) var turnSec = Shop.defaultTurnSec
    @Published private(set) var busy = true
    @Published var selected: Int? = nil
    @Published private(set) var myName = ""
    @Published private(set) var opponentName = ""
    @Published private(set) var mySkin: Animal? = nil
    @Published private(set) var opponentSkin: Animal? = nil
    @Published private(set) var aiLevel: AiLevel? = nil
    @Published private(set) var scoreDelta: Int? = nil
    @Published private(set) var scoreBonus = 0
    @Published private(set) var notice: String? = nil
    @Published private(set) var myCountry: String? = nil
    @Published private(set) var opponentCountry: String? = nil
    @Published private(set) var myScore = 0
    @Published private(set) var opponentScore = 0
    @Published private(set) var loaded = false
    @Published private(set) var myBoost = false
    /// 친구 대전 / 그중 점수가 걸린 판
    @Published private(set) var isFriend = false
    @Published private(set) var friendRated = false

    private var lastMove = -1
    /// 상대 방울이 날아가는 동안 들어온 서버 상태 (부딪힌 뒤에 반영)
    private var holding = false
    private var heldDoc: OnlineGameDoc?
    private var listener: ListenerRegistration?
    private var timerTask: Task<Void, Never>?

    var myTurn: Bool { loaded && !finished && !busy && turn == mySide }
    var isAi: Bool { aiLevel != nil }
    var isOnline: Bool { true }
    var friendlyMatch: Bool { isFriend && !friendRated }

    init(gameId: String, uid: String, fallbackAi: Bool = false) {
        self.gameId = gameId
        self.myUid = uid
        self.fallbackAi = fallbackAi
        listener = OnlineService.shared.listenGame(gameId) { [weak self] g in self?.merge(g) }
        // 시간 초과 감시: 온라인은 상대가, AI 대전은 내가 시간을 넘기면 서버에 알림 (판정은 서버)
        timerTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self, !self.finished, let d = self.deadline else { continue }
                let lateIsMe = self.isAi
                if (self.turn == self.mySide) == lateIsMe,
                   Date() > d.addingTimeInterval(Double(OnlineService.timeoutGraceMs) / 1000) {
                    _ = try? await OnlineService.shared.call("claimTimeout", ["gameId": self.gameId])
                }
            }
        }
    }

    private func merge(_ g: OnlineGameDoc) {
        if holding { heldDoc = g; return }
        guard let side = g.side(of: myUid) else { return }
        // 상대가 둔 수: 효과(날아가기 · 분할 · 부활)를 먼저 보여주고, 끝난 뒤에 판을 바꿈
        if g.moveNo != lastMove, lastMove >= 0, let by = g.lastBy, by != side, let mv = g.lastMove {
            let shot = ShotInfo.of(by, mv, id: g.moveNo)
            lastShot = shot
            holding = true
            heldDoc = g
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(shot.holdSec * 1_000_000_000))
                guard let self else { return }
                self.holding = false
                if let d = self.heldDoc {
                    self.heldDoc = nil
                    self.apply(d)
                }
            }
            return
        }
        apply(g)
    }

    private func apply(_ g: OnlineGameDoc) {
        guard let side = g.side(of: myUid) else { return }
        let newMove = g.moveNo != lastMove && g.moveNo > 0
        if newMove && !g.lastText.isEmpty { log = Array(([g.lastText] + log).prefix(30)) }
        if newMove, let es = g.explodedSide { explosion = (Explosion(side: es, index: g.explodedIndex), g.moveNo) }
        // 💰 복냥이(+1) · 🐉 아기 용(+2)
        let gained = g.lastCoins > 0 ? g.lastCoins : (g.lastLucky ? 1 : 0)
        if newMove, gained > 0, let by = g.lastBy, case let .add(_, _, to)? = g.lastMove {
            lucky = LuckyHit(side: by, to: to, id: g.moveNo, coins: gained)
        }
        if newMove { notice = nil }
        lastMove = g.moveNo
        mySide = side
        board = g.board
        turn = g.turn
        finished = g.finished
        winner = g.winner
        reason = g.reason
        aiLevel = g.vsAi
        myName = g.name(side)
        opponentName = g.vsAi.map { lv in
            tr("AI 친구 (\(lv.label))", "AI Buddy (\(lv.label))", zh: "AI 伙伴（\(lv.label)）", es: "Amigo IA (\(lv.label))",
               fr: "Copain IA (\(lv.label))", de: "KI-Kumpel (\(lv.label))", pt: "Amigo IA (\(lv.label))", ja: "AIフレンド（\(lv.label)）")
        } ?? g.name(side.opponent)
        mySkin = g.skin(side)
        myRibbons = side == .cat ? g.catStreak : g.hamStreak
        opponentRibbons = side == .cat ? g.hamStreak : g.catStreak
        rabbitStreak = g.winner == side ? g.rabbitStreak : 0
        rabbitCoins = g.winner == side ? g.rabbitCoins : 0
        opponentSkin = g.skin(side.opponent)
        myCountry = g.country(side)
        opponentCountry = g.country(side.opponent)
        myScore = g.score(side)
        opponentScore = g.score(side.opponent)
        scoreDelta = g.delta(side)
        scoreBonus = g.bonus(side)
        myBoost = g.boost(side)
        isFriend = g.vsFriend
        friendRated = g.rated
        // AI 대전은 내 차례에만 시간 제한
        let timed = !(g.vsAi != nil && g.turn != side)
        turnSec = g.turnMs(g.turn) / 1000
        deadline = timed && !g.finished ? g.turnStartedAt.addingTimeInterval(Double(g.turnMs(g.turn)) / 1000) : nil
        busy = false
        loaded = true
        if g.turn != side { selected = nil }
    }

    func play(_ move: Move) {
        guard myTurn, GameLogic.isLegal(board, mySide, move) else { return }
        busy = true
        selected = nil
        Task {
            do {
                _ = try await OnlineService.shared.call("submitMove", ["gameId": gameId, "move": move.encoded])
            } catch {
                busy = false
                notice = error.localizedDescription
            }
        }
    }

    /// 나가기: 진행 중이면 기권
    func leave() {
        if !finished {
            let id = gameId
            Task { _ = try? await OnlineService.shared.call("forfeit", ["gameId": id]) }
        }
        stop()
    }

    func stop() {
        listener?.remove()
        listener = nil
        timerTask?.cancel()
    }
}
