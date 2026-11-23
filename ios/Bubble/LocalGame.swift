import BubbleCore
import Foundation

/// 폰 안에서 하는 AI 연습 게임 (점수 없음). 1단계 범위.
@MainActor
final class LocalGame: GameModel {
    let level: AiLevel
    let mySide: Side
    private let ai: Ai

    @Published private(set) var board = Board()
    @Published private(set) var turn: Side = .cat
    @Published private(set) var finished = false
    @Published private(set) var winner: Side? = nil
    @Published private(set) var reason = ""
    @Published private(set) var log: [String] = []
    @Published private(set) var explosion: (Explosion, Int)? = nil
    @Published private(set) var lastShot: ShotInfo? = nil
    var lucky: LuckyHit? { nil }
    var myRibbons: Int { 0 }
    var opponentRibbons: Int { 0 }
    var rabbitStreak: Int { 0 }
    var rabbitCoins: Int { 0 }
    var unicornCoins: Int { 0 }
    var gameCoins: Int { 0 }
    @Published private(set) var deadline: Date? = nil
    @Published private(set) var busy = false
    @Published var selected: Int? = nil

    private var explosionId = 0
    private var timerTask: Task<Void, Never>?

    var myTurn: Bool { !finished && !busy && turn == mySide }
    var turnSec: Int { level.turnSec }
    var myName: String { t("나") }
    var mySkin: Animal? { nil }
    var opponentSkin: Animal? { nil }
    var isAi: Bool { true }
    var isOnline: Bool { false }
    var scoreDelta: Int? { nil }
    var scoreBonus: Int { 0 }
    @Published private(set) var notice: String? = nil
    /// 🎓 튜토리얼 단계 (0~3 안내 중, 4 끝). nil이면 일반 연습
    @Published private(set) var tutorial: Int? = nil
    var myBoost: Bool { false }
    var friendlyMatch: Bool { false }
    var opponentName: String {
        if tutorial != nil { return tr("연습 친구", "Practice buddy", zh: "练习伙伴", ja: "れんしゅう相手") }
        return tr("AI 친구 (\(level.label))", "AI Buddy (\(level.label))", zh: "AI 伙伴（\(level.label)）", hi: "AI दोस्त (\(level.label))",
           es: "Amigo IA (\(level.label))", fr: "Copain IA (\(level.label))", ar: "رفيق الذكاء الاصطناعي (\(level.label))",
           de: "KI-Kumpel (\(level.label))", pt: "Amigo IA (\(level.label))", ru: "ИИ-друг (\(level.label))", ja: "AIフレンド（\(level.label)）")
    }

    init(level: AiLevel, mySide: Side? = nil) {
        self.level = level
        self.ai = Ai(level: level)
        self.mySide = mySide ?? (Bool.random() ? .cat : .hamster)
        startTurn()
        timerTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 200_000_000)
                self?.checkTimeout()
            }
        }
    }

    /// 🎓 튜토리얼: 정해진 판, 상대는 두지 않음, 시간 제한 없음
    init(tutorial: Bool) {
        self.level = .easy
        self.ai = Ai(level: .easy)
        self.mySide = .cat
        self.board = TutorialScript.board
        self.tutorial = 0
        self.turn = .cat
    }

    deinit { timerTask?.cancel() }

    /// 스크린샷/미리보기용 판 설정
    func setDemo(board: Board, log: [String]) {
        self.board = board
        self.log = log
        turn = mySide
        // CI 스크린샷이 실행 8초 뒤에 찍히므로 그때 게이지가 반쯤 남아 있게
        deadline = Date().addingTimeInterval(Double(level.turnSec) + 2)
    }

    func play(_ move: Move) {
        if let step = tutorial {
            guard step < TutorialScript.moves.count else { return }
            guard move == TutorialScript.moves[step] else {
                notice = tr("👆 손가락이 가리키는 방울을 눌러 주세요", "👆 Tap the bubble the finger points to", zh: "👆 请点手指指着的泡泡", ja: "👆 指がさしているバブルをタップしてね")
                selected = nil
                return
            }
            notice = nil
            apply(mySide, move)
            selected = nil
            turn = mySide  // 상대는 두지 않고 계속 내 차례
            tutorial = step + 1
            return
        }
        guard myTurn, GameLogic.isLegal(board, mySide, move) else { return }
        apply(mySide, move)
        selected = nil
        startTurn()
    }

    func stop() { timerTask?.cancel() }

    private func apply(_ side: Side, _ move: Move) {
        guard let r = try? GameLogic.apply(board, side, move) else { return }
        board = r.board
        turn = side.opponent
        log = Array(([MoveText.describe(r.info)] + log).prefix(30))
        if let e = r.exploded {
            explosionId += 1
            explosion = (e, explosionId)
        }
        if let w = r.board.winner {
            finished = true
            winner = w
            reason = "normal"
            deadline = nil
        }
    }

    /// 차례 시작: 내 차례면 타이머, AI 차례면 잠시 생각한 뒤 둠
    private func startTurn() {
        guard !finished else { return }
        if turn == mySide {
            deadline = Date().addingTimeInterval(Double(level.turnSec))
            busy = false
        } else {
            deadline = nil
            busy = true
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64.random(in: 700_000_000...1_400_000_000))
                guard let self, !self.finished else { return }
                let m = self.ai.choose(self.board, self.turn)
                // 방울이 날아가 부딪힌 뒤(분할·부활도 효과가 끝난 뒤)에 판을 바꿈
                let shot = ShotInfo.of(self.turn, m, id: (self.lastShot?.id ?? 0) + 1)
                self.lastShot = shot
                try? await Task.sleep(nanoseconds: UInt64(shot.holdSec * 1_000_000_000))
                guard !self.finished else { return }
                self.apply(self.turn, m)
                self.busy = false
                self.startTurn()
            }
        }
    }

    private func checkTimeout() {
        guard !finished, turn == mySide, let d = deadline, Date() > d else { return }
        finished = true
        winner = mySide.opponent
        reason = "timeout"
        deadline = nil
    }
}
