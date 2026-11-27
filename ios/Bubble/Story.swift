import BubbleCore
import SwiftUI

/*
 * 📜 세계관: 거대한 고양이 왕국에 살던 햄스터들이 "우리 땅!"을 외치며 독립을 선언했고,
 * 그래서 고양이와 햄스터의 방울 대결이 시작됐다. 점수를 모아 🌈 땅을 사면 독립 엔딩.
 * (안드로이드 Story.kt와 같은 내용)
 */

// MARK: - 🐹 나는 늘 햄스터

/// 화면에 보이는 진영. 대전 화면에서는 "나 = 햄스터, 상대 = 고양이".
/// 규칙(고양이 진영이 먼저 둠)과 서버 데이터는 그대로이고 그림·이름만 바꿔 보여줌.
enum SideLook {
    static var mySide: Side? = nil

    /// 대전 화면이 그려질 때 내 진영을 알려줌
    @discardableResult
    static func set(_ side: Side) -> Bool {
        mySide = side
        return true
    }

    /// [side]가 화면에서 어떤 동물로 보이는지
    static func of(_ side: Side) -> Side {
        guard let me = mySide else { return side }
        return side == me ? .hamster : .cat
    }
}

extension Optional where Wrapped == Animal {
    /// 보이는 모습: ⏱ 시계 햄스터(소모품)는 모습을 바꾸지 않고 시작 때 알림만 → 기본 햄스터
    var shownSkin: Animal? {
        guard let a = self, !a.consumable else { return nil }
        return a
    }
}

// MARK: - 📜 프롤로그

/// 프롤로그를 띄울지 (처음 실행할 때 한 번, 로비 "📜 이야기"로 다시)
final class StoryState: ObservableObject {
    static let shared = StoryState()
    private static let key = "prologueSeen"
    @Published var showPrologue = !UserDefaults.standard.bool(forKey: StoryState.key)

    func finish() {
        showPrologue = false
        UserDefaults.standard.set(true, forKey: Self.key)
    }
}

private func prologueText(_ page: Int) -> String {
    switch page {
    case 0:
        return tr("구름 위 거대한 고양이 왕국.\n작은 햄스터들도 그곳에서\n고양이들과 함께 살았어요.",
                  "High above the clouds lies the great Cat Kingdom.\nLittle hamsters lived there too, alongside the cats.",
                  zh: "云朵之上，有一个巨大的猫咪王国。\n小仓鼠们也和猫咪们一起住在那里。",
                  ja: "雲の上の大きなネコ王国。\n小さなハムスターたちも、ネコといっしょに暮らしていました。")
    case 1:
        return tr("어느 날, 햄스터들이 외쳤어요.\n\"우리도 우리 땅을 갖고 싶다! 독립이다!\"",
                  "One day, the hamsters cried out:\n\"We want a land of our own! Independence!\"",
                  zh: "有一天，仓鼠们大喊：\n“我们也想要自己的土地！我们要独立！”",
                  ja: "ある日、ハムスターたちがさけびました。\n「ぼくらも自分の土地がほしい！独立だ！」")
    case 2:
        return tr("깜짝 놀란 고양이 왕국!\n그렇게 고양이와 햄스터의\n💥 방울 대결이 시작됐어요.",
                  "The Cat Kingdom was shocked!\nAnd so the 💥 bubble battle between cats and hamsters began.",
                  zh: "猫咪王国大吃一惊！\n猫咪和仓鼠的 💥 泡泡对决就此开始。",
                  ja: "びっくりしたネコ王国！\nこうしてネコとハムスターの💥バブル対決がはじまりました。")
    default:
        return tr("꼬마 햄스터야, 고양이들을 이기고\n점수를 모아 🌈 우리 땅을 되찾자!",
                  "Little hamster, beat the cats, collect points,\nand win back 🌈 our own land!",
                  zh: "小仓鼠，打败猫咪、收集分数，\n夺回 🌈 我们的土地吧！",
                  ja: "ちびハムスター、ネコたちに勝ってスコアを集めて、\n🌈 ぼくらの土地を取りもどそう！")
    }
}

/// 꿈꾸는 밤하늘 배경 (파스텔 보라 → 분홍) + 반짝이는 별 + 아래 구름
private struct DreamSky<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: 0x8F86D9), Color(hex: 0xC6A8E8), Color(hex: 0xF6C9DE)], startPoint: .top, endPoint: .bottom)
            TimelineView(.animation) { tl in
                let tw = tl.date.timeIntervalSinceReferenceDate / 2.4
                Canvas { ctx, size in
                    for i in 0..<40 {
                        let x = CGFloat((i * 73 + 17) % 101) / 101 * size.width
                        let y = CGFloat((i * 37 + 11) % 97) / 97 * size.height * 0.75
                        let a = 0.35 + 0.65 * (sin((tw + Double(i) * 0.13) * 2 * .pi) + 1) / 2
                        let r: CGFloat = i % 5 == 0 ? 2 : 1.1
                        ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(a)))
                    }
                    for i in 0...6 {
                        let c = CGPoint(x: size.width * CGFloat(i) / 6, y: size.height * 1.02)
                        let r = size.width * 0.16
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.55)))
                    }
                }
            }
            content
        }
        .ignoresSafeArea()
    }
}

/// 살짝 둥실둥실
private struct Floating: ViewModifier {
    var amp: CGFloat = 8
    var period: Double = 3.6
    var flip = false
    func body(content: Content) -> some View {
        TimelineView(.animation) { tl in
            let y = sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / period) * amp
            content.offset(y: flip ? -y : y)
        }
    }
}

private extension View {
    func floating(_ amp: CGFloat = 8, flip: Bool = false) -> some View { modifier(Floating(amp: amp, flip: flip)) }
}

private let prologuePages = 4

/// 📜 프롤로그 4컷. 화면을 누르면 다음, 건너뛰기 가능.
struct PrologueView: View {
    var onDone: () -> Void
    @State private var page = 0

    private func next() {
        if page < prologuePages - 1 { withAnimation(.easeInOut(duration: 0.4)) { page += 1 } } else { onDone() }
    }

    var body: some View {
        DreamSky {
            VStack(spacing: 16) {
                HStack {
                    HStack(spacing: 6) {
                        ForEach(0..<prologuePages, id: \.self) { i in
                            Circle().fill(Color.white.opacity(i <= page ? 1 : 0.45)).frame(width: i == page ? 12 : 8, height: i == page ? 12 : 8)
                        }
                    }
                    Spacer()
                    Button(tr("건너뛰기 ▶▶", "Skip ▶▶", zh: "跳过 ▶▶", ja: "スキップ ▶▶"), action: onDone)
                        .foregroundColor(.white).font(.system(size: 15, weight: .bold))
                }
                Spacer(minLength: 0)
                PrologueArt(page: page)
                    .frame(height: 320)
                    .id(page)
                    .transition(.opacity)
                Text(prologueText(page))
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(Color(hex: 0x4A3466))
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 18).padding(.vertical, 16)
                    .background(Color.white.opacity(0.82))
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .id("t\(page)")
                    .transition(.opacity)
                Spacer(minLength: 0)
                Button(action: next) {
                    Text(page < prologuePages - 1 ? tr("다음 ▶", "Next ▶", zh: "下一页 ▶", ja: "つぎ ▶")
                                                 : tr("🐹 독립하러 가자!", "🐹 Let's go!", zh: "🐹 出发吧！", ja: "🐹 独立しにいこう！"))
                        .font(.system(size: 18, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(UiColor.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 56)
            .padding(.bottom, 34)
            .contentShape(Rectangle())
            .onTapGesture(perform: next)
        }
    }
}

private struct PrologueArt: View {
    let page: Int
    var body: some View {
        switch page {
        case 0:
            // 1컷: 왕관 쓴 커다란 고양이와 그 앞의 작은 햄스터들
            ZStack {
                VStack(spacing: -8) {
                    Text("👑").font(.system(size: 40))
                    Image("art_cat_idle").resizable().scaledToFit().frame(width: 240)
                }
                .floating()
                HStack(spacing: -14) {
                    Image("art_ham_idle").resizable().scaledToFit().frame(width: 72).floating(5, flip: true)
                    Image("art_ham_idle").resizable().scaledToFit().frame(width: 82).floating(5)
                    Image("art_ham_idle").resizable().scaledToFit().frame(width: 72).floating(5, flip: true)
                }
                .frame(maxHeight: .infinity, alignment: .bottom)
            }
        case 1:
            // 2컷: 햄스터들의 외침 + 무지개 깃발
            ZStack(alignment: .top) {
                HStack(alignment: .bottom, spacing: -30) {
                    Image("art_ham_happy").resizable().scaledToFit().frame(width: 120).floating(6, flip: true)
                    Image("art_ham_surprised").resizable().scaledToFit().frame(width: 170).floating()
                    Image("art_ham_happy").resizable().scaledToFit().frame(width: 120).floating(6, flip: true)
                }
                .frame(maxHeight: .infinity)
                RainbowFlag().frame(width: 90, height: 90).offset(x: 70, y: 6)
            }
        case 2:
            // 3컷: 고양이 vs 햄스터 💥
            ZStack {
                HStack(spacing: -20) {
                    Image("art_cat_surprised").resizable().scaledToFit().frame(width: 160).floating()
                    Image("art_ham_surprised").resizable().scaledToFit().frame(width: 160).floating(8, flip: true)
                }
                TimelineView(.animation) { tl in
                    let s = 1.02 + 0.17 * sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi)
                    Text("💥").font(.system(size: 56)).scaleEffect(s)
                }
            }
        default:
            // 4컷: 플레이어 햄스터 + 깃발
            ZStack(alignment: .topTrailing) {
                Image("art_ham_happy").resizable().scaledToFit().frame(width: 250).floating()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                RainbowFlag().frame(width: 84, height: 84).offset(x: -24, y: 10)
            }
        }
    }
}

// MARK: - 🌈 햄스터 나라 깃발

/// 펄럭이는 햄스터 나라 깃발 (파스텔 무지개 줄무늬)
struct RainbowFlag: View {
    private static let stripes: [Color] = [
        Color(hex: 0xFFB3C7), Color(hex: 0xFFD1A6), Color(hex: 0xFFF0A8),
        Color(hex: 0xBDEBC1), Color(hex: 0xB5D8FF), Color(hex: 0xD6C2FF),
    ]

    var body: some View {
        TimelineView(.animation) { tl in
            let phase = tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.1
            Canvas { ctx, size in
                let w = size.width, h = size.height
                let poleX = w * 0.18
                // 깃대 + 꼭지
                ctx.fill(Path(roundedRect: CGRect(x: poleX - w * 0.03, y: h * 0.06, width: w * 0.06, height: h * 0.92), cornerRadius: 3),
                         with: .color(Color(hex: 0x9C6B3E)))
                ctx.fill(Path(ellipseIn: CGRect(x: poleX - w * 0.06, y: 0, width: w * 0.12, height: w * 0.12)), with: .color(Color(hex: 0xFFD34D)))
                // 물결치는 천
                let top = h * 0.1, fh = h * 0.42, fw = w * 0.74
                let steps = 16
                func wave(_ i: Int) -> CGFloat { CGFloat(sin(phase + Double(i) * 0.55)) * h * 0.035 * CGFloat(i) / CGFloat(steps) }
                var cloth = Path()
                cloth.move(to: CGPoint(x: poleX, y: top))
                for i in 1...steps { cloth.addLine(to: CGPoint(x: poleX + fw * CGFloat(i) / CGFloat(steps), y: top + wave(i))) }
                for i in stride(from: steps, through: 0, by: -1) { cloth.addLine(to: CGPoint(x: poleX + fw * CGFloat(i) / CGFloat(steps), y: top + fh + wave(i))) }
                cloth.closeSubpath()
                var inner = ctx
                inner.clip(to: cloth)
                let sh = (fh + h * 0.08) / CGFloat(Self.stripes.count)
                for (i, c) in Self.stripes.enumerated() {
                    inner.fill(Path(CGRect(x: poleX, y: top - h * 0.04 + CGFloat(i) * sh, width: fw + 2, height: sh + 1)), with: .color(c))
                }
                inner.fill(Path(CGRect(x: poleX, y: top - h * 0.04, width: fw, height: fh * 0.5)),
                           with: .linearGradient(Gradient(colors: [.white.opacity(0.35), .clear]), startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: top + fh * 0.5)))
                ctx.stroke(cloth, with: .color(.white.opacity(0.6)), lineWidth: w * 0.012)
            }
        }
    }
}

// MARK: - 🌈 독립 엔딩

/// 땅을 사면 나오는 독립 엔딩 컷. 누르면 닫힘.
struct LandEnding: View {
    var name: String?
    var onDismiss: () -> Void
    @State private var planted = false
    @State private var text1 = false
    @State private var text2 = false

    var body: some View {
        DreamSky {
            VStack(spacing: 14) {
                Text("🎉 THE END 🎉").font(.system(size: 22, weight: .black)).foregroundColor(.white).opacity(text2 ? 1 : 0)
                ZStack(alignment: .bottom) {
                    // 작은 땅 언덕 + 꽃
                    ZStack {
                        Ellipse().fill(Color(hex: 0x9BD58A)).frame(width: 320, height: 110).offset(y: 30)
                        Ellipse().fill(Color(hex: 0xB9E7A5)).frame(width: 270, height: 52).offset(y: 6)
                    }
                    .frame(height: 90)
                    RainbowFlag().frame(width: 150, height: 150)
                        .offset(x: 80, y: planted ? -100 : -420)
                    Image("art_ham_happy").resizable().scaledToFit().frame(width: 220)
                        .floating(5)
                        .offset(x: -40, y: -26)
                }
                .frame(width: 320, height: 320)
                VStack(spacing: 8) {
                    Text(tr("드디어… 우리 땅이다!", "At last… our very own land!", zh: "终于……这是我们的土地！", ja: "ついに…ぼくらの土地だ！"))
                        .font(.system(size: 24, weight: .black)).foregroundColor(Color(hex: 0x4A3466)).opacity(text1 ? 1 : 0)
                    Text(tr("\(name ?? "꼬마 햄스터")의 활약으로\n햄스터들이 고양이 왕국에서 독립했어요! 🌈\n칭호 '독립 햄스터'를 받았고,\n랭킹에 깃발이 휘날려요.",
                            "Thanks to \(name ?? "you"), the hamsters are free from the Cat Kingdom! 🌈\nYou earned the title 'Free Hamster', and your flag now flies on the ranking.",
                            zh: "多亏了\(name ?? "你")，仓鼠们从猫咪王国独立了！🌈\n你获得了称号“独立仓鼠”，排行榜上也会飘扬你的旗帜。",
                            ja: "\(name ?? "きみ")の活躍で、ハムスターたちはネコ王国から独立しました！🌈\n称号「独立ハムスター」をゲット、ランキングに旗がはためきます。"))
                        .font(.system(size: 15)).foregroundColor(Color(hex: 0x4A3466)).multilineTextAlignment(.center).opacity(text2 ? 1 : 0)
                    Text(tr("…그런데 고양이들이 가만히 있을까? 🐱", "…but will the cats just let it go? 🐱", zh: "……不过猫咪们会就此罢休吗？🐱", ja: "…でも、ネコたちがだまっているかな？🐱"))
                        .font(.system(size: 13)).foregroundColor(Color(hex: 0x8A6FA8)).opacity(text2 ? 1 : 0)
                }
                .frame(maxWidth: .infinity)
                .padding(18)
                .background(Color.white.opacity(0.85))
                .clipShape(RoundedRectangle(cornerRadius: 24))
                Text(tr("화면을 누르면 닫혀요", "Tap to close", zh: "点击关闭", ja: "タップでとじる")).font(.system(size: 13)).foregroundColor(.white.opacity(0.85))
            }
            .padding(20)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onDismiss)
        .onAppear {
            withAnimation(.spring(response: 0.55, dampingFraction: 0.45)) { planted = true }
            withAnimation(.easeIn(duration: 0.6).delay(0.6)) { text1 = true }
            withAnimation(.easeIn(duration: 0.6).delay(1.2)) { text2 = true }
        }
    }
}

/// "🌈 독립 햄스터" 칭호 배지
struct IndependentBadge: View {
    var body: some View {
        Text(tr("🌈 독립 햄스터", "🌈 Free Hamster", zh: "🌈 独立仓鼠", ja: "🌈 独立ハムスター"))
            .font(.system(size: 13, weight: .black))
            .foregroundColor(.white)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(LinearGradient(colors: [Color(hex: 0xFF7FAE), Color(hex: 0xB58CFF)], startPoint: .leading, endPoint: .trailing))
            .clipShape(Capsule())
    }
}

/// 아직 독립 전 (🌈 땅을 사지 않음) 표시
struct NotIndependentBadge: View {
    var body: some View {
        Text(tr("🏰 아직 고양이 왕국", "🏰 Not free yet", zh: "🏰 尚未独立", ja: "🏰 まだネコ王国"))
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(UiColor.text.opacity(0.6))
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(UiColor.text.opacity(0.08))
            .clipShape(Capsule())
    }
}

/// 상점의 땅 그림: 작은 언덕 위 무지개 깃발 + 햄스터
struct LandArt: View {
    var body: some View {
        ZStack(alignment: .bottom) {
            Ellipse().fill(Color(hex: 0x9BD58A)).frame(height: 34).offset(y: 10)
            RainbowFlag().frame(width: 64, height: 64).offset(x: 8, y: -24)
            Image("sprite_ham_idle").resizable().scaledToFit().frame(width: 40).offset(x: -24, y: -6)
        }
        .frame(width: 88, height: 88)
    }
}

// MARK: - ⏱ 시계 햄스터 알림

/// 게임 시작 때 위에서 내려오는 알림 카드 (내 것은 노랑, 상대 것은 분홍)
struct TimeHamsterAlert: View {
    let animal: Animal
    let mine: Bool
    var body: some View {
        HStack(spacing: 10) {
            Critter(side: .hamster, skin: animal, happy: mine).frame(width: 56, height: 56)
            Text(mine
                 ? tr("⏰ \(animal.label) 사용!\n상대 턴이 \(animal.opponentTurnSec)초로 줄어요", "⏰ \(animal.label) used!\nYour rival's turn is \(animal.opponentTurnSec)s",
                      zh: "⏰ 使用了\(animal.label)！\n对手回合缩短为 \(animal.opponentTurnSec) 秒", ja: "⏰ \(animal.label)を使った！\n相手のターンは\(animal.opponentTurnSec)秒")
                 : tr("⏰ 상대가 \(animal.label)을(를) 썼어요!\n내 턴이 \(animal.opponentTurnSec)초로 줄어요", "⏰ Your rival used \(animal.label)!\nYour turn is \(animal.opponentTurnSec)s",
                      zh: "⏰ 对手用了\(animal.label)！\n你的回合缩短为 \(animal.opponentTurnSec) 秒", ja: "⏰ 相手が\(animal.label)を使った！\n自分のターンは\(animal.opponentTurnSec)秒"))
                .font(.system(size: 16, weight: .black))
                .foregroundColor(UiColor.text)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(mine ? Color(hex: 0xFFF3C4) : Color(hex: 0xFFE0E6))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(mine ? Color(hex: 0xFFC94D) : Color(hex: 0xFF8FA3), lineWidth: 2))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, 16)
    }
}

/// ⏱ 시계 햄스터 알림을 차례로 보여 주는 겹 (게임 화면 위). 처음 한 번만.
struct TimeHamsterAlerts: View {
    let mine: Animal?
    let theirs: Animal?
    /// VS 소개가 있으면 그다음에
    var delay: Double = 0
    @State private var current: (Animal, Bool)? = nil

    var body: some View {
        VStack {
            if let c = current {
                TimeHamsterAlert(animal: c.0, mine: c.1)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
            Spacer()
        }
        .padding(.top, 56)
        .allowsHitTesting(false)
        .task {
            let list = [mine.map { ($0, true) }, theirs.map { ($0, false) }].compactMap { $0 }.filter { $0.0.consumable }
            guard !list.isEmpty else { return }
            if delay > 0 { try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000)) }
            for item in list {
                withAnimation(.spring()) { current = item }
                try? await Task.sleep(nanoseconds: 2_600_000_000)
                withAnimation { current = nil }
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
        }
    }
}
