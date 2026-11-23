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
    // 유료 스킨 (한 번 사면 계속, 효과 없음). 서버 COSMETIC_SKINS와 같은 가격
    case pinkCat = "PINK_CAT"
    case blackCat = "BLACK_CAT"
    case snowHamster = "SNOW_HAMSTER"
    case goldHamster = "GOLD_HAMSTER"
    /// 💰 복냥이: 한 번 사면 계속. 공격할 때마다 50% 확률로 상점 포인트 +1 (하루 최대 30)
    case luckyCat = "LUCKY_CAT"
    /// 🐙 문어 · 🐧 펭귄: 모습 + 효과만
    case octopus = "OCTOPUS"
    case penguin = "PENGUIN"
    /// 🐼 출석 판다: 장착하고 출석하면 💰 +5
    case panda = "PANDA"
    /// 🐉 아기 용: 상대 방울을 터뜨리면 💰 +2 (하루 최대 10)
    case dragon = "DRAGON"
    /// 🐰 연승 토끼: 연승할수록 리본 (최대 5)
    case rabbit = "RABBIT"
    /// 🦄 유니콘: 팔지 않음. 다이아 계급 이상이면 장착
    case unicorn = "UNICORN"

    public var price: Int {
        switch self {
        case .sheep: return 20
        case .dog: return 40
        case .giraffe: return 70
        case .hamster: return 0
        case .pinkCat, .blackCat, .snowHamster: return 300
        case .goldHamster: return 500
        case .luckyCat: return 1000
        case .octopus, .penguin: return 400
        case .panda: return 500
        case .dragon: return 700
        case .rabbit: return 600
        case .unicorn: return 0
        }
    }
    public var opponentTurnSec: Int {
        switch self {
        case .sheep: return 15
        case .dog: return 12
        case .giraffe: return 8
        case .hamster, .pinkCat, .blackCat, .snowHamster, .goldHamster, .luckyCat, .octopus, .penguin, .panda, .dragon, .rabbit, .unicorn: return Shop.defaultTurnSec
        }
    }
    /// 유료 스킨 (한 번 사면 계속, 모습만)
    public var cosmetic: Bool { [.pinkCat, .blackCat, .snowHamster, .goldHamster, .luckyCat, .octopus, .penguin, .panda, .dragon, .rabbit].contains(self) }
    /// 계급 캐릭터 (팔지 않음): 🦄 유니콘은 다이아(10001점) 이상이면 무료로 장착
    public var rankOnly: Bool { self == .unicorn }
    public var rankMin: Int { self == .unicorn ? 10_001 : 0 }
    /// 효과가 있는 영구 캐릭터 (복냥이)
    public var lucky: Bool { self == .luckyCat }
    /// 소모품 동물 (랜덤 매칭 1판에 1개)
    public var consumable: Bool { !free && !cosmetic && !rankOnly }
    /// 그릴 때 고양이 모양인지 햄스터 모양인지 (상점 미리보기용)
    public var looksLikeCat: Bool { self == .pinkCat || self == .blackCat || self == .luckyCat }
    /// 무료 캐릭터 (사지 않고 바로 장착, 효과 없음)
    public var free: Bool { price == 0 && !rankOnly }
}

public enum Shop {
    public static let potionPrice = 50
    /// ✏️ 닉네임 변경권 (첫 변경은 무료). functions/src/catalog.ts RENAME_PRICE와 같게
    public static let renamePrice = 100
    public static let boostPrice = 100
    public static let boostMultiplier = 3
    public static let maxRevivesPerGame = 3
    public static let defaultTurnSec = 20
    public static let winPoints = 30
    public static let losePoints = 10
    /// 친구 대전 점수 (functions/src/friend.ts와 같게): 승리 +30, 패배 -10, 같은 친구와 하루 5판까지
    public static let friendWinPoints = 30
    public static let friendLosePoints = 10
    public static let friendRatedPerDay = 5
    /// 💰 복냥이: 공격할 때 💰 +1 확률(%)과 하루 최대 (functions/src/catalog.ts LUCKY_*와 같게)
    public static let luckyChancePct = 50
    public static let luckyDailyMax = 30
    /// 🐼 판다 보너스, 🐉 아기 용(터뜨릴 때 💰, 하루 최대), 🐰 리본 최대 (functions/src/catalog.ts와 같게)
    /// 묶음 할인 (개수, 가격). functions/src/catalog.ts BUNDLES와 같게. 부활물약 1·3·5·10개, 동물 1·3개
    public static func bundles(_ item: String) -> [(qty: Int, price: Int)] {
        switch item {
        case "POTION": return [(1, 50), (3, 135), (5, 200), (10, 350)]
        case "SHEEP": return [(1, 20), (3, 50)]
        case "DOG": return [(1, 40), (3, 100)]
        case "GIRAFFE": return [(1, 70), (3, 180)]
        default: return []
        }
    }

    public static let pandaCheckinBonus = 10
    public static let dragonPopCoins = 2
    public static let dragonDailyMax = 10
    public static let rabbitMaxRibbons = 5
    /// 🐰 연승 보상: 3연승 💰5 · 5연승 💰15 · 6연승부터 💰2, 하루 최대 30 (functions/src/catalog.ts rabbitCoins와 같게)
    public static let rabbit3Coins = 5
    public static let rabbit5Coins = 15
    public static let rabbitMoreCoins = 2
    public static let rabbitDailyMax = 30
    /// 🦄 유니콘 무지개 축복: 이길 때마다 💰3, 하루 최대 30 (functions/src/catalog.ts와 같게)
    public static let unicornWinCoins = 3
    public static let unicornDailyMax = 30
}
