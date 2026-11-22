import BubbleCore
import Foundation

/// 게임 화면이 쓰는 공통 모양. 폰 안의 AI 연습(LocalGame)과 서버 대전(OnlineGame)이 같이 따름.
@MainActor
protocol GameModel: ObservableObject {
    var board: Board { get }
    var mySide: Side { get }
    var turn: Side { get }
    var finished: Bool { get }
    var winner: Side? { get }
    var reason: String { get }
    var log: [String] { get }
    var explosion: (Explosion, Int)? { get }
    /// 상대가 방금 둔 "더하기" 수 (방울이 날아가는 효과). 판은 날아가 부딪힌 뒤에 바뀜.
    var lastShot: ShotInfo? { get }
    /// 마지막 💰 복냥이 효과 (맞은 방울에서 동전이 튀어나옴). 서버 대전만
    var lucky: LuckyHit? { get }
    /// 🐰 연승 토끼 리본 수
    var myRibbons: Int { get }
    var opponentRibbons: Int { get }
    /// 🐰 내가 연승 토끼로 이겼을 때: 이번 판으로 된 연승과 받은 💰
    var rabbitStreak: Int { get }
    var rabbitCoins: Int { get }
    /// 🦄 내가 유니콘으로 이겼을 때 받은 💰
    var unicornCoins: Int { get }
    var deadline: Date? { get }
    /// 이번 차례 제한 시간 (초)
    var turnSec: Int { get }
    var myTurn: Bool { get }
    var selected: Int? { get set }
    var myName: String { get }
    var opponentName: String { get }
    var mySkin: Animal? { get }
    var opponentSkin: Animal? { get }
    /// AI 상대인지 (로컬 연습 또는 점수가 걸린 서버 AI 대전)
    var isAi: Bool { get }
    /// 서버 대전인지 (점수가 걸림)
    var isOnline: Bool { get }
    /// 끝난 뒤 바뀐 점수 (서버 대전), 그중 계급 보너스
    var scoreDelta: Int? { get }
    var scoreBonus: Int { get }
    var notice: String? { get }
    /// 이번 판에 ×3 부스터를 썼는지 (서버 랜덤 매칭)
    var myBoost: Bool { get }
    /// 친구 대전인데 하루 제한을 넘겨 점수가 없는 친선전인지
    var friendlyMatch: Bool { get }
    func play(_ move: Move)
}

/// [side]가 [from] 방울로 (상대 or 자기) [to] 방울을 침. [id]가 바뀔 때마다 한 번 날림.
struct ShotInfo: Equatable {
    let side: Side
    let from: Int
    let toOpponent: Bool
    let to: Int
    let id: Int
    var kind: ShotKind = .add

    /// 수 하나를 효과 표식으로 (분할은 빈 옆 방울로, 부활은 그 자리)
    static func of(_ side: Side, _ move: Move, id: Int) -> ShotInfo {
        switch move {
        case let .add(from, toOpponent, to): return ShotInfo(side: side, from: from, toOpponent: toOpponent, to: to, id: id)
        case let .split(from): return ShotInfo(side: side, from: from, toOpponent: false, to: 1 - from, id: id, kind: .split)
        case let .revive(index): return ShotInfo(side: side, from: index, toOpponent: false, to: index, id: id, kind: .revive)
        }
    }

    /// 판이 바뀌기 전에 효과를 보여줄 시간 (초)
    var holdSec: Double { kind == .revive ? ReviveBurst.dropSec : FlyingShot.flySec }
}

/// 효과 종류: 더하기(날아가 치기) · 분할(가위로 잘라 옆으로) · 부활(물약)
enum ShotKind { case add, split, revive }

/// 💰 복냥이 효과가 터짐: [side]가 상대 [to] 방울을 쳐서 💰 +1
struct LuckyHit: Equatable {
    let side: Side
    let to: Int
    let id: Int
    var coins: Int = 1
}
