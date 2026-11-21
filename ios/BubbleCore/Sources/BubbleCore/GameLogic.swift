import Foundation

/// 진영: 고양이 또는 햄스터. 고양이가 항상 먼저 둡니다.
public enum Side: String, CaseIterable, Sendable {
    case cat = "CAT"
    case hamster = "HAMSTER"

    public var opponent: Side { self == .cat ? .hamster : .cat }
}

/// 한 수.
/// - add: 내 세트 from의 마리 수만큼 상대 세트(toOpponent) 또는 내 다른 세트 to에 더함
/// - split: 짝수 마리인 세트를 반으로 나눠 비어 있는 다른 세트를 살림
/// - revive: 부활물약으로 죽은 세트를 1마리로 살림
public enum Move: Hashable, Sendable {
    case add(from: Int, toOpponent: Bool, to: Int)
    case split(from: Int)
    case revive(index: Int)

    /// 서버·안드로이드와 같은 문자열 형식 ("A:0:1:1", "S:0", "R:1")
    public var encoded: String {
        switch self {
        case let .add(from, toOpponent, to): return "A:\(from):\(toOpponent ? 1 : 0):\(to)"
        case let .split(from): return "S:\(from)"
        case let .revive(index): return "R:\(index)"
        }
    }

    public static func decode(_ s: String) -> Move? {
        let p = s.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        func idx(_ x: String) -> Int? { x == "0" ? 0 : x == "1" ? 1 : nil }
        switch p.first {
        case "A":
            guard p.count == 4, let f = idx(p[1]), let t = idx(p[3]), p[2] == "0" || p[2] == "1" else { return nil }
            return .add(from: f, toOpponent: p[2] == "1", to: t)
        case "S":
            guard p.count == 2, let f = idx(p[1]) else { return nil }
            return .split(from: f)
        case "R":
            guard p.count == 2, let i = idx(p[1]) else { return nil }
            return .revive(index: i)
        default:
            return nil
        }
    }
}

/// 판 상태. 각 진영은 세트 2개와 남은 분할/부활 횟수를 가집니다.
public struct Board: Equatable, Sendable {
    public var cat: [Int]
    public var ham: [Int]
    public var catSplits: Int
    public var hamSplits: Int
    public var catRevives: Int
    public var hamRevives: Int

    public init(
        cat: [Int] = [1, 1], ham: [Int] = [1, 1],
        catSplits: Int = GameLogic.maxSplits, hamSplits: Int = GameLogic.maxSplits,
        catRevives: Int = 0, hamRevives: Int = 0
    ) {
        self.cat = cat
        self.ham = ham
        self.catSplits = catSplits
        self.hamSplits = hamSplits
        self.catRevives = catRevives
        self.hamRevives = hamRevives
    }

    public func sets(_ side: Side) -> [Int] { side == .cat ? cat : ham }
    public func splitsLeft(_ side: Side) -> Int { side == .cat ? catSplits : hamSplits }
    public func revivesLeft(_ side: Side) -> Int { side == .cat ? catRevives : hamRevives }

    mutating func setSets(_ side: Side, _ v: [Int]) { if side == .cat { cat = v } else { ham = v } }
    mutating func setSplits(_ side: Side, _ n: Int) { if side == .cat { catSplits = n } else { hamSplits = n } }
    mutating func setRevives(_ side: Side, _ n: Int) { if side == .cat { catRevives = n } else { hamRevives = n } }

    public func isDefeated(_ side: Side) -> Bool { sets(side).allSatisfy { $0 == 0 } }

    /// 승자. 아직 끝나지 않았으면 nil.
    public var winner: Side? {
        if isDefeated(.cat) { return .hamster }
        if isDefeated(.hamster) { return .cat }
        return nil
    }
}

/// 한 수의 정보 (화면 언어로 설명 문장을 만들 때 씀)
public struct MoveInfo: Equatable, Sendable {
    public let side: Side
    public let move: Move
    /// 더한 마리 수 (add) / 분할 전 마리 수 (split)
    public let power: Int
    /// 더한 결과 (터졌으면 터지기 전 합)
    public let sum: Int
    public let boom: Bool
    public let splitsLeft: Int

    public init(side: Side, move: Move, power: Int, sum: Int, boom: Bool, splitsLeft: Int) {
        self.side = side
        self.move = move
        self.power = power
        self.sum = sum
        self.boom = boom
        self.splitsLeft = splitsLeft
    }
}

public struct Explosion: Equatable, Sendable {
    public let side: Side
    public let index: Int

    public init(side: Side, index: Int) {
        self.side = side
        self.index = index
    }
}

public struct MoveResult: Equatable, Sendable {
    public let board: Board
    public let exploded: Explosion?
    public let info: MoveInfo
}

public enum GameLogicError: Error { case illegalMove }

public enum GameLogic {
    public static let setCount = 2
    public static let explodeAt = 5
    /// 한 게임에서 한 사람이 쓸 수 있는 분할 횟수
    public static let maxSplits = 3

    /// 분할 모양: 짝수(2 이상)이고 다른 세트가 0
    public static func splitShapeOk(_ b: Board, _ side: Side, _ from: Int) -> Bool {
        let mine = b.sets(side)
        let n = mine[from]
        return n > 0 && n % 2 == 0 && mine[1 - from] == 0
    }

    public static func canSplit(_ b: Board, _ side: Side, _ from: Int) -> Bool {
        b.splitsLeft(side) > 0 && splitShapeOk(b, side, from)
    }

    /// 부활: 물약이 남았고, 그 세트가 죽었고, 다른 세트는 살아 있을 때
    public static func canRevive(_ b: Board, _ side: Side, _ index: Int) -> Bool {
        let mine = b.sets(side)
        return b.revivesLeft(side) > 0 && mine[index] == 0 && mine[1 - index] > 0
    }

    public static func isLegal(_ b: Board, _ side: Side, _ move: Move) -> Bool {
        if b.winner != nil { return false }
        let range = 0..<setCount
        let mine = b.sets(side)
        switch move {
        case let .add(from, toOpponent, to):
            guard range.contains(from), range.contains(to), mine[from] > 0 else { return false }
            if toOpponent { return b.sets(side.opponent)[to] > 0 }
            // 내 다른 세트 강화: 0개(죽은) 세트는 분할/부활로만 살릴 수 있음
            return to != from && mine[to] > 0
        case let .split(from):
            return range.contains(from) && canSplit(b, side, from)
        case let .revive(index):
            return range.contains(index) && canRevive(b, side, index)
        }
    }

    public static func legalMoves(_ b: Board, _ side: Side) -> [Move] {
        if b.winner != nil { return [] }
        var all: [Move] = []
        for from in 0..<setCount {
            for to in 0..<setCount {
                all.append(.add(from: from, toOpponent: true, to: to))
                all.append(.add(from: from, toOpponent: false, to: to))
            }
            all.append(.split(from: from))
            all.append(.revive(index: from))
        }
        return all.filter { isLegal(b, side, $0) }
    }

    public static func apply(_ b: Board, _ side: Side, _ move: Move) throws -> MoveResult {
        guard isLegal(b, side, move) else { throw GameLogicError.illegalMove }
        var next = b
        switch move {
        case let .add(from, toOpponent, to):
            let power = b.sets(side)[from]
            let targetSide = toOpponent ? side.opponent : side
            var target = b.sets(targetSide)
            let sum = target[to] + power
            let boom = sum >= explodeAt
            target[to] = boom ? 0 : sum
            next.setSets(targetSide, target)
            return MoveResult(
                board: next,
                exploded: boom ? Explosion(side: targetSide, index: to) : nil,
                info: MoveInfo(side: side, move: move, power: power, sum: sum, boom: boom, splitsLeft: b.splitsLeft(side))
            )
        case let .split(from):
            let half = b.sets(side)[from] / 2
            let left = b.splitsLeft(side) - 1
            next.setSets(side, [half, half])
            next.setSplits(side, left)
            return MoveResult(
                board: next, exploded: nil,
                info: MoveInfo(side: side, move: move, power: half * 2, sum: half, boom: false, splitsLeft: left)
            )
        case let .revive(index):
            var mine = b.sets(side)
            mine[index] = 1
            next.setSets(side, mine)
            next.setRevives(side, b.revivesLeft(side) - 1)
            return MoveResult(
                board: next, exploded: nil,
                info: MoveInfo(side: side, move: move, power: 0, sum: 0, boom: false, splitsLeft: b.splitsLeft(side))
            )
        }
    }
}
