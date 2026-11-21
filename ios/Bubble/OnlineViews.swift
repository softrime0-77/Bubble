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
        // 반짝이 Canvas가 남는 공간을 다 차지하지 않게 글자 크기에 맞춤
        .fixedSize()
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

/// 친구 대전: 대전 링크를 보내고 친구를 기다리는 화면
struct FriendWaitView: View {
    let code: String?
    let startedAt: Date
    let onCancel: () -> Void
    @State private var copied = false

    private func shareText(_ c: String) -> String {
        let link = OnlineService.battleLink(c)
        return tr("Bubble 냥햄대전에서 나랑 한 판 붙자! ⚔️🐱🐹\n대전 코드: \(c)\n\(link)",
                  "Battle me in Bubble: Cats vs Hamsters! ⚔️🐱🐹\nBattle code: \(c)\n\(link)",
                  zh: "来 Bubble 猫咪大战仓鼠和我对战吧！⚔️🐱🐹\n对战码：\(c)\n\(link)",
                  es: "¡Juega una partida conmigo en Bubble! ⚔️🐱🐹\nCódigo: \(c)\n\(link)",
                  fr: "Viens m’affronter dans Bubble ! ⚔️🐱🐹\nCode : \(c)\n\(link)",
                  de: "Spiel eine Runde Bubble gegen mich! ⚔️🐱🐹\nCode: \(c)\n\(link)",
                  pt: "Jogue uma partida de Bubble comigo! ⚔️🐱🐹\nCódigo: \(c)\n\(link)",
                  ru: "Сразись со мной в Bubble! ⚔️🐱🐹\nКод боя: \(c)\n\(link)",
                  ja: "Bubble で一緒に対戦しよう！⚔️🐱🐹\n対戦コード：\(c)\n\(link)")
    }

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { tl in
            let sec = Int(tl.date.timeIntervalSince(startedAt))
            let left = max(0, Int(OnlineService.friendWait) - sec)
            let swing = sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.3)
            VStack(spacing: 14) {
                Spacer()
                HStack {
                    Critter(side: .cat).frame(width: 96, height: 96).offset(x: swing * 10)
                    Text("👫").font(.system(size: 34))
                    Critter(side: .hamster).frame(width: 96, height: 96).offset(x: -swing * 10)
                }
                if let code {
                    Text(tr("친구에게 링크를 보내 주세요", "Send this link to a friend", zh: "把链接发给朋友", es: "Envía este enlace a un amigo",
                            fr: "Envoie ce lien à un ami", de: "Schick diesen Link an einen Freund", pt: "Envie este link para um amigo",
                            ru: "Отправьте ссылку другу", ja: "友だちにリンクを送ってください"))
                        .font(.system(size: 20, weight: .bold)).foregroundColor(UiColor.text).multilineTextAlignment(.center)
                    Text(code).font(.system(size: 32, weight: .black)).kerning(4).foregroundColor(UiColor.primaryDark)
                        .padding(.horizontal, 18).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 14).fill(.white))
                    ShareLink(item: shareText(code)) {
                        Text(tr("💬 링크 보내기", "💬 Send link", zh: "💬 发送链接", es: "💬 Enviar enlace", fr: "💬 Envoyer le lien",
                                de: "💬 Link senden", pt: "💬 Enviar link", ru: "💬 Отправить ссылку", ja: "💬 リンクを送る"))
                            .font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                            .padding(.horizontal, 22).padding(.vertical, 12)
                            .background(Capsule().fill(UiColor.accent))
                    }
                    Button(copied ? t("✅ 링크를 복사했어요")
                           : tr("🔗 링크 복사", "🔗 Copy link", zh: "🔗 复制链接", es: "🔗 Copiar enlace", fr: "🔗 Copier le lien",
                                de: "🔗 Link kopieren", pt: "🔗 Copiar link", ru: "🔗 Скопировать ссылку", ja: "🔗 リンクをコピー")) {
                        UIPasteboard.general.string = OnlineService.battleLink(code)
                        copied = true
                    }
                    .font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.accent)
                    Text(tr("친구를 기다리는 중", "Waiting for your friend", zh: "正在等待朋友", es: "Esperando a tu amigo",
                            fr: "En attente de ton ami", de: "Warte auf deinen Freund", pt: "Esperando seu amigo", ru: "Ждём друга",
                            ja: "友だちを待っています") + String(repeating: ".", count: sec % 4)
                         + String(format: "  %d:%02d", left / 60, left % 60))
                        .foregroundColor(UiColor.text.opacity(0.75))
                    Text(tr("승리 +\(Shop.friendWinPoints)점 · 패배 -\(Shop.friendLosePoints)점\n같은 친구와는 하루 \(Shop.friendRatedPerDay)판까지 점수가 걸려요",
                            "Win +\(Shop.friendWinPoints) · Lose -\(Shop.friendLosePoints)\nOnly \(Shop.friendRatedPerDay) scored games per friend per day",
                            zh: "胜 +\(Shop.friendWinPoints) · 负 -\(Shop.friendLosePoints)\n同一好友每天最多 \(Shop.friendRatedPerDay) 局计分",
                            es: "Gana +\(Shop.friendWinPoints) · Pierde -\(Shop.friendLosePoints)\nSolo \(Shop.friendRatedPerDay) partidas con puntos por amigo al día",
                            fr: "Victoire +\(Shop.friendWinPoints) · Défaite -\(Shop.friendLosePoints)\n\(Shop.friendRatedPerDay) parties notées max. par ami et par jour",
                            de: "Sieg +\(Shop.friendWinPoints) · Niederlage -\(Shop.friendLosePoints)\nNur \(Shop.friendRatedPerDay) gewertete Partien pro Freund und Tag",
                            pt: "Vitória +\(Shop.friendWinPoints) · Derrota -\(Shop.friendLosePoints)\nSó \(Shop.friendRatedPerDay) partidas valendo pontos por amigo por dia",
                            ru: "Победа +\(Shop.friendWinPoints) · Поражение -\(Shop.friendLosePoints)\nНе больше \(Shop.friendRatedPerDay) игр на очки с одним другом в день",
                            ja: "勝ち +\(Shop.friendWinPoints)点 · 負け -\(Shop.friendLosePoints)点\n同じ友だちとは1日\(Shop.friendRatedPerDay)戦まで点数がかかります"))
                        .font(.system(size: 13, weight: .bold)).foregroundColor(UiColor.accent).multilineTextAlignment(.center)
                } else {
                    ProgressView()
                    Text(tr("대전방을 만드는 중…", "Creating a room…", zh: "正在创建房间…", es: "Creando sala…", fr: "Création de la salle…",
                            de: "Raum wird erstellt…", pt: "Criando sala…", ru: "Создаём комнату…", ja: "ルームを作成中…"))
                        .foregroundColor(UiColor.text)
                }
                Button(t("취소"), action: onCancel)
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
                     : game.isFriend
                     ? tr("👫 친구와 대전!", "👫 Friend battle!", zh: "👫 好友对战！", es: "👫 ¡Duelo entre amigos!", fr: "👫 Duel entre amis !",
                          de: "👫 Freundesduell!", pt: "👫 Duelo entre amigos!", ru: "👫 Бой с другом!", ja: "👫 友だちと対戦！")
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
