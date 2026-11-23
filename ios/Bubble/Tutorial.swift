import BubbleCore
import SwiftUI

/// 🎓 첫 접속 튜토리얼 (안드로이드 ui/Tutorial.kt와 같은 판·순서·문구).
/// 정해진 판에서 손가락 안내를 따라 4수를 두면 끝 (상대는 두지 않음, 점수 없음).
enum TutorialScript {
    static let board = Board(cat: [1, 2], ham: [1, 3])
    static let moves: [Move] = [
        .add(from: 1, toOpponent: true, to: 0),   // 2로 상대 1 공격 → 3
        .add(from: 0, toOpponent: false, to: 1),  // 내 1을 내 2에 더해 → 3
        .add(from: 1, toOpponent: true, to: 0),   // 3으로 상대 3 공격 → 펑!
        .add(from: 1, toOpponent: true, to: 1),   // 마지막 방울도 펑 → 승리
    ]
    static let coins = 20

    static func text(_ step: Int, selected: Bool) -> String {
        switch step {
        case 0: return selected
            ? tr("👆 이제 상대 방울을 콕! 내 방울 수만큼 더해져요 (1+2=3)", "👆 Now tap a rival bubble! Your count gets added (1+2=3)", zh: "👆 点对手的泡泡！会加上你的数量（1+2=3）", ja: "👆 相手のバブルをタップ！数が足されるよ (1+2=3)")
            : tr("👆 내 방울(2마리)을 콕 눌러 골라요", "👆 Tap your bubble with 2 to pick it", zh: "👆 点一下自己的泡泡（2只）选中它", ja: "👆 自分のバブル(2匹)をタップして選ぼう")
        case 1: return selected
            ? tr("👆 옆의 내 방울을 콕! 2+1=3으로 커져요", "👆 Tap your other bubble! 2+1 = 3", zh: "👆 点你的另一个泡泡！2+1=3", ja: "👆 となりの自分のバブルをタップ！2+1=3")
            : tr("👆 내 방울끼리도 더할 수 있어요. 1마리 방울을 골라요", "👆 You can add to your own bubbles too. Pick the bubble with 1", zh: "👆 自己的泡泡也能相加。选只有1只的泡泡", ja: "👆 自分のバブル同士も足せるよ。1匹のバブルを選んで")
        case 2: return selected
            ? tr("👆 상대 3마리 방울을 콕! 5 이상이 되면 💥 펑!", "👆 Tap their bubble with 3! 5 or more = 💥 POP!", zh: "👆 点对手3只的泡泡！达到5就💥砰！", ja: "👆 相手の3匹のバブルをタップ！5以上で💥パン！")
            : tr("👆 3마리 방울을 골라요", "👆 Pick your bubble with 3", zh: "👆 选3只的泡泡", ja: "👆 3匹のバブルを選んで")
        case 3: return selected
            ? tr("👆 남은 상대 방울을 펑! 다 터뜨리면 승리!", "👆 Pop their last bubble to win!", zh: "👆 打爆最后一个泡泡就赢了！", ja: "👆 残った相手のバブルをパン！全部割れば勝ち！")
            : tr("👆 마지막! 3마리 방울을 다시 골라요", "👆 Last one! Pick your 3 again", zh: "👆 最后一步！再选3只的泡泡", ja: "👆 最後！もう一度3匹のバブルを選んで")
        default: return ""
        }
    }
}

/// 게임 화면 위 안내: 말풍선 + 가리키는 손가락 + 건너뛰기
struct TutorialGuide: View {
    let step: Int
    let mySide: Side
    let selected: Int?
    let centers: [String: CGPoint]
    let onSkip: () -> Void

    var body: some View {
        guard step < TutorialScript.moves.count, case let .add(from, toOpp, to) = TutorialScript.moves[step] else { return AnyView(EmptyView()) }
        let picked = selected == from
        let key = picked ? "\(toOpp ? mySide.opponent : mySide)\(to)" : "\(mySide)\(from)"
        return AnyView(ZStack(alignment: .topTrailing) {
            if let c = centers[key] {
                TimelineView(.animation) { tl in
                    let bob = CGFloat(0.5 + 0.5 * sin(tl.date.timeIntervalSinceReferenceDate * 5))
                    Text("👆").font(.system(size: 52)).position(x: c.x + 4, y: c.y + 40 + bob * 14)
                }
                .allowsHitTesting(false)
            }
            VStack(spacing: 6) {
                Text(tr("🎓 튜토리얼 \(step + 1)/4", "🎓 Tutorial \(step + 1)/4", zh: "🎓 教程 \(step + 1)/4", ja: "🎓 チュートリアル \(step + 1)/4"))
                    .font(.system(size: 13, weight: .black)).foregroundColor(.white)
                    .padding(.horizontal, 12).padding(.vertical, 4)
                    .background(Capsule().fill(UiColor.accent))
                Text(TutorialScript.text(step, selected: picked))
                    .font(.system(size: 17, weight: .black)).foregroundColor(UiColor.text).multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 18).fill(.white))
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20).padding(.top, 290)
            .allowsHitTesting(false)
            Button(action: onSkip) {
                Text(tr("건너뛰기 ›", "Skip ›", zh: "跳过 ›", ja: "スキップ ›"))
                    .font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.text.opacity(0.7))
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Capsule().fill(.white.opacity(0.8)))
            }
            .buttonStyle(.plain)
            .padding(.top, 4).padding(.trailing, 72)
        })
    }
}

/// 다 끝나면: 더 알아 둘 것 + 보상 받기
struct TutorialDone: View {
    /// 이미 보상을 받은 계정이면 보상 버튼은 비활성, 로비로만
    var alreadyRewarded = false
    let onClaim: () -> Void

    var body: some View {
        ZStack {
            Color(hex: 0x302040, alpha: 0.8).ignoresSafeArea()
            VStack(spacing: 8) {
                Text("🎉").font(.system(size: 48))
                Text(tr("튜토리얼 완료!", "Tutorial complete!", zh: "教程完成！", ja: "チュートリアル完了！"))
                    .font(.system(size: 26, weight: .black)).foregroundColor(UiColor.primaryDark)
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("✂️ 분할: 한쪽 방울이 터지고 남은 방울이 짝수면 반으로 나눠 다시 두 개로! (한 판 3번)",
                            "✂️ Split: when one bubble is gone and the other is even, split it into two! (3 per game)",
                            zh: "✂️ 分裂：一个泡泡没了、剩下的是偶数时可以一分为二！（每局3次）", ja: "✂️ 分割：片方が割れて残りが偶数なら半分に分けて2つに！（1試合3回）"))
                    Text(tr("💖 부활물약: 터진 방울을 1마리로 되살려요 (상점·출석에서 얻어요)",
                            "💖 Revive potion: bring a popped bubble back with 1 (from the shop or check-ins)",
                            zh: "💖 复活药水：让破掉的泡泡以1只复活（商店或签到获得）", ja: "💖 復活薬：割れたバブルを1匹で復活（ショップや出席で入手）"))
                    Text(tr("⏱ 온라인 대전은 한 턴 20초! 시간을 넘기면 패배예요",
                            "⏱ Online turns are 20s. Run out of time and you lose!",
                            zh: "⏱ 在线对战每回合20秒，超时判负！", ja: "⏱ オンライン対戦は1ターン20秒！時間切れで負け"))
                }
                .font(.system(size: 14)).foregroundColor(UiColor.text)
                .padding(.vertical, 8)
                if alreadyRewarded {
                    Text(tr("✅ 보상은 이미 받았어요", "✅ Reward already claimed", zh: "✅ 奖励已领取", ja: "✅ 報酬は受け取り済み"))
                        .font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.text.opacity(0.45))
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Capsule().fill(Color.black.opacity(0.08)))
                    Button(action: onClaim) {
                        Text(tr("로비로", "Back to lobby", zh: "返回大厅", ja: "ロビーへ"))
                            .font(.system(size: 16, weight: .black)).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Capsule().fill(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: onClaim) {
                        Text(tr("🎁 보상 받기 · 💰 +\(TutorialScript.coins) · 💖 +1", "🎁 Claim · 💰 +\(TutorialScript.coins) · 💖 +1",
                                zh: "🎁 领取奖励 · 💰 +\(TutorialScript.coins) · 💖 +1", ja: "🎁 報酬を受け取る · 💰 +\(TutorialScript.coins) · 💖 +1"))
                            .font(.system(size: 17, weight: .black)).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .background(Capsule().fill(LinearGradient(colors: [Color(hex: 0xFFB300), UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(22)
            .background(RoundedRectangle(cornerRadius: 28).fill(LinearGradient(colors: [.white, Color(hex: 0xF6EEFF)], startPoint: .top, endPoint: .bottom)))
            .padding(24)
        }
    }
}
