import BubbleCore
import SwiftUI

struct GameView<M: GameModel>: View {
    @ObservedObject var game: M
    var onLeave: () -> Void
    /// AI 대전이면 "한 판 더"
    var onAgain: (() -> Void)?
    /// 졌을 때 로비로 가며 광고를 보면 돌려받는 점수 (없으면 nil)
    var rewardOffer: Int? = nil

    @State private var confirmLeave = false

    private var me: Side { game.mySide }
    private var opp: Side { game.mySide.opponent }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                HStack {
                    Button(t("← 나가기")) { if game.finished { onLeave() } else { confirmLeave = true } }
                        .foregroundColor(UiColor.text)
                    Spacer()
                }
                .padding(.horizontal, 16)

                HStack(spacing: 8) {
                    PlayerTag(side: opp, name: game.opponentName, active: game.turn == opp && !game.finished, skin: game.opponentSkin)
                    SplitCounter(left: game.board.splitsLeft(opp))
                    ReviveCounter(left: game.board.revivesLeft(opp))
                }
                .padding(.top, 6)

                HStack {
                    ForEach(0..<GameLogic.setCount, id: \.self) { i in
                        opponentBubble(i)
                        if i == 0 { Spacer() }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 14)

                Spacer(minLength: 8)
                turnBanner
                if let d = game.deadline, !game.finished {
                    TurnTimerView(deadline: d, totalSec: game.turnSec, mine: game.turn == me)
                        .padding(.top, 10)
                }
                Spacer(minLength: 8)

                HStack {
                    ForEach(0..<GameLogic.setCount, id: \.self) { i in
                        myBubble(i)
                        if i == 0 { Spacer() }
                    }
                }
                .padding(.horizontal, 20)

                HStack(spacing: 8) {
                    PlayerTag(side: me, name: game.myName, active: game.myTurn, skin: game.mySkin)
                    SplitCounter(left: game.board.splitsLeft(me))
                    ReviveCounter(left: game.board.revivesLeft(me))
                    if game.myBoost {
                        Text("🚀×3").font(.system(size: 13, weight: .black)).foregroundColor(Color(hex: 0xB23C00))
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill(UiColor.gold.opacity(0.6)))
                    }
                }
                .padding(.top, 16)

                splitButton
                reviveButton
                logView
            }
            .padding(.vertical, 12)

            if game.finished, let w = game.winner {
                ResultOverlay(won: w == me, side: me, reason: game.reason, delta: game.isOnline ? game.scoreDelta : nil,
                              online: game.isOnline, bonus: game.scoreBonus, boosted: game.myBoost, rewardOffer: rewardOffer,
                              onLobby: onLeave, onAgain: onAgain)
            }
        }
        .alert(t("게임에서 나갈까요?"), isPresented: $confirmLeave) {
            Button(t("계속하기"), role: .cancel) {}
            Button(t("나가기"), role: .destructive) { onLeave() }
        } message: {
            Text(!game.isOnline ? t("연습 게임이 끝나요.")
                 : game.isAi ? tr("지금 나가면 패배로 처리돼요.", "Leaving now counts as a loss.", zh: "现在离开将判负。", es: "Salir ahora cuenta como derrota.",
                                fr: "Partir maintenant compte comme une défaite.", de: "Jetzt zu gehen zählt als Niederlage.",
                                pt: "Sair agora conta como derrota.", ru: "Выход сейчас — поражение.", ja: "今退出すると負けになります。")
                 : tr("지금 나가면 기권패로 처리돼요 (-\(Shop.losePoints)점).", "Leaving now counts as a forfeit (-\(Shop.losePoints) pts).",
                      zh: "现在离开将判为弃权（-\(Shop.losePoints) 分）。", es: "Salir ahora cuenta como abandono (-\(Shop.losePoints) pts).",
                      fr: "Partir maintenant compte comme un abandon (-\(Shop.losePoints) pts).", de: "Jetzt zu gehen zählt als Aufgabe (-\(Shop.losePoints) Pkt).",
                      pt: "Sair agora conta como desistência (-\(Shop.losePoints) pts).", ru: "Выход сейчас — техническое поражение (-\(Shop.losePoints) очк.).",
                      ja: "今退出すると棄権負けになります（-\(Shop.losePoints)点）。"))
        }
    }

    private var power: Int { game.selected.map { game.board.sets(me)[$0] } ?? 0 }

    private func previewFor(_ target: Int) -> String {
        target + power >= GameLogic.explodeAt ? t("💥 펑!") : "→ \(target + power)"
    }

    private func explodeKey(_ side: Side, _ i: Int) -> Int? {
        guard let ex = game.explosion, ex.0.side == side, ex.0.index == i else { return nil }
        return ex.1
    }

    private func opponentBubble(_ i: Int) -> some View {
        let count = game.board.sets(opp)[i]
        let target = game.selected != nil && count > 0
        var action: (() -> Void)? = nil
        if target {
            action = { if let s = game.selected { game.play(.add(from: s, toOpponent: true, to: i)) } }
        }
        return SetBubbleView(
            side: opp, count: count, title: setName(i), mode: target ? .target : .normal, size: 140,
            preview: target ? previewFor(count) : nil, explodeKey: explodeKey(opp, i),
            onTap: action
        )
    }

    private func myBubble(_ i: Int) -> some View {
        let mine = game.board.sets(me)
        let isSel = game.selected == i
        let boostTarget = game.selected != nil && game.selected != i && mine[i] > 0
        let pickable = game.myTurn && mine[i] > 0
        let mode: BubbleMode = isSel ? .selected : boostTarget ? .target : (pickable ? .normal : .disabled)
        return SetBubbleView(
            side: me, count: mine[i], title: setName(i), mode: mode, size: 150,
            preview: boostTarget ? "💪 " + previewFor(mine[i]) : nil, explodeKey: explodeKey(me, i),
            onTap: {
                if boostTarget, let s = game.selected {
                    game.play(.add(from: s, toOpponent: false, to: i))
                } else if pickable {
                    game.selected = isSel ? nil : i
                }
            }
        )
    }

    private var turnBanner: some View {
        let text: String
        if game.finished {
            text = t("게임 끝!")
        } else if game.turn != me {
            text = game.isAi ? t("AI가 생각 중… 🤔") : t("상대가 고민 중이에요…")
        } else if game.selected != nil {
            text = t("어디에 더할까요? 반짝이는 방울을 눌러요")
        } else {
            text = t("내 차례! 내 방울을 하나 골라요")
        }
        return Text(text)
            .font(.system(size: 22, weight: .black))
            .multilineTextAlignment(.center)
            .foregroundColor(game.turn == me && !game.finished ? UiColor.primaryDark : UiColor.text)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 24).fill(.white.opacity(0.8)))
            .padding(.horizontal, 16)
            .animation(.spring(), value: text)
    }

    @ViewBuilder private var splitButton: some View {
        if let s = game.selected, GameLogic.splitShapeOk(game.board, me, s) {
            let left = game.board.splitsLeft(me)
            Button {
                game.play(.split(from: s))
            } label: {
                Text(left > 0
                     ? tr("✂️ 분할하기 (\(power / 2) + \(power / 2)) · 남은 \(left)회", "✂️ Split (\(power / 2) + \(power / 2)) · \(left) left",
                          zh: "✂️ 分裂 (\(power / 2) + \(power / 2)) · 剩 \(left) 次", es: "✂️ Dividir (\(power / 2) + \(power / 2)) · quedan \(left)",
                          fr: "✂️ Diviser (\(power / 2) + \(power / 2)) · \(left) restantes", de: "✂️ Teilen (\(power / 2) + \(power / 2)) · noch \(left)",
                          pt: "✂️ Dividir (\(power / 2) + \(power / 2)) · restam \(left)", ru: "✂️ Разделить (\(power / 2) + \(power / 2)) · осталось \(left)",
                          ja: "✂️ 分割 (\(power / 2) + \(power / 2)) · 残り\(left)回")
                     : t("✂️ 분할 횟수를 모두 썼어요"))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Capsule().fill(left > 0 ? UiColor.accent : Color.gray))
            }
            .disabled(left == 0)
            .padding(.top, 10)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    /// 💖 부활물약: 내 차례에 터진 방울이 있고 이번 판 물약이 남았으면
    @ViewBuilder private var reviveButton: some View {
        if game.myTurn, let target = (0..<GameLogic.setCount).first(where: { GameLogic.canRevive(game.board, me, $0) }) {
            Button {
                game.selected = nil
                game.play(.revive(index: target))
            } label: {
                Text(tf("💖 부활물약으로 {0} 살리기 · 남은 {1}개", setName(target), game.board.revivesLeft(me)))
                    .font(.system(size: 15, weight: .bold)).foregroundColor(.white)
                    .padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Capsule().fill(Color(hex: 0xE0457B)))
            }
            .padding(.top, 8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    private var logView: some View {
        VStack(spacing: 2) {
            if let n = game.notice {
                Text(n).font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.danger).multilineTextAlignment(.center)
            }
            ForEach(Array(game.log.prefix(2).enumerated()), id: \.offset) { i, line in
                Text(line)
                    .font(.system(size: i == 0 ? 16 : 14, weight: i == 0 ? .heavy : .semibold))
                    .foregroundColor(UiColor.text.opacity(i == 0 ? 1 : 0.55))
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.top, 8)
        .padding(.horizontal, 16)
    }
}

struct ResultOverlay: View {
    let won: Bool
    let side: Side
    let reason: String
    var delta: Int? = nil
    var online = false
    var bonus = 0
    var boosted = false
    var rewardOffer: Int? = nil
    let onLobby: () -> Void
    let onAgain: (() -> Void)?

    var body: some View {
        ZStack {
            Color(hex: 0x302040, alpha: 0.53).ignoresSafeArea()
            VStack(spacing: 8) {
                Critter(side: side, happy: won).frame(width: 120, height: 120)
                Text(won ? t("승리! 🎉") : t("아쉬운 패배…"))
                    .font(.system(size: 32, weight: .black))
                    .foregroundColor(won ? UiColor.primaryDark : UiColor.text)
                Text(reasonText).foregroundColor(UiColor.text.opacity(0.7))
                if online {
                    Text(delta.map { d in d >= 0 ? "+" + pts(d) : pts(d) } ?? t("점수 계산 중…"))
                        .font(.system(size: 26, weight: .black))
                        .foregroundColor((delta ?? 0) >= 0 ? UiColor.accent : UiColor.danger)
                    if bonus > 0 {
                        Text(tr("✨ 계급 보너스 +\(bonus)점 포함", "✨ Includes rank bonus +\(bonus)", zh: "✨ 含等级奖励 +\(bonus)",
                                es: "✨ Incluye bonus de rango +\(bonus)", fr: "✨ Bonus de rang inclus +\(bonus)", de: "✨ Inkl. Rangbonus +\(bonus)",
                                pt: "✨ Inclui bônus de rank +\(bonus)", ru: "✨ С бонусом ранга +\(bonus)", ja: "✨ ランクボーナス +\(bonus) 込み"))
                            .font(.system(size: 14, weight: .bold)).foregroundColor(Color(hex: 0xD08A00))
                    }
                    if boosted, (delta ?? 0) > 0 {
                        Text(t("🚀 ×3 부스터 적용!")).font(.system(size: 16, weight: .black)).foregroundColor(UiColor.primaryDark)
                    }
                    if let r = rewardOffer {
                        Text(tf("📺 로비로 갈 때 광고를 보면 {0}점을 돌려받아요", r))
                            .font(.system(size: 13, weight: .bold)).foregroundColor(UiColor.accent).multilineTextAlignment(.center)
                    }
                }
                HStack(spacing: 10) {
                    if let onAgain {
                        Button(t("한 판 더"), action: onAgain)
                            .foregroundColor(UiColor.text)
                            .padding(.horizontal, 18).padding(.vertical, 10)
                            .overlay(Capsule().stroke(UiColor.text.opacity(0.4)))
                    }
                    Button(t("로비로"), action: onLobby)
                        .foregroundColor(.white)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(Capsule().fill(UiColor.primary))
                }
                .padding(.top, 10)
            }
            .padding(28)
            .background(RoundedRectangle(cornerRadius: 32).fill(.white))
            .padding(32)
        }
    }

    private var reasonText: String {
        switch reason {
        case "forfeit": return won ? t("상대가 나갔어요") : t("기권했어요")
        case "timeout": return won ? t("상대가 시간을 넘겼어요") : t("시간을 넘겼어요")
        default: return won ? t("상대 방울을 모두 터뜨렸어요!") : t("내 방울이 모두 터졌어요")
        }
    }
}
