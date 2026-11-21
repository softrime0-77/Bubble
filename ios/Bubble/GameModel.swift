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
