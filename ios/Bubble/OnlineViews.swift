import BubbleCore
import SwiftUI

/// 계급 배지: 흙은 효과 없음, 높을수록 반짝이가 많고 다이아는 무지개빛 (안드로이드 RankBadge와 같은 단계)
struct RankBadge: View {
    let score: Int
    var large = false

    private var rank: Rank { Rank.of(score) }

    private func colors(_ hue: Double) -> [Color] {
        switch rank {
        case .dirt: return [Color(hex: 0x9C7A5B), Color(hex: 0x7A5A3E)]
        case .copper: return [Color(hex: 0xF0A36B), Color(hex: 0xB8673A), Color(hex: 0xE38B52)]
        case .silver: return [Color(hex: 0xF4F6FA), Color(hex: 0xAEB6C4), Color(hex: 0xE2E6EE)]
        case .gold: return [Color(hex: 0xFFF1A8), Color(hex: 0xFFC02E), Color(hex: 0xE59A00)]
        case .ruby: return [Color(hex: 0xFF8FB0), Color(hex: 0xE0115F), Color(hex: 0x9B0B3F)]
        case .sapphire: return [Color(hex: 0x9AD7FF), Color(hex: 0x1F6FEB), Color(hex: 0x0B3D91)]
        case .diamond: return (0..<6).map { i in Color(hue: (hue + Double(i) * 50 / 360).truncatingRemainder(dividingBy: 1), saturation: 0.32, brightness: 1) }
        }
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let time = tl.date.timeIntervalSinceReferenceDate
            let hue = (time / 4).truncatingRemainder(dividingBy: 1)
            let darkText = [Rank.silver, .gold, .diamond].contains(rank)
            let sparkles = [0, 2, 4, 6, 8, 11, 16][rank.rawValue]
            ZStack {
                Text("\(rank.emoji) \(rank.label)")
                    .font(.system(size: large ? 18 : 12, weight: .black))
                    .foregroundColor(darkText ? UiColor.text : .white)
                    .lineLimit(1)
                    .fixedSize()
                    .padding(.horizontal, large ? 14 : 8).padding(.vertical, large ? 6 : 2)
                    .background(Capsule().fill(LinearGradient(colors: colors(hue), startPoint: .topLeading, endPoint: .bottomTrailing)))
                    .shadow(color: rank.rawValue >= 3 ? colors(hue)[1].opacity(0.6) : .clear, radius: CGFloat(rank.rawValue - 2) * 2)
                if sparkles > 0 {
                    Canvas { ctx, size in
                        var g = SeededRandom(seed: UInt64(rank.rawValue * 31 + 7))
                        for i in 0..<sparkles {
                            let sx = g.next(), sy = g.next(), ph = g.next()
                            let p = (time / 1.3 + Double(ph)).truncatingRemainder(dividingBy: 1)
                            let a = sin(p * .pi)
                            let pos = CGPoint(x: (sx * 1.3 - 0.15) * size.width, y: (sy * 1.8 - 0.4) * size.height)
                            let color: Color = rank == .diamond
                                ? Color(hue: (hue + Double(i) * 47 / 360).truncatingRemainder(dividingBy: 1), saturation: 0.5, brightness: 1)
                                : (rank.rawValue >= 3 ? Color(hex: 0xFFF4B0) : .white)
                            ctx.fill(sparklePath(pos, (large ? 7 : 4.5) * (0.4 + a * 0.8)), with: .color(color.opacity(a)))
                        }
                    }
                    .allowsHitTesting(false)
                }
            }
        }
    }
}

/// 매칭 기다리는 화면: 경과 시간 + "N초 안에 상대가 없으면 AI(어려움)"
struct MatchingView: View {
    let startedAt: Date
    let onCancel: () -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.5)) { tl in
            let sec = Int(tl.date.timeIntervalSince(startedAt))
            let left = max(0, Int(OnlineService.matchTimeout) - sec)
            let swing = sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.3)
            VStack(spacing: 14) {
                Spacer()
                HStack {
                    Critter(side: .cat).frame(width: 110, height: 110).offset(x: swing * 14, y: -abs(swing) * 12)
                    Text("VS").font(.system(size: 30, weight: .black)).foregroundColor(UiColor.accent)
                    Critter(side: .hamster).frame(width: 110, height: 110).offset(x: -swing * 14, y: -abs(swing) * 12)
                }
                Text(t("상대를 찾고 있어요") + String(repeating: ".", count: sec % 4))
                    .font(.system(size: 22, weight: .bold)).foregroundColor(UiColor.text)
                Text(String(format: "%d:%02d", sec / 60, sec % 60)).foregroundColor(UiColor.text.opacity(0.7))
                Text(tr("\(left)초 안에 상대가 없으면 🤖 AI(어려움)와 대결해요", "No opponent in \(left)s? You'll face 🤖 AI (Hard)",
                        zh: "\(left) 秒内没有对手，将与 🤖 AI（困难）对战", es: "¿Sin rival en \(left) s? Jugarás contra 🤖 IA (Difícil)",
                        fr: "Pas d’adversaire dans \(left) s ? Tu affronteras 🤖 l’IA (Difficile)", de: "Kein Gegner in \(left) s? Dann spielst du gegen 🤖 KI (Schwer)",
                        pt: "Sem rival em \(left) s? Você enfrentará 🤖 IA (Difícil)", ru: "Нет соперника за \(left) с? Сыграете с 🤖 ИИ (Сложно)",
                        ja: "\(left)秒以内に相手がいなければ 🤖 AI（むずかしい）と対戦します"))
                    .font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.accent).multilineTextAlignment(.center)
                Button(t("매칭 취소"), action: onCancel)
                    .foregroundColor(UiColor.text)
                    .padding(.horizontal, 22).padding(.vertical, 10)
                    .overlay(Capsule().stroke(UiColor.text.opacity(0.4)))
                    .padding(.top, 18)
                Spacer()
            }
            .padding(24)
        }
    }
}

/// "KR" → 🇰🇷
func flag(_ code: String?) -> String {
    guard let c = code?.uppercased(), c.count == 2, c.allSatisfy({ $0.isASCII && $0.isLetter }) else { return "🌐" }
    return c.unicodeScalars.compactMap { UnicodeScalar(0x1F1E6 + $0.value - 65) }.map { String($0) }.joined()
}

func countryName(_ code: String?) -> String {
    guard let c = code else { return "" }
    return Locale(identifier: LangStore.shared.code.rawValue).localizedString(forRegionCode: c) ?? c
}

/// 매칭 직후 3초 VS 소개 (나라·계급)
struct VsIntro: View {
    @ObservedObject var game: OnlineGame
    let onClose: () -> Void

    var body: some View {
        ZStack {
            Color(hex: 0x201530, alpha: 0.94).ignoresSafeArea()
            VStack(spacing: 20) {
                Text(game.fallbackAi
                     ? tr("상대를 찾지 못해 🤖 AI(어려움)와 대결해요", "No opponent found, so you'll face 🤖 AI (Hard)",
                          zh: "没有找到对手，将与 🤖 AI（困难）对战", es: "No hubo rival, así que juegas contra 🤖 IA (Difícil)",
                          fr: "Aucun adversaire trouvé : tu affrontes 🤖 l’IA (Difficile)", de: "Kein Gegner gefunden, du spielst gegen 🤖 KI (Schwer)",
                          pt: "Nenhum rival encontrado, você vai enfrentar 🤖 IA (Difícil)", ru: "Соперник не найден — играете с 🤖 ИИ (Сложно)",
                          ja: "相手が見つからなかったので 🤖 AI（むずかしい）と対戦します")
                     : t("⚔️ 매칭 완료!"))
                    .font(.system(size: 22, weight: .black)).foregroundColor(.white).multilineTextAlignment(.center)
                HStack(spacing: 10) {
                    card(game.mySide, game.mySkin, game.myName, game.myCountry, game.myScore, ai: false)
                    Text("VS").font(.system(size: 30, weight: .black)).foregroundColor(UiColor.gold)
                    card(game.mySide.opponent, game.opponentSkin, game.opponentName, game.opponentCountry, game.opponentScore, ai: game.isAi)
                }
                .fixedSize(horizontal: false, vertical: true)
            }
            .padding(20)
        }
        .onTapGesture(perform: onClose)
    }

    private func card(_ side: Side, _ skin: Animal?, _ name: String, _ country: String?, _ score: Int, ai: Bool) -> some View {
        VStack(spacing: 4) {
            Critter(side: side, skin: skin, happy: true).frame(width: 72, height: 72)
            Text(ai ? "🤖" : flag(country)).font(.system(size: 30))
            if !ai, country != nil {
                Text(countryName(country)).font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.7)).lineLimit(1)
            }
            Text(name).font(.system(size: 15, weight: .black)).foregroundColor(UiColor.text).lineLimit(1)
            if !ai, score > 0 { RankBadge(score: score) }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 24).fill(.white))
    }
}
