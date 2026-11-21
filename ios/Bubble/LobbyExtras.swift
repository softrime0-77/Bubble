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
        case .diamond: return tf("{0}점 이상", r.minScore)
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
            Text(t("친구가 내 코드를 입력하면 내가 +30점!")).font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.75))
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
                        case .diamond: color = Color(hue: Double(h), saturation: 0.55, brightness: 1)
                        case .gold: color = [UiColor.gold, Color(hex: 0xFFF4B0), .white][i % 3]
                        case .ruby: color = [Color(hex: 0xE0115F), Color(hex: 0xFF8FB0), .white][i % 3]
                        case .sapphire: color = [Color(hex: 0x1F6FEB), Color(hex: 0x9AD7FF), .white][i % 3]
                        default: color = [Color(hex: 0xF0A36B), .white][i % 2]
                        }
                        let r = (1 - ease * 0.7) * (6 + Double(s) * (4 + Double(rank.sparkle) * 3))
                        ctx.fill(sparklePath(pos, r), with: .color(color.opacity(1 - ease * 0.6)))
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
