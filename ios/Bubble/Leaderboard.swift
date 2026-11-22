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

/// 4~10위 테마색: 4~5위 보라 · 6~7위 하늘 · 8~10위 분홍
private func tierColor(_ position: Int) -> Color {
    switch position {
    case 1: return Color(hex: 0xFFC107)
    case 2: return Color(hex: 0xB0C4D6)
    case 3: return Color(hex: 0xFF9E57)
    case 4, 5: return Color(hex: 0xB388FF)
    case 6, 7: return Color(hex: 0x7FC8FF)
    default: return Color(hex: 0xFF8FC8)
    }
}

private func medalColor(_ position: Int) -> Color {
    switch position {
    case 4, 5: return Color(hex: 0x8A63D8)
    case 6, 7: return Color(hex: 0x4E95C8)
    case 8, 9, 10: return Color(hex: 0xC8609A)
    default: return UiColor.text
    }
}

struct RankingRowView: View {
    let p: RankedPlayer
    let me: Bool
    let onTap: () -> Void

    private var level: Int { rankFxLevel(p.position) }
    private var first: Bool { p.position == 1 }

    var body: some View {
        Group {
            if first {
                TimelineView(.animation) { tl in
                    row(CGFloat(tl.date.timeIntervalSinceReferenceDate))
                }
            } else {
                row(0)
            }
        }
        .padding(.vertical, first ? 12 : level >= 8 ? 6 : level >= 1 ? 3 : 0)
    }

    private func row(_ time: CGFloat) -> some View {
        let breathe = 0.5 + 0.5 * lsin(time * 2.25)
        return Button(action: onTap) { content(time, breathe) }
            .buttonStyle(.plain)
            .scaleEffect(first ? 1 + 0.018 * breathe : 1)
    }

    private func nameText(_ time: CGFloat) -> some View {
        let name = p.name + (me ? t(" (나)") : "")
        return Group {
            if first {
                // 금빛 그라데이션 글자가 흐르듯 움직임
                let x = (time / 2.2).truncatingRemainder(dividingBy: 1)
                Text(name).font(.system(size: 18, weight: .black)).lineLimit(1)
                    .foregroundColor(.clear)
                    .overlay(
                        LinearGradient(colors: [Color(hex: 0x6B3E00), Color(hex: 0xA86400), Color(hex: 0xD99A00), Color(hex: 0xA86400), Color(hex: 0x6B3E00)],
                                       startPoint: UnitPoint(x: x * 2 - 1, y: 0), endPoint: UnitPoint(x: x * 2 + 0.2, y: 0))
                            .mask(Text(name).font(.system(size: 18, weight: .black)).lineLimit(1))
                    )
                    .shadow(color: .white, radius: 3)
            } else {
                Text(name).font(.system(size: level >= 8 ? 16 : 15, weight: level >= 1 ? .black : .regular))
                    .foregroundColor(UiColor.text).lineLimit(1)
            }
        }
    }

    private func content(_ time: CGFloat, _ breathe: CGFloat) -> some View {
        HStack(spacing: 6) {
            ZStack {
                if first {
                    Text("👑").font(.system(size: 16)).offset(y: -22 - 3 * breathe).rotationEffect(.degrees(Double(-12 + 8 * breathe)))
                }
                Text(medal(p.position))
                    .font(.system(size: p.position == 1 ? 30 : p.position <= 3 ? 24 : level >= 1 ? 18 : 15, weight: .black))
                    .foregroundColor(medalColor(p.position))
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(width: 44)
            Text(flag(p.country)).font(.system(size: first ? 18 : 16)).frame(width: 24)
            nameText(time)
            Spacer(minLength: 4)
            RankBadge(score: p.score)
            Text(pts(p.score)).font(.system(size: first ? 16 : 15, weight: .black))
                .foregroundColor(first ? Color(hex: 0xC77700) : UiColor.primaryDark)
        }
        .padding(.horizontal, 8).padding(.vertical, first ? 16 : level >= 8 ? 10 : level >= 1 ? 8 : 5)
        .background(RoundedRectangle(cornerRadius: level >= 1 ? 14 : 10).fill(me ? UiColor.primary.opacity(0.18) : .clear))
        .background(level >= 1 ? AnyView(RowFxView(position: p.position, level: level)) : AnyView(EmptyView()))
        .overlay(level >= 0 ? AnyView(RowSparkles(position: p.position, level: level).allowsHitTesting(false)) : AnyView(EmptyView()))
        .contentShape(Rectangle())
    }
}

/// 줄 뒤: 햇살(1~3위) · 후광 · 배경 · 빛 줄기 · 빛나는 테두리
private struct RowFxView: View {
    let position: Int
    let level: Int

    var body: some View {
        TimelineView(.animation) { tl in
            let time = CGFloat(tl.date.timeIntervalSinceReferenceDate)
            ZStack {
                if level >= 8 {
                    Canvas { ctx, size in drawRays(&ctx, size, time) }
                        .padding(-120)
                }
                Canvas { ctx, size in drawHalo(&ctx, size, time) }.padding(-24)
                Canvas { ctx, size in drawBody(&ctx, size, time) }
            }
        }
        .allowsHitTesting(false)
    }

    private func glow(_ time: CGFloat) -> CGFloat { 0.35 + 0.65 * (0.5 + 0.5 * lsin(time * 3.7)) }

    /// 메달 뒤에서 도는 햇살 (1위는 크고 길게). 캔버스가 사방 120 더 크니 그만큼 옮겨서 그림
    private func drawRays(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat) {
        let c = CGPoint(x: 120 + 30, y: size.height / 2)
        let rowW = size.width - 240
        let len = level == 10 ? rowW * 0.75 : rowW * 0.35
        let rays = level == 10 ? 16 : 10
        let g = glow(time)
        let alpha = level == 10 ? 0.32 + 0.18 * g : 0.18 + 0.08 * g
        let spin = time / 9 * lbTau
        let grad = Gradient(colors: [tierColor(position).opacity(Double(alpha)), .clear])
        for i in 0..<rays {
            let a0 = spin + CGFloat(i) / CGFloat(rays) * lbTau
            let w: CGFloat = i % 2 == 0 ? 0.09 : 0.05
            var path = Path()
            path.move(to: c)
            path.addLine(to: CGPoint(x: c.x + lcos(a0 - w) * len, y: c.y + lsin(a0 - w) * len))
            path.addLine(to: CGPoint(x: c.x + lcos(a0 + w) * len, y: c.y + lsin(a0 + w) * len))
            path.closeSubpath()
            ctx.fill(path, with: .radialGradient(grad, center: c, startRadius: 0, endRadius: len))
        }
    }

    /// 줄 바깥으로 번지는 빛 (위로 갈수록 크고 진하게)
    private func drawHalo(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat) {
        let g = glow(time)
        let lv = CGFloat(level)
        let a = (0.12 + lv * 0.035) * (0.6 + 0.4 * g)
        let tc = tierColor(position)
        let grow: CGFloat = level == 10 ? 6 * g : -10 + lv
        let rect = CGRect(origin: .zero, size: size).insetBy(dx: 10 - grow, dy: 14 - grow)
        let grad = Gradient(colors: [tc.opacity(Double(a)), tc.opacity(Double(a * 0.4)), .clear])
        ctx.fill(Path(roundedRect: rect, cornerRadius: 24),
                 with: .radialGradient(grad, center: CGPoint(x: size.width / 2, y: size.height / 2), startRadius: 0, endRadius: size.width * 0.55))
    }

    private func drawBody(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat) {
        let lv = CGFloat(level)
        let g = glow(time)
        let hue = (time / 3).truncatingRemainder(dividingBy: 1)
        let rect = CGRect(origin: .zero, size: size)
        let shape = Path(roundedRect: rect, cornerRadius: 14)
        let tc = tierColor(position)
        // 배경 (4~10위는 아래로 갈수록 옅게)
        let bgA: Double = position <= 3 ? 1 : Double(0.45 + lv * 0.07)
        let cols: [Color] = position <= 3 ? rowColors(position, hue) : [.white, tc.opacity(0.35), Color(hex: 0xFFE6F2), tc.opacity(0.25)]
        var body = ctx
        body.clip(to: shape)
        var bg = body
        bg.opacity = bgA
        bg.fill(Path(rect), with: .linearGradient(Gradient(colors: cols), startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)))
        // 1위: 안쪽에서 맥박처럼 빛나는 금빛
        if level == 10 {
            body.fill(Path(rect), with: .radialGradient(Gradient(colors: [Color.white.opacity(Double(0.55 * g)), .clear]),
                                                        center: CGPoint(x: size.width * 0.3, y: size.height / 2), startRadius: 0, endRadius: size.width * 0.5))
        }
        // 빛 줄기 (1위 세 줄 · 2~3위 두 줄)
        let period = 1.2 + CGFloat(10 - level) * 0.17
        let shine = (time / period).truncatingRemainder(dividingBy: 1) * 1.8 - 0.4
        let n = level == 10 ? 3 : level >= 8 ? 2 : 1
        for k in 0..<n {
            let b = (shine + 0.4 + CGFloat(k) * 1.8 / CGFloat(n)).truncatingRemainder(dividingBy: 1.8) - 0.4
            let x = b * size.width
            let gr = Gradient(colors: [.clear, Color.white.opacity(Double(0.35 + lv * 0.05)), .clear])
            body.fill(Path(rect), with: .linearGradient(gr, startPoint: CGPoint(x: x - size.height, y: 0), endPoint: CGPoint(x: x + size.height, y: size.height)))
        }
        // 빛나는 테두리: 넓고 흐린 빛 + 선명한 선 (1위 무지개)
        let w: CGFloat = level == 10 ? 3 : level >= 8 ? 2.2 : 1 + lv * 0.15
        let shading: GraphicsContext.Shading
        if level == 10 {
            shading = .conicGradient(Gradient(colors: rainbow(hue)), center: CGPoint(x: size.width / 2, y: size.height / 2))
        } else if level >= 8 {
            shading = .linearGradient(Gradient(colors: Array(rowColors(position, hue).dropFirst())), startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height))
        } else {
            shading = .linearGradient(Gradient(colors: [tc, .white, tc]), startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height))
        }
        let inner = Path(roundedRect: rect.insetBy(dx: w / 2, dy: w / 2), cornerRadius: 14)
        var wide = ctx
        wide.opacity = Double((0.12 + lv * 0.025) * (0.5 + 0.5 * g))
        wide.stroke(inner, with: shading, lineWidth: w * 3.5)
        var line = ctx
        line.opacity = Double(0.55 + lv * 0.045)
        line.stroke(inner, with: shading, lineWidth: w)
    }
}

/// 줄 앞: 반짝이 (1위 32개 → 10위 5개, 11~30위 3개) · 떠오르는 빛가루 · 메달 주위 별빛.
/// 글자를 가리지 않게 위·아래 가장자리에만.
private struct RowSparkles: View {
    let position: Int
    let level: Int

    var body: some View {
        let count = level >= 1 ? 3 + level * 2 + (level == 10 ? 9 : 0) : 3
        let ss = seeds(count, position * 97 + 13)
        let motes = seeds(level >= 1 ? level * 2 - 2 + (level == 10 ? 4 : 0) : 0, position * 31 + 5)
        return TimelineView(.animation) { tl in
            let time = CGFloat(tl.date.timeIntervalSinceReferenceDate)
            Canvas { ctx, size in
                drawMotes(&ctx, size, time, motes)
                draw(&ctx, size, time, ss)
            }
        }
    }

    private func color(_ i: Int, _ hue: CGFloat) -> Color {
        if level == 10 { return [Color(hex: 0xFFF59D), .white, Color(hex: 0xFFD54F), hsv(hue + CGFloat(i) * 50 / 360, 0.45, 1)][i % 4] }
        if level >= 1 { return i % 2 == 0 ? .white : tierColor(position).opacity(0.8) }
        return i % 2 == 0 ? Color(hex: 0xFFE27A) : Color(hex: 0xFFB3D6)
    }

    private func drawMotes(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat, _ ms: [(CGFloat, CGFloat, CGFloat)]) {
        let rise = time / 2.6
        for (i, m) in ms.enumerated() {
            let q = (rise * (0.7 + m.2 * 0.6) + m.1).truncatingRemainder(dividingBy: 1)
            let x = (0.08 + m.0 * 0.88) * size.width + lsin((q * 2 + m.0) * lbTau) * 4
            let y = i % 2 == 0 ? size.height * (0.28 - q * 0.55) : size.height * (1.25 - q * 0.5)
            let a = lsin(q * .pi)
            let r = 1.4 + m.2 * 1.8 + CGFloat(level) * 0.12
            let col: Color = level == 10 ? (i % 3 == 0 ? .white : Color(hex: 0xFFD54F)) : (i % 2 == 0 ? .white : tierColor(position))
            ctx.fill(Path(ellipseIn: CGRect(x: x - r * 2.4, y: y - r * 2.4, width: r * 4.8, height: r * 4.8)), with: .color(col.opacity(Double(a * 0.35))))
            ctx.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(col.opacity(Double(a))))
        }
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ time: CGFloat, _ ss: [(CGFloat, CGFloat, CGFloat)]) {
        let tw = time / 1.5
        let hue = (time / 3).truncatingRemainder(dividingBy: 1)
        for (i, s) in ss.enumerated() {
            let speed = 1 + CGFloat(i % 3) * 0.2
            let p = (tw * speed + s.2).truncatingRemainder(dividingBy: 1)
            let a = lsin(p * .pi)
            let fy: CGFloat = s.1 < 0.5 ? -0.12 + s.1 * 0.4 : 0.92 + (s.1 - 0.5) * 0.4
            let pos = CGPoint(x: (0.03 + s.0 * 0.94) * size.width, y: fy * size.height)
            let base: CGFloat = level >= 1 ? 2.6 + CGFloat(level) * 0.5 : 2.6
            let maxA: CGFloat = level >= 1 ? 0.95 : 0.8
            twinkleStar(&ctx, pos, base * (0.4 + a * 0.8), color(i, hue).opacity(Double(a * maxA)))
        }
        // 1~3위: 메달 주위를 도는 별빛 (1위 5개)
        if level >= 8 {
            let n = level == 10 ? 5 : 2
            for k in 0..<n {
                let ang = (tw + CGFloat(k) / CGFloat(n)) * lbTau
                let pos = CGPoint(x: 30 + lcos(ang) * 24, y: size.height / 2 + lsin(ang) * 16)
                twinkleStar(&ctx, pos, level == 10 ? 4 : 3, Color(hex: 0xFFF8E1).opacity(0.95))
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
