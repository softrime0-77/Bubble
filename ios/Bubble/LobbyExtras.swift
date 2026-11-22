import BubbleCore
import SwiftUI
import UIKit

/// 게임 규칙 (안드로이드 RulesDialog와 같은 문장)
struct RulesSheet: View {
    let onClose: () -> Void

    private var text: String {
        t("• 고양이와 햄스터가 각자 방울(세트) 2개에 1마리씩 들고 시작해요. 고양이가 먼저!\n\n")
            + t("• 내 차례에는 내 방울 하나를 골라요. 그 방울의 마리 수만큼\n")
            + t("   ⚔️ 상대 방울에 더하거나 (공격)\n")
            + t("   💪 내 다른 방울에 더할 수 있어요 (강화)\n\n")
            + t("• 방울이 5마리 이상이 되면 💥 펑! 터져서 0마리가 돼요.\n\n")
            + t("• 0마리 방울은 공격도 강화도 못 해요. 살리는 방법은 분할뿐!\n")
            + t("   ✂️ 분할: 한 방울이 짝수(2·4마리)이고 다른 방울이 0일 때 반씩 나눠요.\n")
            + tf("   ✂️ 분할은 한 게임에 한 사람당 최대 {0}번까지!\n\n", GameLogic.maxSplits)
            + t("• 내 방울 2개가 모두 터지면 져요.\n\n")
            + t("• 💖 부활물약(상점)이 있으면 터진 방울을 1마리로 살릴 수 있어요. 한 판에 최대 3번!\n\n")
            + tf("• 온라인 대전은 한 턴에 {0}초! 시간을 넘기면 패배예요.\n", Shop.defaultTurnSec)
    }

    var body: some View {
        sheetFrame(title: t("게임 규칙"), onClose: onClose) {
            Text(text).font(.system(size: 16)).foregroundColor(UiColor.text).lineSpacing(3)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// 계급표: 모든 계급, 점수 범위, 보너스, 내 계급, 다음 계급까지 남은 점수
struct RanksSheet: View {
    let myScore: Int
    let onClose: () -> Void

    var body: some View {
        let mine = Rank.of(myScore)
        let next = Rank(rawValue: mine.rawValue + 1)
        sheetFrame(title: t("🏅 계급표"), onClose: onClose) {
            ForEach(Rank.allCases.reversed(), id: \.self) { r in
                HStack(spacing: 10) {
                    RankBadge(score: r == .dirt ? 1000 : r.minScore, large: true)
                        .frame(width: 140, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(range(r)).font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.text)
                        Text(r.bonusPercent > 0 ? tf("🎁 얻는 점수 +{0}% 추가", r.bonusPercent) : t("보너스 없음"))
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(r.bonusPercent > 0 ? Color(hex: 0xD08A00) : UiColor.text.opacity(0.5))
                        if r == mine {
                            Text(t("👈 지금 내 계급")).font(.system(size: 12, weight: .black)).foregroundColor(UiColor.accent)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 8).padding(.vertical, 5)
                .background(RoundedRectangle(cornerRadius: 14).fill(r == mine ? UiColor.primary.opacity(0.15) : .clear))
            }
            Text(next.map { tf("내 점수 {0}점 · {1}까지 {2}점 남았어요!", myScore, $0.label, $0.minScore - myScore) }
                 ?? tf("내 점수 {0}점 · 최고 계급이에요! 💎", myScore))
                .font(.system(size: 15, weight: .black)).foregroundColor(UiColor.accent).multilineTextAlignment(.center)
                .padding(.top, 8)
            Text(t("🎁 계급 보너스: 이겨서 얻는 점수(랜덤 매칭·AI 대전)에 계급만큼 점수를 더 받아요. ")
                 + t("예) 다이아가 랜덤 매칭에서 이기면 30점 + 30%(9점) = 39점"))
                .font(.system(size: 13, weight: .semibold)).foregroundColor(UiColor.text)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xFFC94D, alpha: 0.13)))
            Text(t("높은 계급일수록 배지가 더 반짝여요 ✨")).font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.6))
        }
    }

    private func range(_ r: Rank) -> String {
        switch r {
        case .dirt: return t("1000점 이하")
        case .pinkDiamond: return tf("{0}점 이상", r.minScore)
        default: return tf("{0} ~ {1}점", r.minScore, Rank(rawValue: r.rawValue + 1)!.minScore - 1)
        }
    }
}

/// 시트 공통 틀: 제목, 스크롤 내용, 닫기
func sheetFrame<C: View>(title: String, onClose: @escaping () -> Void, @ViewBuilder content: () -> C) -> some View {
    ZStack {
        LinearGradient(colors: [UiColor.bgTop, UiColor.bgBottom], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
        VStack(spacing: 10) {
            Text(title).font(.system(size: 24, weight: .black)).foregroundColor(UiColor.primaryDark).padding(.top, 20)
            ScrollView {
                VStack(spacing: 6) { content() }
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 24).fill(.white.opacity(0.9)))
                    .padding(.horizontal, 16)
            }
            Button(t("닫기"), action: onClose)
                .font(.system(size: 17, weight: .bold)).foregroundColor(.white)
                .padding(.horizontal, 40).padding(.vertical, 12)
                .background(Capsule().fill(UiColor.primary))
                .padding(.bottom, 12)
        }
    }
}

/// 친구 초대: 내 코드 공유 · 링크 복사 · 친구 코드 입력
struct InviteCard: View {
    @ObservedObject var online = OnlineService.shared
    let profile: Profile
    @State private var input = ""
    @State private var copied = false

    var body: some View {
        card {
            Text(t("🎁 친구 초대")).font(.system(size: 17, weight: .bold)).foregroundColor(UiColor.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(t("친구가 내 코드를 입력하면 나도 친구도 +30점!")).font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.75))
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack {
                Text(profile.refCode ?? "······")
                    .font(.system(size: 24, weight: .black)).kerning(3).foregroundColor(UiColor.primaryDark)
                    .padding(.horizontal, 14).padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 12).fill(UiColor.bgTop))
                Spacer()
                if let code = profile.refCode {
                    ShareLink(item: tf("Bubble 냥햄대전에서 같이 놀자! 🐱🐹\n추천 코드: {0}\n{1}", code, OnlineService.inviteLink(code))) {
                        Text(t("공유하기")).font(.system(size: 15, weight: .bold)).foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(Capsule().fill(UiColor.accent))
                    }
                }
            }
            if let code = profile.refCode {
                Button {
                    UIPasteboard.general.string = OnlineService.inviteLink(code)
                    copied = true
                } label: {
                    Text(copied ? t("✅ 링크를 복사했어요") : t("🔗 초대 링크 복사"))
                        .font(.system(size: 13, weight: .bold)).foregroundColor(UiColor.accent)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if profile.referrals > 0 {
                Text(tf("초대한 친구 {0}명", profile.referrals)).font(.system(size: 12, weight: .bold)).foregroundColor(UiColor.accent)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !profile.referred {
                HStack(spacing: 8) {
                    TextField(t("친구 코드 입력"), text: $input)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).stroke(UiColor.text.opacity(0.3)))
                        .onChange(of: input) { v in
                            let clean = String(v.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }.prefix(6))
                            if clean != v { input = clean }
                        }
                    Button {
                        let code = input
                        input = ""
                        Task { await online.redeemReferral(code) }
                    } label: {
                        Text(t("확인")).font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.accent)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .overlay(Capsule().stroke(UiColor.accent.opacity(0.6)))
                    }
                    .disabled(input.count != 6 || online.busy)
                }
            }
        }
        .onAppear { online.loadReferralCode() }
    }
}

/// 승급 축하: 계급이 높을수록 반짝이가 많이, 크게 터짐 (누르면 닫힘)
struct RankUpCelebration: View {
    let score: Int
    let onClose: () -> Void
    @State private var startedAt = Date()
    @State private var pop = false

    var body: some View {
        let rank = Rank.of(score)
        let count = 16 + rank.sparkle * 14
        ZStack {
            Color(hex: 0x201530, alpha: 0.67).ignoresSafeArea()
            TimelineView(.animation) { tl in
                let p = min(1, tl.date.timeIntervalSince(startedAt) / 1.8)
                let ease = 1 - pow(1 - p, 3)
                Canvas { ctx, size in
                    var g = SeededRandom(seed: UInt64(score))
                    let center = CGPoint(x: size.width / 2, y: size.height / 2)
                    for i in 0..<count {
                        let a = g.next(), s = g.next(), h = g.next()
                        let ang = (Double(i) / Double(count) + Double(a) * 0.05) * 2 * .pi
                        let dist = min(size.width, size.height) * (0.1 + ease * (0.35 + Double(s) * 0.3))
                        let pos = CGPoint(x: center.x + cos(ang) * dist, y: center.y + sin(ang) * dist + ease * ease * 120)
                        let color: Color
                        switch rank {
                        case .pinkDiamond: color = [Color(hex: 0xFF4FA8), Color(hex: 0xFFB3E0), .white, Color(hex: 0xE3A6FF), Color(hex: 0xFFD6EC)][i % 5]
                        case .diamond: color = Color(hue: Double(h), saturation: 0.55, brightness: 1)
                        case .gold: color = [UiColor.gold, Color(hex: 0xFFF4B0), .white][i % 3]
                        case .ruby: color = [Color(hex: 0xE0115F), Color(hex: 0xFF8FB0), .white][i % 3]
                        case .sapphire: color = [Color(hex: 0x1F6FEB), Color(hex: 0x9AD7FF), .white][i % 3]
                        default: color = [Color(hex: 0xF0A36B), .white][i % 2]
                        }
                        let r = (1 - ease * 0.7) * (6 + Double(s) * (4 + Double(rank.sparkle) * 3))
                        ctx.fill(rank == .pinkDiamond && i % 3 == 0 ? heartPath(pos, r * 0.8) : sparklePath(pos, r), with: .color(color.opacity(1 - ease * 0.6)))
                    }
                }
            }
            .ignoresSafeArea()
            VStack(spacing: 18) {
                Text(t("🎉 승급! 🎉")).font(.system(size: 34, weight: .black)).foregroundColor(.white)
                RankBadge(score: score, large: true).scaleEffect(1.6).padding(.vertical, 8)
                Text(tf("{0} 계급이 되었어요!", rank.label)).font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                Text(t("화면을 누르면 닫혀요")).font(.system(size: 13)).foregroundColor(.white.opacity(0.7))
            }
            .scaleEffect(pop ? 1 : 0.2)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClose)
        .onAppear {
            startedAt = Date()
            withAnimation(.spring(response: 0.6, dampingFraction: 0.45)) { pop = true }
        }
    }
}

/// 보상형 광고 안내 (AdMob 정책: 광고 전에 보상을 알리고 건너뛸 수 있어야 함). 5초 뒤 자동 시작.
struct RewardIntro: View {
    let points: Int
    let onSkip: () -> Void
    let onWatch: () -> Void
    @State private var remain = 5
    @State private var done = false

    var body: some View {
        ZStack {
            Color(hex: 0x201530, alpha: 0.8).ignoresSafeArea()
            VStack(spacing: 8) {
                Text("📺").font(.system(size: 44))
                Text(tf("광고를 보면\n잃은 점수 중 {0}점을 돌려드려요", points))
                    .font(.system(size: 20, weight: .black)).foregroundColor(UiColor.text).multilineTextAlignment(.center)
                Text(tf("{0}초 후 광고가 시작돼요", remain)).font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.accent)
                Text(t("광고를 끝까지 봐야 점수가 돌아와요")).font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.6))
                HStack(spacing: 10) {
                    Button { finish(onSkip) } label: {
                        Text(t("괜찮아요 (건너뛰기)")).foregroundColor(UiColor.text)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .overlay(Capsule().stroke(UiColor.text.opacity(0.4)))
                    }
                    Button { finish(onWatch) } label: {
                        Text(t("지금 보기")).font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(Capsule().fill(UiColor.accent))
                    }
                }
                .padding(.top, 10)
            }
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 28).fill(.white))
            .padding(28)
        }
        .task {
            while remain > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if done { return }
                remain -= 1
            }
            finish(onWatch)
        }
    }

    private func finish(_ action: () -> Void) {
        guard !done else { return }
        done = true
        action()
    }
}


/// 로비: 📅 출석 체크(7일 주기) + 🎯 오늘의 미션 3개 (안드로이드 DailyCard와 같은 내용)
struct DailyCard: View {
    let profile: Profile
    @ObservedObject var online = OnlineService.shared

    private struct Mission: Identifiable {
        let id: String
        let label: String
        let now: Int
        let goal: Int
        let coins: Int
    }

    private var today: String { OnlineService.today() }
    private var yesterday: String { OnlineService.today(Date().addingTimeInterval(-86_400)) }
    private var checkedToday: Bool { profile.checkinDay == today }
    private var streak: Int { checkedToday || profile.checkinDay == yesterday ? profile.checkinStreak : 0 }
    private var nextDay: Int? { checkedToday ? nil : (streak % 7) + 1 }

    private var missions: [Mission] {
        let same = profile.missionDay == today
        return [
            Mission(id: "PLAY3", label: tr("아무 대전 3판 하기", "Play 3 battles", zh: "进行 3 场对战", es: "Juega 3 batallas", fr: "Joue 3 combats",
                                           de: "Spiele 3 Duelle", pt: "Jogue 3 batalhas", ru: "Сыграйте 3 боя", ja: "対戦を3回する"),
                    now: same ? profile.mPlay : 0, goal: 3, coins: 20),
            Mission(id: "WIN2", label: tr("2판 이기기", "Win 2 battles", zh: "赢 2 场", es: "Gana 2 batallas", fr: "Gagne 2 combats",
                                          de: "Gewinne 2 Duelle", pt: "Vença 2 batalhas", ru: "Выиграйте 2 боя", ja: "2回勝つ"),
                    now: same ? profile.mWin : 0, goal: 2, coins: 30),
            Mission(id: "RANDOM1", label: tr("랜덤 매칭 1판 하기", "Play 1 random match", zh: "进行 1 场随机匹配", es: "Juega 1 partida aleatoria",
                                             fr: "Joue 1 match aléatoire", de: "Spiele 1 Zufallsmatch", pt: "Jogue 1 partida aleatória",
                                             ru: "Сыграйте 1 случайный матч", ja: "ランダムマッチを1回する"),
                    now: same ? profile.mRandom : 0, goal: 1, coins: 20),
        ]
    }

    var body: some View {
        card {
            checkinHeader
            HStack(spacing: 4) {
                ForEach(0..<7, id: \.self) { i in dayBox(i) }
            }
            Text(tr("하루라도 빠지면 1일부터 다시 시작해요 · 7일째는 💖 부활물약도!", "Miss a day and it restarts at day 1 · Day 7 adds a 💖 potion!",
                    zh: "中断一天就从第 1 天重新开始 · 第 7 天还送 💖 复活药水！", es: "Si fallas un día, vuelves al día 1 · ¡El día 7 trae una 💖 poción!",
                    fr: "Un jour manqué et tout recommence · Le jour 7 donne une 💖 potion !", de: "Ein Tag verpasst – zurück zu Tag 1 · Tag 7 gibt einen 💖 Trank!",
                    pt: "Perdeu um dia, volta ao dia 1 · O dia 7 dá uma 💖 poção!", ru: "Пропустите день — начнёте с 1-го · На 7-й день ещё 💖 зелье!",
                    ja: "1日でも休むと1日目から · 7日目は 💖 復活薬も！"))
                .font(.system(size: 11)).foregroundColor(UiColor.text.opacity(0.6))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(tr("🎯 오늘의 미션", "🎯 Today's missions", zh: "🎯 今日任务", es: "🎯 Misiones de hoy", fr: "🎯 Missions du jour",
                    de: "🎯 Heutige Missionen", pt: "🎯 Missões de hoje", ru: "🎯 Задания дня", ja: "🎯 今日のミッション"))
                .font(.system(size: 17, weight: .black)).foregroundColor(UiColor.text)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 8)
            ForEach(missions) { m in missionRow(m) }
            Text(tr("AI 대전·친구 대전(점수 걸린 판)도 들어가요 · 매일 0시에 새로 시작", "AI and scored friend battles count too · resets daily at 00:00 (KST)",
                    zh: "AI 对战和计分好友对战也算 · 每天 0 点（韩国时间）重置", es: "Cuentan la IA y los duelos con puntos · se reinicia a las 00:00 (KST)",
                    fr: "L’IA et les duels notés comptent · remise à zéro à 00h (KST)", de: "KI- und gewertete Freundesduelle zählen · Neustart um 0 Uhr (KST)",
                    pt: "IA e duelos valendo pontos contam · reinicia às 00h (KST)", ru: "Бои с ИИ и с друзьями на очки тоже считаются · сброс в 00:00 (KST)",
                    ja: "AI対戦・点数付きの友だち対戦も対象 · 毎日0時（韓国時間）にリセット"))
                .font(.system(size: 11)).foregroundColor(UiColor.text.opacity(0.6))
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var checkinHeader: some View {
        HStack {
            Text(tr("📅 출석 체크", "📅 Daily check-in", zh: "📅 每日签到", es: "📅 Asistencia diaria", fr: "📅 Présence du jour",
                    de: "📅 Täglicher Check-in", pt: "📅 Presença diária", ru: "📅 Ежедневный вход", ja: "📅 出席チェック"))
                .font(.system(size: 17, weight: .black)).foregroundColor(UiColor.text)
            Spacer()
            Button { Task { await online.claimDaily() } } label: {
                Text(nextDay.map { d in
                    tr("받기 💰+\(OnlineService.checkinCoins[d - 1])", "Claim 💰+\(OnlineService.checkinCoins[d - 1])",
                       zh: "领取 💰+\(OnlineService.checkinCoins[d - 1])", es: "Recoger 💰+\(OnlineService.checkinCoins[d - 1])",
                       fr: "Prendre 💰+\(OnlineService.checkinCoins[d - 1])", de: "Holen 💰+\(OnlineService.checkinCoins[d - 1])",
                       pt: "Pegar 💰+\(OnlineService.checkinCoins[d - 1])", ru: "Забрать 💰+\(OnlineService.checkinCoins[d - 1])",
                       ja: "受け取る 💰+\(OnlineService.checkinCoins[d - 1])")
                } ?? tr("✅ 내일 또!", "✅ See you tomorrow!", zh: "✅ 明天见！", es: "✅ ¡Hasta mañana!", fr: "✅ À demain !",
                         de: "✅ Bis morgen!", pt: "✅ Até amanhã!", ru: "✅ До завтра!", ja: "✅ また明日！"))
                    .font(.system(size: 13, weight: .bold)).foregroundColor(.white)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Capsule().fill(UiColor.primary.opacity(nextDay == nil ? 0.4 : 1)))
            }
            .disabled(nextDay == nil || online.busy)
        }
    }

    private func dayBox(_ i: Int) -> some View {
        let d = i + 1
        let done = d <= streak
        let isNext = d == nextDay
        let bg: Color = done ? UiColor.primary.opacity(0.25) : isNext ? UiColor.gold.opacity(0.35) : UiColor.text.opacity(0.06)
        return VStack(spacing: 1) {
            Text(tr("\(d)일", "D\(d)", zh: "第\(d)天", ja: "\(d)日目")).font(.system(size: 10)).foregroundColor(UiColor.text.opacity(0.7)).lineLimit(1)
            Text(done ? "✅" : d == 7 ? "💖" : "💰").font(.system(size: 14))
            Text("\(OnlineService.checkinCoins[i])").font(.system(size: 10, weight: .bold)).foregroundColor(UiColor.text).lineLimit(1)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 5)
        .background(RoundedRectangle(cornerRadius: 10).fill(bg))
    }

    private func missionRow(_ m: Mission) -> some View {
        let claimed = profile.missionDay == today && profile.mClaimed.contains(m.id)
        let ready = m.now >= m.goal && !claimed
        return HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text(m.label).font(.system(size: 14, weight: .bold)).foregroundColor(UiColor.text)
                Text("\(min(m.now, m.goal))/\(m.goal) · 💰 +\(m.coins)").font(.system(size: 12)).foregroundColor(UiColor.text.opacity(0.65))
            }
            Spacer()
            if claimed {
                Text("✅").font(.system(size: 18))
            } else {
                Button { Task { await online.claimMission(m.id, label: m.label) } } label: {
                    Text(tr("받기", "Claim", zh: "领取", es: "Recoger", fr: "Prendre", de: "Holen", pt: "Pegar", ru: "Забрать", ja: "受け取る"))
                        .font(.system(size: 13, weight: .bold)).foregroundColor(.white)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Capsule().fill(UiColor.accent.opacity(ready ? 1 : 0.35)))
                }
                .disabled(!ready || online.busy)
            }
        }
        .padding(.top, 4)
    }
}
