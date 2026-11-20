import BubbleCore
import SwiftUI

struct GameView: View {
    @ObservedObject var game: LocalGame
    var myName: String
    var onLeave: () -> Void
    var onAgain: () -> Void

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
                    PlayerTag(side: opp, name: game.opponentName, active: game.turn == opp && !game.finished)
                    SplitCounter(left: game.board.splitsLeft(opp))
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
                    TurnTimerView(deadline: d, totalSec: game.level.turnSec, mine: game.turn == me)
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
                    PlayerTag(side: me, name: myName, active: game.myTurn)
                    SplitCounter(left: game.board.splitsLeft(me))
                }
                .padding(.top, 16)

                splitButton
                logView
            }
            .padding(.vertical, 12)

            if game.finished, let w = game.winner {
                ResultOverlay(won: w == me, side: me, reason: game.reason, onLobby: onLeave, onAgain: onAgain)
            }
        }
        .alert(t("게임에서 나갈까요?"), isPresented: $confirmLeave) {
            Button(t("계속하기"), role: .cancel) {}
            Button(t("나가기"), role: .destructive) { onLeave() }
        } message: {
            Text(t("연습 게임이 끝나요."))
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
            text = t("AI가 생각 중… 🤔")
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

    private var logView: some View {
        VStack(spacing: 2) {
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
    let onLobby: () -> Void
    let onAgain: () -> Void

    var body: some View {
        ZStack {
            Color(hex: 0x302040, alpha: 0.53).ignoresSafeArea()
            VStack(spacing: 8) {
                Critter(side: side, happy: won).frame(width: 120, height: 120)
                Text(won ? t("승리! 🎉") : t("아쉬운 패배…"))
                    .font(.system(size: 32, weight: .black))
                    .foregroundColor(won ? UiColor.primaryDark : UiColor.text)
                Text(reasonText).foregroundColor(UiColor.text.opacity(0.7))
                HStack(spacing: 10) {
                    Button(t("한 판 더"), action: onAgain)
                        .foregroundColor(UiColor.text)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .overlay(Capsule().stroke(UiColor.text.opacity(0.4)))
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
