import Foundation

/// 계급. 값은 서버(functions/src/catalog.ts RANKS)·안드로이드와 같아야 합니다.
public enum Rank: Int, CaseIterable, Sendable {
    case dirt, copper, silver, gold, ruby, sapphire, diamond

    public var minScore: Int {
        switch self {
        case .dirt: return Int.min
        case .copper: return 1001
        case .silver: return 1500
        case .gold: return 2000
        case .ruby: return 5000
        case .sapphire: return 7000
        case .diamond: return 10000
        }
    }

    /// 반짝임 단계 (흙 0 ~ 다이아 6)
    public var sparkle: Int { rawValue }
    /// 이겨서 얻는 점수에 붙는 보너스 %
    public var bonusPercent: Int { [0, 5, 10, 15, 20, 25, 30][rawValue] }
    public var emoji: String { ["🟫", "🥉", "🥈", "🥇", "🔴", "🔷", "💎"][rawValue] }

    public static func of(_ score: Int) -> Rank {
        Rank.allCases.last { score >= $0.minScore } ?? .dirt
    }

    public func bonus(for base: Int) -> Int {
        base > 0 ? Int((Double(base * bonusPercent) / 100).rounded()) : 0
    }
}

/// 상점 동물 (소모품: 랜덤 매칭 1판에 1개)
public enum Animal: String, CaseIterable, Sendable {
    case sheep = "SHEEP"
    case dog = "DOG"
    case giraffe = "GIRAFFE"

    public var price: Int {
        switch self {
        case .sheep: return 30
        case .dog: return 50
        case .giraffe: return 100
        }
    }
    public var opponentTurnSec: Int {
        switch self {
        case .sheep: return 15
        case .dog: return 10
        case .giraffe: return 5
        }
    }
}

public enum Shop {
    public static let potionPrice = 50
    public static let boostPrice = 50
    public static let boostMultiplier = 3
    public static let maxRevivesPerGame = 3
    public static let defaultTurnSec = 20
    public static let winPoints = 30
    public static let losePoints = 15
}
