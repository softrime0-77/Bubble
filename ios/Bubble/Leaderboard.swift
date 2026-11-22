import BubbleCore
import SwiftUI

// 안드로이드 ui/Leaderboard.kt와 같은 랭킹 줄 이펙트와 프로필 카드.
// 계산은 모두 CGFloat로 (Double과 섞으면 컴파일러가 타입을 못 정함)
private let lbTau: CGFloat = .pi * 2
private func lcos(_ x: CGFloat) -> CGFloat { CGFloat(cos(Double(x))) }
private func lsin(_ x: CGFloat) -> CGFloat { CGFloat(sin(Double(x))) }

/// 랭킹 한 사람 (TOP 100 목록과 "내 주변 순위" 둘 다)
struct RankedPlayer: Identifiable, Equatable {
    var position: Int
    var uid: String
    var name: String
    var country: String?
    var score: Int
    var wins: Int?
    var losses: Int?
    /// 지금 보이는 캐릭터 (nil이면 기본 고양이)
    var look: Animal?
    var id: String { uid }
}

extension Profile {
    /// 다른 사람에게 보이는 캐릭터 (서버 lookSkin과 같은 규칙 + 남은 동물)
    var look: Animal? {
        guard let a = equipped else { return nil }
        if a.free { return a }
        if a.cosmetic && skins.contains(a) { return a }
        if a.rankOnly && score >= a.rankMin { return a }
        if a.consumable && animalCount(a) > 0 { return a }
        return nil
    }

    func ranked(_ position: Int) -> RankedPlayer {
        RankedPlayer(position: position, uid: uid, name: name, country: country, score: score, wins: wins, losses: losses, look: look)
    }
}

extension RankRow {
    var ranked: RankedPlayer {
        RankedPlayer(position: rank, uid: uid, name: name, country: country, score: score, wins: wins, losses: losses, look: look)
    }
}

/// 1위 10 → 10위 1 (빛 줄기·반짝이·테두리), 11~30위 0 (반짝이만), 그 밖은 -1 (효과 없음)
func rankFxLevel(_ position: Int) -> Int {
    if (1...10).contains(position) { return 11 - position }
    if (11...30).contains(position) { return 0 }
    return -1
}

private func medal(_ p: Int) -> String { p == 1 ? "🥇" : p == 2 ? "🥈" : p == 3 ? "🥉" : "\(p)" }

private func hsv(_ h: CGFloat, _ s: CGFloat, _ v: CGFloat) -> Color {
    Color(hue: Double(h.truncatingRemainder(dividingBy: 1)), saturation: Double(s), brightness: Double(v))
}

/// 순위별 줄 배경: 1위 금 · 2위 은 · 3위 동 · 4~10위 분홍 보라
private func rowColors(_ position: Int, _ hue: CGFloat) -> [Color] {
    switch position {
    case 1: return [Color(hex: 0xFFF6C2), Color(hex: 0xFFD54F), hsv((40 + hue * 20) / 360, 0.75, 1), Color(hex: 0xFFF0A8)]
    case 2: return [Color.white, Color(hex: 0xDCE3EA), Color(hex: 0xB8C4CF), Color(hex: 0xF1F4F8)]
    case 3: return [Color(hex: 0xFFEBD9), Color(hex: 0xFFC58F), Color(hex: 0xE59A5C), Color(hex: 0xFFE0C4)]
    default: return [Color(hex: 0xFFE6F2), Color(hex: 0xF3E6FF), Color(hex: 0xFFE6F2)]
    }
}

private func rainbow(_ hue: CGFloat) -> [Color] {
    (0..<7).map { i in hsv(hue + CGFloat(i) / 6, 0.55, 1) }
}

/// 네 갈래 반짝이
private func twinkleStar(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color) {
    var p = Path()
    let k = r * 1.6
    p.move(to: CGPoint(x: c.x, y: c.y - k))
    p.addQuadCurve(to: CGPoint(x: c.x + k, y: c.y), control: c)
    p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + k), control: c)
    p.addQuadCurve(to: CGPoint(x: c.x - k, y: c.y), control: c)
    p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - k), control: c)
    ctx.fill(p, with: .color(color))
}

/// 고정된 난수 (순위마다 같은 자리)
private func seeds(_ n: Int, _ seed: Int) -> [(CGFloat, CGFloat, CGFloat)] {
    var s = UInt64(seed * 2654435761 + 12345)
    func next() -> CGFloat {
        s = s &* 6364136223846793005 &+ 1442695040888963407
        return CGFloat((s >> 33) % 10_000) / 10_000
    }
    return (0..<n).map { _ in (next(), next(), next()) }
}

// MARK: - 랭킹 한 줄

struct RankingRowView: View {
    let p: RankedPlayer
    let me: Bool
    let onTap: () -> Void

    private var level: Int { rankFxLevel(p.position) }
    private var first: Bool { p.position == 1 }

    var body: some View {
        Button(action: onTap) { content }
            .buttonStyle(.plain)
            .padding(.vertical, first ? 5 : level >= 1 ? 2 : 0)
    }

    private var content: some View {
        HStack(spacing: 6) {
            Text(medal(p.position))
                .font(.system(size: p.position == 1 ? 26 : p.position <= 3 ? 21 : 15, weight: .bold))
                .lineLimit(1).minimumScaleFactor(0.7).frame(width: 40, alignment: .leading)
            Text(flag(p.country)).font(.system(size: 16)).frame(width: 24)
            Text(p.name + (me ? t(" (나)") : ""))
                .font(.system(size: first ? 17 : 15, weight: level >= 1 ? .black : .regular))
                .foregroundColor(UiColor.text).lineLimit(1)
            Spacer(minLength: 4)
            RankBadge(score: p.score)
            Text(pts(p.score)).font(.system(size: 15, weight: .bold)).foregroundColor(UiColor.primaryDark)
        }
        .padding(.horizontal, 8).padding(.vertical, first ? 12 : level >= 1 ? 7 : 5)
        .background(RoundedRectangle(cornerRadius: level >= 1 ? 14 : 10).fill(me ? UiColor.primary.opacity(0.18) : .clear))
        .background(level >= 0 ? AnyView(RowFxView(position: p.position, level: level)) : AnyView(EmptyView()))
        .overlay(level >= 0 ? AnyView(RowSparkles(position: p.position, level: level).allowsHitTesting(false)) : AnyView(EmptyView()))
        .contentShape(Rectangle())
    }
}

/// 줄 뒤: 배경색 · 빛 줄기 · 테두리 · (1~3위) 뒤로 퍼지는 빛
private struct RowFxView: View {
    let position: Int
    let level: Int

    var body: some View {
        TimelineView(.animation) { tl in
            let time = CGFloat(tl.date.timeIntervalSinceReferenceDate)
            Canvas { ctx, size in draw(&ctx, size, time) }
        }
        .allowsHitTesting(false)
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat) {
        guard level >= 1 else { return }
        let lv = CGFloat(level)
        let hue = (time / 3).truncatingRemainder(dividingBy: 1)
        let rect = CGRect(origin: .zero, size: size)
        let shape = Path(roundedRect: rect, cornerRadius: 14)
        // 1~3위: 뒤에서 퍼지는 빛
        if level >= 8 {
            let glow = 0.35 + 0.65 * (0.5 + 0.5 * lsin(time * 3.7))
            let c: Color = level == 10 ? Color(hex: 0xFFC107) : level == 9 ? Color(hex: 0xB0BEC5) : Color(hex: 0xFF9E57)
            let grow: CGFloat = level == 10 ? 6 + 3 * glow : 3
            let big = Path(roundedRect: rect.insetBy(dx: -grow, dy: -grow), cornerRadius: 18)
            ctx.fill(big, with: .color(c.opacity(Double((0.25 + lv * 0.03) * glow))))
        }
        // 배경 (4~10위는 아래로 갈수록 옅게)
        let a: Double = position <= 3 ? 1 : Double(0.35 + lv * 0.08)
        let cols = rowColors(position, hue).map { $0.opacity(a) }
        ctx.fill(shape, with: .linearGradient(Gradient(colors: cols), startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)))
        // 빛 줄기 (1위는 두 줄)
        let period = 1.3 + CGFloat(10 - level) * 0.17
        let shine = (time / period).truncatingRemainder(dividingBy: 1) * 1.8 - 0.4
        var bands = [shine]
        if level == 10 { bands.append((shine + 0.9 + 0.4).truncatingRemainder(dividingBy: 1.8) - 0.4) }
        var clip = ctx
        clip.clip(to: shape)
        for b in bands {
            let x = b * size.width
            let g = Gradient(colors: [.clear, Color.white.opacity(Double(0.3 + lv * 0.05)), .clear])
            clip.fill(Path(rect), with: .linearGradient(g, startPoint: CGPoint(x: x - size.height, y: 0), endPoint: CGPoint(x: x + size.height, y: size.height)))
        }
        // 테두리: 1위 무지개 · 2~3위 금속 · 4~7위 옅게
        if level >= 4 {
            let w: CGFloat = level == 10 ? 3 : level >= 8 ? 2 : 1.2
            let inner = Path(roundedRect: rect.insetBy(dx: w / 2, dy: w / 2), cornerRadius: 14)
            let shading: GraphicsContext.Shading = level == 10
                ? .conicGradient(Gradient(colors: rainbow(hue)), center: CGPoint(x: size.width / 2, y: size.height / 2))
                : .linearGradient(Gradient(colors: Array(rowColors(position, hue).dropFirst())), startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height))
            var b = ctx
            b.opacity = level >= 8 ? 1 : Double(0.5 + 0.1 * CGFloat(level - 4))
            b.stroke(inner, with: shading, lineWidth: w)
        }
    }
}

/// 줄 앞 반짝이: 1위 22개 → 10위 4개, 11~30위 3개
private struct RowSparkles: View {
    let position: Int
    let level: Int

    var body: some View {
        let count = level >= 1 ? 2 + level * 2 : 3
        let ss = seeds(count, position * 97 + 13)
        return TimelineView(.animation) { tl in
            let time = CGFloat(tl.date.timeIntervalSinceReferenceDate)
            Canvas { ctx, size in draw(&ctx, size, time, ss) }
        }
    }

    private func color(_ i: Int, _ hue: CGFloat) -> Color {
        if level == 10 { return [Color(hex: 0xFFF59D), .white, Color(hex: 0xFFD54F), hsv(hue + CGFloat(i) * 50 / 360, 0.45, 1)][i % 4] }
        if level >= 8 { return i % 2 == 0 ? .white : Color(hex: 0xFFF3C4) }
        if level >= 1 { return i % 2 == 0 ? .white : Color(hex: 0xFFC1DE) }
        return i % 2 == 0 ? Color(hex: 0xFFE27A) : Color(hex: 0xFFB3D6)
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat, _ ss: [(CGFloat, CGFloat, CGFloat)]) {
        let tw = time / 1.5
        let hue = (time / 3).truncatingRemainder(dividingBy: 1)
        for (i, s) in ss.enumerated() {
            let speed = 1 + CGFloat(i % 3) * 0.2
            let p = (tw * speed + s.2).truncatingRemainder(dividingBy: 1)
            let a = lsin(p * .pi)
            let pos = CGPoint(x: (0.12 + s.0 * 0.86) * size.width, y: (s.1 * 1.2 - 0.1) * size.height)
            let base: CGFloat = level >= 1 ? 2.5 + CGFloat(level) * 0.45 : 2.6
            let maxA: CGFloat = level >= 1 ? 0.95 : 0.8
            twinkleStar(&ctx, pos, base * (0.4 + a * 0.8), color(i, hue).opacity(Double(a * maxA)))
        }
        // 1위: 왕관 주위로 도는 별빛
        if level == 10 {
            for k in 0..<3 {
                let ang = (tw + CGFloat(k) / 3) * lbTau
                let pos = CGPoint(x: 26 + lcos(ang) * 22, y: size.height / 2 + lsin(ang) * 14)
                twinkleStar(&ctx, pos, 3.5, Color(hex: 0xFFF8E1).opacity(0.9))
            }
        }
    }
}

// MARK: - 프로필 카드

/// 랭킹 줄을 누르면 뜨는 작은 카드: 캐릭터 · 전체 닉네임 · 나라 · 계급 · 점수 · 전적
struct PlayerCardView: View {
    let p: RankedPlayer
    let me: Bool
    let onClose: () -> Void
    @State private var pop: CGFloat = 0

    private var level: Int { rankFxLevel(p.position) }

    var body: some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea().onTapGesture(perform: onClose)
            TimelineView(.animation) { tl in
                let time = CGFloat(tl.date.timeIntervalSinceReferenceDate)
                ZStack {
                    if level >= 1 {
                        Canvas { ctx, size in drawRays(&ctx, size, time) }.frame(width: 420, height: 420)
                    }
                    if level >= 0 {
                        Canvas { ctx, size in drawStars(&ctx, size, time) }.frame(width: 360, height: 360)
                    }
                    cardBody(time)
                }
            }
            .scaleEffect(0.7 + 0.3 * pop)
            .opacity(Double(min(1, pop)))
        }
        .onAppear { withAnimation(.spring(response: 0.45, dampingFraction: 0.6)) { pop = 1 } }
    }

    private func drawRays(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat) {
        let rays = 6 + level
        let c: Color = p.position == 1 ? Color(hex: 0xFFD54F) : p.position == 2 ? Color(hex: 0xE3EAF0) : p.position == 3 ? Color(hex: 0xFFB27A) : Color(hex: 0xFFB3D6)
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let r = min(size.width, size.height) / 2
        let spin = time / 14 * lbTau
        let g = Gradient(colors: [c.opacity(Double(0.25 + CGFloat(level) * 0.04)), .clear])
        for i in 0..<rays {
            let a0 = CGFloat(i) / CGFloat(rays) * lbTau + spin
            var path = Path()
            path.move(to: center)
            path.addLine(to: CGPoint(x: center.x + lcos(a0 - 0.11) * r, y: center.y + lsin(a0 - 0.11) * r))
            path.addLine(to: CGPoint(x: center.x + lcos(a0 + 0.11) * r, y: center.y + lsin(a0 + 0.11) * r))
            path.closeSubpath()
            ctx.fill(path, with: .radialGradient(g, center: center, startRadius: 0, endRadius: r))
        }
    }

    private func drawStars(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat) {
        let n = level >= 1 ? 10 + level * 4 : 8
        let ss = seeds(n, p.position * 7 + 3)
        let tw = time / 1.6
        let hue = (time / 3).truncatingRemainder(dividingBy: 1)
        for (i, s) in ss.enumerated() {
            let q = (tw + s.2).truncatingRemainder(dividingBy: 1)
            let a = lsin(q * .pi)
            let col: Color = p.position == 1 ? hsv(hue + CGFloat(i) * 40 / 360, 0.4, 1) : (i % 2 == 0 ? .white : Color(hex: 0xFFE27A))
            let r = (3 + CGFloat(max(level, 0)) * 0.4) * (0.4 + a)
            twinkleStar(&ctx, CGPoint(x: s.0 * size.width, y: s.1 * size.height), r, col.opacity(Double(a)))
        }
    }

    private var bg: [Color] {
        switch p.position {
        case 1: return [Color(hex: 0xFFFBE6), Color(hex: 0xFFE9A6)]
        case 2: return [Color.white, Color(hex: 0xE6ECF2)]
        case 3: return [Color(hex: 0xFFF6EE), Color(hex: 0xFFDDBF)]
        default: return [Color.white, Color(hex: 0xF6EEFF)]
        }
    }

    private func border(_ hue: CGFloat) -> AnyShapeStyle {
        if p.position == 1 { return AnyShapeStyle(AngularGradient(colors: rainbow(hue), center: .center)) }
        if level >= 1 { return AnyShapeStyle(LinearGradient(colors: Array(rowColors(p.position, hue).dropFirst()), startPoint: .topLeading, endPoint: .bottomTrailing)) }
        return AnyShapeStyle(LinearGradient(colors: [UiColor.primary.opacity(0.4), UiColor.accent.opacity(0.4)], startPoint: .leading, endPoint: .trailing))
    }

    private var positionText: String {
        let n = p.position
        let s = tr("\(n)위", "#\(n)", zh: "第\(n)名", ja: "\(n)位")
        return n <= 3 ? "\(medal(n)) \(s)" : s
    }

    private func cardBody(_ time: CGFloat) -> some View {
        let bob = lsin(time * 2.85)
        let hue = (time / 3).truncatingRemainder(dividingBy: 1)
        return VStack(spacing: 4) {
            Text(positionText)
                .font(.system(size: p.position == 1 ? 24 : 18, weight: .black))
                .foregroundColor(p.position == 1 ? Color(hex: 0xD68A00) : UiColor.primaryDark)
            ZStack(alignment: .top) {
                Critter(side: .cat, skin: p.look, happy: true)
                    .frame(width: p.position == 1 ? 120 : 100, height: p.position == 1 ? 120 : 100)
                    .offset(y: bob * 4)
                if p.position == 1 {
                    Text("👑").font(.system(size: 34)).offset(y: -20 + bob * 3).rotationEffect(.degrees(Double(bob * 8)))
                }
            }
            .padding(.vertical, 4)
            info
            closeButton.padding(.top, 10)
        }
        .padding(20)
        .frame(width: 280)
        .background(RoundedRectangle(cornerRadius: 28).fill(LinearGradient(colors: bg, startPoint: .top, endPoint: .bottom)))
        .overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(border(hue), lineWidth: p.position == 1 ? 3 : 2))
        .onTapGesture {}
    }

    private var info: some View {
        VStack(spacing: 4) {
            Text(p.name + (me ? t(" (나)") : ""))
                .font(.system(size: 20, weight: .black)).foregroundColor(UiColor.text)
                .multilineTextAlignment(.center).lineLimit(3).fixedSize(horizontal: false, vertical: true)
            Text("\(flag(p.country)) \(countryName(p.country).isEmpty ? tr("알 수 없음", "Unknown", zh: "未知", es: "Desconocido", fr: "Inconnu", de: "Unbekannt", pt: "Desconhecido", ru: "Неизвестно", ja: "不明") : countryName(p.country))")
                .font(.system(size: 15)).foregroundColor(UiColor.text.opacity(0.8))
            Text("🐾 " + (p.look?.label ?? tr("기본 고양이", "Basic Cat", zh: "基础猫咪", es: "Gato básico", fr: "Chat de base", de: "Standardkatze", pt: "Gato básico", ru: "Обычный котик", ja: "ふつうのネコ")))
                .font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.7))
            RankBadge(score: p.score, large: true).padding(.top, 8)
            Text(pts(p.score)).font(.system(size: 22, weight: .black)).foregroundColor(UiColor.primaryDark).padding(.top, 4)
            if let w = p.wins, let l = p.losses {
                Text(tr("\(w)승 \(l)패", "\(w)W \(l)L", zh: "\(w)胜 \(l)负", es: "\(w)V \(l)D", fr: "\(w)V \(l)D", de: "\(w)S \(l)N", pt: "\(w)V \(l)D", ru: "\(w)В \(l)П", ja: "\(w)勝 \(l)敗"))
                    .font(.system(size: 14)).foregroundColor(UiColor.text.opacity(0.7))
            }
        }
    }

    private var closeButton: some View {
        Button(action: onClose) {
            Text(tr("닫기", "Close", zh: "关闭", es: "Cerrar", fr: "Fermer", de: "Schließen", pt: "Fechar", ru: "Закрыть", ja: "閉じる"))
                .font(.system(size: 16, weight: .black)).foregroundColor(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 10)
                .background(Capsule().fill(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
        }
        .buttonStyle(.plain)
    }
}
