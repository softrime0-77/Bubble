import Foundation

/// AI 난이도. 점수와 턴 시간은 서버(functions/src/index.ts)·안드로이드와 같아야 합니다.
public enum AiLevel: String, CaseIterable, Sendable {
    case easy = "EASY"
    case normal = "NORMAL"
    case hard = "HARD"

    public var depth: Int {
        switch self {
        case .easy: return 1
        case .normal: return 4
        case .hard: return 9
        }
    }
    public var mistakeRate: Double {
        switch self {
        case .easy: return 0.45
        case .normal: return 0.12
        case .hard: return 0
        }
    }
    public var winPoints: Int {
        switch self {
        case .easy: return 1
        case .normal: return 5
        case .hard: return 10
        }
    }
    public var losePoints: Int {
        switch self {
        case .easy: return 0
        case .normal: return -1
        case .hard: return -2
        }
    }
    /// 내 턴 시간(초)
    public var turnSec: Int {
        switch self {
        case .easy: return 20
        case .normal: return 15
        case .hard: return 8
        }
    }
}

/// 알파-베타 미니맥스 AI (안드로이드 Ai.kt와 같은 평가)
public struct Ai {
    public let level: AiLevel
    private static let win = 100_000

    public init(level: AiLevel) { self.level = level }

    public func choose<G: RandomNumberGenerator>(_ board: Board, _ side: Side, using rng: inout G) -> Move {
        let moves = GameLogic.legalMoves(board, side)
        precondition(!moves.isEmpty, "no legal moves")
        if Double.random(in: 0..<1, using: &rng) < level.mistakeRate {
            return moves.randomElement(using: &rng)!
        }
        var best = Int.min
        var bestMoves: [Move] = []
        for m in moves {
            guard let r = try? GameLogic.apply(board, side, m) else { continue }
            let score = -search(r.board, side.opponent, level.depth - 1, -Ai.win * 2, Ai.win * 2)
            if score > best {
                best = score
                bestMoves = [m]
            } else if score == best {
                bestMoves.append(m)
            }
        }
        return bestMoves.randomElement(using: &rng) ?? moves[0]
    }

    public func choose(_ board: Board, _ side: Side) -> Move {
        var g = SystemRandomNumberGenerator()
        return choose(board, side, using: &g)
    }

    private func search(_ b: Board, _ toMove: Side, _ depth: Int, _ alphaIn: Int, _ beta: Int) -> Int {
        if let w = b.winner { return w == toMove ? Ai.win + depth : -Ai.win - depth }
        if depth <= 0 { return evaluate(b, toMove) }
        var alpha = alphaIn
        var best = Int.min
        for m in GameLogic.legalMoves(b, toMove) {
            guard let r = try? GameLogic.apply(b, toMove, m) else { continue }
            let score = -search(r.board, toMove.opponent, depth - 1, -beta, -alpha)
            best = max(best, score)
            alpha = max(alpha, best)
            if alpha >= beta { break }
        }
        return best
    }

    /// 살아있는 세트 수가 중요하고, 4마리 세트는 터지기 직전이라 위험
    private func evaluate(_ b: Board, _ side: Side) -> Int {
        func value(_ sets: [Int]) -> Int {
            sets.reduce(0) { acc, n in
                acc + (n > 0 ? 100 : 0) + ([0, 6, 10, 8, -4][safe: n] ?? 0)
            }
        }
        return value(b.sets(side)) - value(b.sets(side.opponent))
    }
}

extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
