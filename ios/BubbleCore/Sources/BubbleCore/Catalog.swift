import Foundation

/// 계급. 값은 서버(functions/src/catalog.ts RANKS)·안드로이드와 같아야 합니다.
public enum Rank: Int, CaseIterable, Sendable {
    case dirt, copper, silver, gold, ruby, sapphire, diamond, pinkDiamond

    public var minScore: Int {
        switch self {
        case .dirt: return Int.min
        case .copper: return 1001
        case .silver: return 1401
        case .gold: return 2001
        case .ruby: return 4001
        case .sapphire: return 7001
        case .diamond: return 10001
        case .pinkDiamond: return 50001
        }
    }

    /// 반짝임 단계 (흙 0 ~ 핑크다이아 7)
    public var sparkle: Int { rawValue }
    /// 이겨서 얻는 점수에 붙는 보너스 %
    public var bonusPercent: Int { [0, 5, 10, 15, 20, 25, 30, 35][rawValue] }
    public var emoji: String { ["🟫", "🟠", "⚪", "🟡", "🔴", "🔷", "💎", "🌸"][rawValue] }

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
    /// 무료 캐릭터: 모습만 햄스터로, 효과 없음·소모되지 않음
    case hamster = "HAMSTER"

    public var price: Int {
        switch self {
        case .sheep: return 30
        case .dog: return 50
        case .giraffe: return 100
        case .hamster: return 0
        }
    }
    public var opponentTurnSec: Int {
        switch self {
        case .sheep: return 15
        case .dog: return 10
        case .giraffe: return 5
        case .hamster: return Shop.defaultTurnSec
        }
    }
    /// 무료 캐릭터 (사지 않고 바로 장착, 효과 없음)
    public var free: Bool { price == 0 }
}

public enum Shop {
    public static let potionPrice = 50
    public static let boostPrice = 50
    public static let boostMultiplier = 3
    public static let maxRevivesPerGame = 3
    public static let defaultTurnSec = 20
    public static let winPoints = 30
    public static let losePoints = 10
    /// 친구 대전 점수 (functions/src/friend.ts와 같게): 승리 +30, 패배 -10, 같은 친구와 하루 5판까지
    public static let friendWinPoints = 30
    public static let friendLosePoints = 10
    public static let friendRatedPerDay = 5
}
