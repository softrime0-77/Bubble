import BubbleCore
import SwiftUI

// ======================= 캐릭터별 이펙트 테마 =======================

/// 터질 때·날아갈 때 흩뿌리는 모양
enum FxShape { case star, heart, paw, snow, coin, bone, leaf, cloud, moon, seed }

/// 캐릭터마다 다른 반짝이 색과 모양, 터질 때 붙는 이모지
struct FxTheme {
    let colors: [Color]
    let shapes: [FxShape]
    let emoji: String
    var main: Color { colors[0] }

    static func of(_ side: Side, _ skin: Animal?) -> FxTheme {
        switch skin ?? (side == .cat ? nil : .hamster) {
        case nil:
            return FxTheme(colors: [Color(hex: 0xFF9A3C), Color(hex: 0xFFC94D), .white, Color(hex: 0xFFD3A8)], shapes: [.paw, .star], emoji: "🐾")
        case .hamster:
            return FxTheme(colors: [Color(hex: 0xB98CFF), Color(hex: 0xFF9EC7), .white, Color(hex: 0xFFE3A3)], shapes: [.seed, .heart, .star], emoji: "🌻")
        case .pinkCat:
            return FxTheme(colors: [Color(hex: 0xFF4FA0), Color(hex: 0xFFA8D2), .white, Color(hex: 0xFFD6EC)], shapes: [.heart, .star], emoji: "💗")
        case .blackCat:
            return FxTheme(colors: [Color(hex: 0x7A4DFF), Color(hex: 0xFFD54A), .white, Color(hex: 0x3B2A6B)], shapes: [.moon, .star], emoji: "🌙")
        case .snowHamster:
            return FxTheme(colors: [Color(hex: 0x5CC8FF), Color(hex: 0xBFE9FF), .white, Color(hex: 0x9AB8FF)], shapes: [.snow, .star], emoji: "❄️")
        case .goldHamster:
            return FxTheme(colors: [Color(hex: 0xFFB800), Color(hex: 0xFFE680), .white, Color(hex: 0xFF9F1C)], shapes: [.coin, .star], emoji: "✨")
        case .sheep:
            return FxTheme(colors: [Color(hex: 0x9EC9FF), .white, Color(hex: 0xFFC2DE), Color(hex: 0xE8F1FF)], shapes: [.cloud, .star], emoji: "☁️")
        case .dog:
            return FxTheme(colors: [Color(hex: 0xC98A4B), Color(hex: 0x7FD3FF), .white, Color(hex: 0xFFE0B0)], shapes: [.bone, .paw, .star], emoji: "🦴")
        case .giraffe:
            return FxTheme(colors: [Color(hex: 0xFFC53D), Color(hex: 0x6CCB5F), Color(hex: 0xD9893A), .white], shapes: [.leaf, .star], emoji: "🍃")
        }
    }
}

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

/// [shape]을 [c]에 반지름 [r]로 그림
func drawFx(_ ctx: inout GraphicsContext, _ shape: FxShape, _ c: CGPoint, _ r: CGFloat, _ color: Color, rot: Double = 0) {
    guard r > 0.3 else { return }
    var g = ctx
    g.translateBy(x: c.x, y: c.y)
    g.rotate(by: .degrees(rot))
    let o = CGPoint.zero
    let shine = Color.white.opacity(0.75)
    switch shape {
    case .star:
        g.fill(sparklePath(o, r * 1.25), with: .color(color))
        g.fill(circle(o, r * 0.22), with: .color(.white.opacity(0.9)))
    case .heart:
        g.fill(heartPath(o, r), with: .color(color))
        g.fill(circle(CGPoint(x: -r * 0.45, y: -r * 0.35), r * 0.2), with: .color(shine))
    case .paw:
        g.fill(Path(ellipseIn: CGRect(x: -r * 0.55, y: -r * 0.05, width: r * 1.1, height: r * 0.9)), with: .color(color))
        for (dx, dy) in [(-0.62, -0.3), (-0.22, -0.72), (0.22, -0.72), (0.62, -0.3)] {
            g.fill(circle(CGPoint(x: dx * r, y: dy * r), r * 0.26), with: .color(color))
        }
    case .snow:
        var p = Path()
        for k in 0..<3 {
            let a = Double(k) * .pi / 3
            let d = CGPoint(x: cos(a) * r, y: sin(a) * r)
            p.move(to: CGPoint(x: -d.x, y: -d.y)); p.addLine(to: d)
            for s in [-1.0, 1.0] {
                let tip = CGPoint(x: d.x * 0.6 * s, y: d.y * 0.6 * s)
                for da in [0.7, -0.7] {
                    p.move(to: tip)
                    p.addLine(to: CGPoint(x: tip.x + cos(a + da) * r * 0.32 * s, y: tip.y + sin(a + da) * r * 0.32 * s))
                }
            }
        }
        g.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: r * 0.2, lineCap: .round))
    case .coin:
        g.fill(circle(o, r), with: .color(color))
        g.stroke(circle(o, r * 0.72), with: .color(Color(hex: 0xE08A00)), lineWidth: r * 0.16)
        g.fill(circle(CGPoint(x: -r * 0.35, y: -r * 0.35), r * 0.22), with: .color(shine))
    case .bone:
        g.fill(Path(CGRect(x: -r * 0.7, y: -r * 0.2, width: r * 1.4, height: r * 0.4)), with: .color(color))
        for sx in [-0.75, 0.75] { for sy in [-0.22, 0.22] {
            g.fill(circle(CGPoint(x: sx * r, y: sy * r), r * 0.27), with: .color(color))
        } }
    case .leaf:
        var p = Path()
        p.move(to: CGPoint(x: 0, y: -r))
        p.addQuadCurve(to: CGPoint(x: 0, y: r), control: CGPoint(x: r * 0.95, y: 0))
        p.addQuadCurve(to: CGPoint(x: 0, y: -r), control: CGPoint(x: -r * 0.95, y: 0))
        g.fill(p, with: .color(color))
        var vein = Path(); vein.move(to: CGPoint(x: 0, y: -r * 0.8)); vein.addLine(to: CGPoint(x: 0, y: r * 0.8))
        g.stroke(vein, with: .color(.white.opacity(0.6)), lineWidth: r * 0.1)
    case .cloud:
        var p = circle(CGPoint(x: -r * 0.45, y: r * 0.12), r * 0.5)
        p.addPath(circle(CGPoint(x: 0, y: -r * 0.12), r * 0.62))
        p.addPath(circle(CGPoint(x: r * 0.48, y: r * 0.12), r * 0.48))
        p.addPath(Path(roundedRect: CGRect(x: -r * 0.9, y: 0, width: r * 1.8, height: r * 0.6), cornerRadius: r * 0.3))
        g.fill(p, with: .color(color))
    case .moon:
        g.drawLayer { l in
            l.clip(to: circle(CGPoint(x: r * 0.45, y: -r * 0.25), r * 0.85), options: .inverse)
            l.fill(circle(o, r), with: .color(color))
        }
    case .seed:
        g.fill(Path(ellipseIn: CGRect(x: -r * 0.45, y: -r, width: r * 0.9, height: r * 2)), with: .color(color))
        var line = Path(); line.move(to: CGPoint(x: 0, y: -r * 0.7)); line.addLine(to: CGPoint(x: 0, y: r * 0.7))
        g.stroke(line, with: .color(.white.opacity(0.7)), style: StrokeStyle(lineWidth: r * 0.14, lineCap: .round))
    }
}

/// 파티클 하나의 무작위 값 (같은 seed면 같은 모양)
struct FxPart { let a: Double; let s: Double; let spin: Double; let k: Int }

func fxParts(_ n: Int, seed: UInt64) -> [FxPart] {
    var g = SeededRandom(seed: seed)
    return (0..<n).map { _ in FxPart(a: Double(g.next()), s: Double(g.next()), spin: Double(g.next()) * 2 - 1, k: Int(g.next() * 1000)) }
}

/// 화려한 폭발: 섬광 + 빛줄기 + 충격파 2겹 + 캐릭터 모양 파편 + 반짝이 가루. [p]는 0 → 1.
func drawBurst(_ ctx: inout GraphicsContext, _ c: CGPoint, _ radius: CGFloat, _ p: Double, _ theme: FxTheme, _ ps: [FxPart], rays: Int = 12) {
    let fade = 1 - p
    // 섬광
    let fr = radius * (0.5 + p * 0.9)
    ctx.fill(circle(c, fr), with: .radialGradient(
        Gradient(colors: [.white.opacity(fade * fade * 0.95), theme.main.opacity(fade * fade * 0.5), .clear]),
        center: c, startRadius: 0, endRadius: fr))
    // 빛줄기
    if p < 0.6 {
        let rp = p / 0.6
        for i in 0..<rays {
            let ang = Double(i) * 2 * .pi / Double(rays) + p * 0.6
            let d = CGPoint(x: cos(ang), y: sin(ang))
            var ray = Path()
            ray.move(to: CGPoint(x: c.x + d.x * radius * (0.2 + rp * 0.5), y: c.y + d.y * radius * (0.2 + rp * 0.5)))
            ray.addLine(to: CGPoint(x: c.x + d.x * radius * (0.6 + rp * 1.1), y: c.y + d.y * radius * (0.6 + rp * 1.1)))
            ctx.stroke(ray, with: .color(theme.colors[i % theme.colors.count].opacity((1 - rp) * 0.9)),
                       style: StrokeStyle(lineWidth: (1 - rp) * radius * 0.09, lineCap: .round))
        }
    }
    // 충격파 2겹
    ctx.stroke(circle(c, radius * (0.5 + p * 1.0)), with: .color(theme.main.opacity(fade * 0.85)), lineWidth: fade * radius * 0.06)
    let p2 = min(1, max(0, (p - 0.12) / 0.88))
    if p2 > 0 {
        ctx.stroke(circle(c, radius * (0.4 + p2 * 1.4)), with: .color(.white.opacity((1 - p2) * 0.9)), lineWidth: (1 - p2) * radius * 0.04)
    }
    // 파편 (캐릭터 모양)
    let ease = 1 - (1 - p) * (1 - p)
    for (i, q) in ps.enumerated() {
        let ang = (Double(i) / Double(ps.count) + q.a * 0.08) * 2 * .pi
        let dist = radius * (0.25 + ease * (0.9 + q.s * 0.9))
        let pos = CGPoint(x: c.x + cos(ang) * dist, y: c.y + sin(ang) * dist + p * p * radius * 0.5)
        let color = theme.colors[q.k % theme.colors.count]
        let shape = theme.shapes[q.k % theme.shapes.count]
        let sz = radius * (0.06 + q.s * 0.06) * (1 - p * 0.6)
        drawFx(&ctx, shape, pos, sz, color.opacity(1 - p * p), rot: q.spin * 360 * p)
    }
    // 반짝이 가루 (깜빡임)
    for i in 0..<ps.count {
        let q = ps[(i * 7) % ps.count]
        let ang = (q.a + Double(i) * 0.13) * 2 * .pi
        let dist = radius * (0.3 + p * (0.5 + q.s * 1.3))
        let tw = 0.5 + 0.5 * sin(p * 30 + Double(i))
        ctx.fill(circle(CGPoint(x: c.x + cos(ang) * dist, y: c.y + sin(ang) * dist), radius * 0.025 * (1 + q.s)),
                 with: .color(.white.opacity(fade * tw)))
    }
}

// ======================= 방울 안 효과 (터짐 · 선택) =======================

/// 방울이 터질 때 캐릭터별 화려한 효과
struct PopBurst: View {
    let side: Side
    let skin: Animal?
    let p: Double

    var body: some View {
        let theme = FxTheme.of(side, skin)
        let ps = fxParts(30, seed: 11)
        ZStack {
            // 방울 밖으로도 튀어나가도록 그리는 영역을 넓힘
            Canvas { ctx, sz in
                let r = (min(sz.width, sz.height) - 120) / 2
                drawBurst(&ctx, CGPoint(x: sz.width / 2, y: sz.height / 2), r * 1.15, p, theme, ps, rays: 14)
            }
            .padding(-60)
            .allowsHitTesting(false)
            Text(theme.emoji + " " + t("펑!"))
                .font(.system(size: 30, weight: .black))
                .foregroundColor(.white)
                .shadow(color: theme.main, radius: 8)
                .shadow(color: theme.main, radius: 3)
                .scaleEffect(p < 0.25 ? 0.4 + p / 0.25 * 0.9 : 1.3 - (p - 0.25) * 0.3)
                .rotationEffect(.degrees(-8 + p * 10))
                .opacity(min(1, (1 - p) * 1.4))
        }
    }
}

/// 고른 방울 주위를 빙글빙글 도는 반짝이
struct SelectedSparkles: View {
    let side: Side
    let skin: Animal?
    let time: Double

    var body: some View {
        let theme = FxTheme.of(side, skin)
        let spin = time.truncatingRemainder(dividingBy: 2.6) / 2.6
        Canvas { ctx, sz in
            let r = (min(sz.width, sz.height) - 60) / 2
            let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
            ctx.fill(circle(c, r * 1.3), with: .radialGradient(
                Gradient(colors: [.clear, UiColor.gold.opacity(0.45), .clear]), center: c, startRadius: 0, endRadius: r * 1.3))
            for i in 0..<8 {
                let ang = (spin + Double(i) / 8) * 2 * .pi
                let rr = r * (1.08 + 0.06 * sin(spin * 4 * .pi + Double(i)))
                let pos = CGPoint(x: c.x + cos(ang) * rr, y: c.y + sin(ang) * rr)
                let tw = 0.55 + 0.45 * sin(spin * 6 * .pi + Double(i) * 1.7)
                if i % 2 == 0 {
                    drawFx(&ctx, theme.shapes[i / 2 % theme.shapes.count], pos, r * 0.13 * tw, theme.colors[i / 2 % theme.colors.count], rot: spin * 360)
                } else {
                    ctx.fill(sparklePath(pos, r * 0.16 * tw), with: .color(UiColor.gold))
                }
            }
        }
        .padding(-30)
        .allowsHitTesting(false)
    }
}

// ======================= 날아가서 치는 방울 =======================

/// 화면에 날아다니는 방울 하나 (좌표는 게임 화면 기준)
struct FlyingShot: Identifiable, Equatable {
    let id = UUID()
    let from: CGPoint
    let to: CGPoint
    let side: Side
    let skin: Animal?
    let power: Int
    /// 상대 방울을 침 (아니면 내 방울 키우기)
    let attack: Bool
    /// 이 방울을 치면 터짐
    let boom: Bool
    let start = Date()

    static let flySec = 0.52
    static let impactSec = 0.75
}

/// 날아가는 방울 하나: 반짝이 꼬리를 남기며 휘어서 날아가 부딪힘
struct ShotFx: View {
    let shot: FlyingShot

    private func at(_ time: Double, _ ctrl: CGPoint) -> CGPoint {
        let t = CGFloat(time)
        let u: CGFloat = 1 - t
        let a: CGFloat = u * u, b: CGFloat = 2 * u * t, c: CGFloat = t * t
        let x: CGFloat = shot.from.x * a + ctrl.x * b + shot.to.x * c
        let y: CGFloat = shot.from.y * a + ctrl.y * b + shot.to.y * c
        return CGPoint(x: x, y: y)
    }

    /// 살짝 뒤로 당겼다가 쓩 (back-in 느낌)
    private func flyEase(_ x: Double) -> Double {
        let s = 1.2
        return x * x * ((s + 1) * x - s) * 0.5 + x * 0.5
    }

    var body: some View {
        let theme = FxTheme.of(shot.side, shot.skin)
        let ps = fxParts(14 + shot.power * 3 + (shot.boom ? 8 : 0), seed: UInt64(shot.power * 7 + 3))
        let trail = fxParts(22, seed: 5)
        let dx = shot.to.x - shot.from.x, dy = shot.to.y - shot.from.y
        let len = max(1, sqrt(dx * dx + dy * dy))
        let perp = CGPoint(x: -dy / len, y: dx / len)
        let bend: CGFloat = shot.from.x <= shot.to.x ? 1 : -1
        let ctrl = CGPoint(x: (shot.from.x + shot.to.x) / 2 + perp.x * len * 0.28 * bend,
                           y: (shot.from.y + shot.to.y) / 2 + perp.y * len * 0.28 * bend)
        let orb = CGFloat(34 + shot.power * 4)

        TimelineView(.animation) { tl in
            let el = tl.date.timeIntervalSince(shot.start)
            let flying = el < FlyingShot.flySec
            let f = min(1, max(0, flyEase(min(1, el / FlyingShot.flySec))))
            let h = min(1, max(0, (el - FlyingShot.flySec) / FlyingShot.impactSec))
            let head = at(f, ctrl)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    if flying {
                        // 꼬리
                        for (k, q) in trail.enumerated() {
                            let tk = f - Double(k + 1) * 0.028
                            if tk <= 0 { continue }
                            let fade = 1 - Double(k) / Double(trail.count)
                            let base = at(tk, ctrl)
                            let w: CGFloat = CGFloat(sin(tk * 22 + q.a * 6) * 0.35 * q.s) * orb
                            let pos = CGPoint(x: base.x + perp.x * w, y: base.y + perp.y * w)
                            ctx.fill(circle(base, orb * 0.5 * fade), with: .color(theme.main.opacity(fade * 0.25)))
                            if k % 2 == 0 {
                                drawFx(&ctx, theme.shapes[q.k % theme.shapes.count], pos, orb * 0.22 * fade,
                                       theme.colors[q.k % theme.colors.count].opacity(fade), rot: q.spin * 300 * f)
                            } else {
                                ctx.fill(sparklePath(pos, orb * 0.2 * fade), with: .color(.white.opacity(fade)))
                            }
                        }
                        // 빛나는 방울
                        ctx.fill(circle(head, orb * 1.1), with: .radialGradient(
                            Gradient(colors: [.white, theme.main.opacity(0.7), theme.main.opacity(0)]),
                            center: head, startRadius: 0, endRadius: orb * 1.1))
                        ctx.stroke(circle(head, orb * 0.62), with: .color(.white.opacity(0.95)), lineWidth: orb * 0.07)
                        ctx.fill(circle(CGPoint(x: head.x - orb * 0.28, y: head.y - orb * 0.3), orb * 0.12), with: .color(.white.opacity(0.9)))
                    } else {
                        drawBurst(&ctx, shot.to, orb * (1.5 + CGFloat(shot.power) * 0.08) * (shot.boom ? 1.2 : 1), h, theme, ps,
                                  rays: shot.boom ? 18 : 12)
                    }
                }
                if flying {
                    Critter(side: shot.side, skin: shot.skin, happy: true)
                        .frame(width: orb, height: orb)
                        .scaleEffect(0.8 + f * 0.3)
                        .rotationEffect(.degrees(f * 360 * (shot.attack ? 1 : -1)))
                        .position(head)
                } else {
                    Text("+\(shot.power)")
                        .font(.system(size: 28, weight: .black))
                        .foregroundColor(.white)
                        .shadow(color: theme.main, radius: 8)
                        .shadow(color: theme.main, radius: 3)
                        .scaleEffect(h < 0.2 ? 0.5 + h * 4 : 1.3 - (h - 0.2) * 0.35)
                        .opacity(h < 0.7 ? 1 : (1 - h) / 0.3)
                        .position(x: shot.to.x, y: shot.to.y - 36 - h * 48)
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// 상대를 치면 화면이 살짝 흔들림 (animatableData를 1씩 올리면 한 번 흔들림)
struct ShakeEffect: GeometryEffect {
    var amount: CGFloat = 7
    var animatableData: CGFloat
    func effectValue(size: CGSize) -> ProjectionTransform {
        let frac = animatableData - floor(animatableData)
        let k = 1 - frac
        let x = sin(animatableData * .pi * 2 * 4) * amount * k
        let y = cos(animatableData * .pi * 2 * 3) * amount * 0.5 * k
        return ProjectionTransform(CGAffineTransform(translationX: x, y: y))
    }
}

// ======================= 출석 완료 팝업 =======================

/// 출석 완료! 쏟아지는 색종이 · 회전하는 빛 · 도장 쾅 · 💰 숫자 올라가기
struct CheckinCelebration: View {
    let day: Int
    let coins: Int
    let potion: Bool
    let onClose: () -> Void
    @State private var startedAt = Date()
    @State private var pop = false

    private static let confetti = fxParts(70, seed: 21)
    private static let burstParts = fxParts(34, seed: 31)
    private let confettiColors = [UiColor.primary, UiColor.gold, UiColor.accent, Color(hex: 0x5CD6B4), Color(hex: 0x6EC1FF), Color(hex: 0xFF8A65), .white]
    private let burstTheme = FxTheme(colors: [UiColor.gold, UiColor.primary, .white, UiColor.accent], shapes: [.star, .heart, .coin], emoji: "🎉")

    var body: some View {
        TimelineView(.animation) { tl in
            let el = tl.date.timeIntervalSince(startedAt)
            let now = tl.date.timeIntervalSinceReferenceDate
            let stamp = min(1, max(0, (el - 0.38) / 0.32))
            let stampEase = stamp * stamp
            let burstP = min(1, max(0, (el - 0.7) / 1.1))
            let count = min(1, max(0, (el - 0.65) / 0.9))
            let countEase = 1 - pow(1 - count, 3)
            ZStack {
                RadialGradient(colors: [Color(hex: 0x3A1F5C, alpha: 0.8), Color(hex: 0x140B22, alpha: 0.93)], center: .center, startRadius: 0, endRadius: 600)
                    .ignoresSafeArea()
                // 회전하는 빛줄기 + 색종이
                Canvas { ctx, size in
                    let c = CGPoint(x: size.width / 2, y: size.height * 0.42)
                    let big = max(size.width, size.height)
                    let spin = now.truncatingRemainder(dividingBy: 9) / 9
                    for i in 0..<16 {
                        let a0 = (spin + Double(i) / 16) * 2 * .pi, a1 = a0 + .pi / 22
                        var p = Path()
                        p.move(to: c)
                        p.addLine(to: CGPoint(x: c.x + cos(a0) * big, y: c.y + sin(a0) * big))
                        p.addLine(to: CGPoint(x: c.x + cos(a1) * big, y: c.y + sin(a1) * big))
                        p.closeSubpath()
                        ctx.fill(p, with: .radialGradient(Gradient(colors: [UiColor.gold.opacity(0.32 * (pop ? 1 : 0)), .clear]),
                                                          center: c, startRadius: 0, endRadius: big * 0.6))
                    }
                    let fall = now.truncatingRemainder(dividingBy: 3.6) / 3.6
                    for (i, q) in Self.confetti.enumerated() {
                        let y = (fall + q.a).truncatingRemainder(dividingBy: 1) * (size.height + 80) - 40
                        let x = q.s * size.width + sin(fall * 2 * .pi * (1 + q.spin) + Double(i)) * 30
                        let color = confettiColors[q.k % confettiColors.count]
                        let sz = 5 + q.s * 4
                        switch q.k % 3 {
                        case 0:
                            var g = ctx
                            g.translateBy(x: x, y: y)
                            g.rotate(by: .degrees(fall * 720 * q.spin + Double(q.k)))
                            g.fill(Path(CGRect(x: -sz, y: -sz * 0.45, width: sz * 2, height: sz * 0.9)), with: .color(color))
                        case 1:
                            ctx.fill(circle(CGPoint(x: x, y: y), sz * 0.6), with: .color(color))
                        default:
                            ctx.fill(sparklePath(CGPoint(x: x, y: y), sz), with: .color(color))
                        }
                    }
                }
                .ignoresSafeArea()
                .allowsHitTesting(false)

                card(now: now, stamp: stampEase, burstP: burstP, countEase: countEase)
                    .scaleEffect(pop ? 1 : 0.6)
                    .opacity(pop ? 1 : 0)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClose)
        .onAppear {
            startedAt = Date()
            withAnimation(.spring(response: 0.55, dampingFraction: 0.5)) { pop = true }
        }
    }

    private func card(now: Double, stamp: Double, burstP: Double, countEase: Double) -> some View {
        let next = day % 7 + 1
        let nextCoins = OnlineService.checkinCoins[next - 1]
        return VStack(spacing: 0) {
            // 도장 쾅!
            ZStack {
                Canvas { ctx, sz in
                    let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
                    if burstP > 0 && burstP < 1 {
                        drawBurst(&ctx, c, min(sz.width, sz.height) / 2 * 0.75, burstP, burstTheme, Self.burstParts, rays: 16)
                    }
                    let tw0 = now.truncatingRemainder(dividingBy: 1.6) / 1.6
                    for i in 0..<6 {
                        let ang = (tw0 + Double(i) / 6) * 2 * .pi
                        let rr = min(sz.width, sz.height) * 0.36
                        let tw = 0.5 + 0.5 * sin(tw0 * 4 * .pi + Double(i) * 1.3)
                        ctx.fill(sparklePath(CGPoint(x: c.x + cos(ang) * rr, y: c.y + sin(ang) * rr), 9 * tw),
                                 with: .color([UiColor.gold, UiColor.primary, UiColor.accent][i % 3]))
                    }
                }
                .frame(width: 220, height: 220)
                .allowsHitTesting(false)
                ZStack {
                    Circle().fill(RadialGradient(colors: [UiColor.primary, UiColor.primaryDark], center: .center, startRadius: 0, endRadius: 54))
                    Circle().stroke(.white, lineWidth: 4)
                    VStack(spacing: 0) {
                        Text(tr("\(day)일째", "DAY \(day)", zh: "第\(day)天", es: "DÍA \(day)", fr: "JOUR \(day)", de: "TAG \(day)",
                                pt: "DIA \(day)", ru: "ДЕНЬ \(day)", ja: "\(day)日目"))
                            .font(.system(size: 20, weight: .black)).foregroundColor(.white)
                        Text("✔").font(.system(size: 30, weight: .black)).foregroundColor(.white)
                    }
                }
                .frame(width: 108, height: 108)
                .scaleEffect(2.6 - 1.6 * stamp)
                .rotationEffect(.degrees(-14 * stamp))
                .opacity(min(1, stamp * 2))
            }
            .frame(width: 130, height: 130)

            Text(tr("출석 완료!", "Checked in!", zh: "签到成功！", es: "¡Asistencia lista!", fr: "Présence validée !", de: "Eingecheckt!",
                    pt: "Presença feita!", ru: "Вход отмечен!", ja: "出席完了！"))
                .font(.system(size: 30, weight: .black))
                .foregroundStyle(LinearGradient(colors: [UiColor.primaryDark, UiColor.accent], startPoint: .leading, endPoint: .trailing))
                .shadow(color: UiColor.gold.opacity(0.6), radius: 5, y: 3)
                .padding(.top, 10)

            // 7일 진행
            HStack(spacing: 5) {
                ForEach(1...7, id: \.self) { d in
                    let done = d <= day, today = d == day
                    ZStack {
                        Circle().fill(today ? LinearGradient(colors: [UiColor.gold, Color(hex: 0xFF9F1C)], startPoint: .top, endPoint: .bottom)
                                      : done ? LinearGradient(colors: [UiColor.primary, UiColor.primaryDark], startPoint: .top, endPoint: .bottom)
                                      : LinearGradient(colors: [.black.opacity(0.12), .black.opacity(0.09)], startPoint: .top, endPoint: .bottom))
                        if today { Circle().stroke(.white, lineWidth: 2) }
                        Text(done ? "✓" : d == 7 ? "💖" : "\(d)")
                            .font(.system(size: done ? 16 : 12, weight: .black))
                            .foregroundColor(done ? .white : UiColor.text.opacity(0.5))
                    }
                    .frame(width: 32, height: 32)
                    .scaleEffect(today ? min(stamp * 1.25, 1 + 0.25 * (1 - burstP)) : 1)
                }
            }
            .padding(.top, 14)

            // 보상
            HStack(spacing: 10) {
                rewardChip("💰", "+\(Int((Double(coins) * countEase).rounded()))", UiColor.gold)
                if potion { rewardChip("💖", "+1", Color(hex: 0xFF5C8A)).scaleEffect(0.6 + 0.4 * countEase) }
            }
            .padding(.top, 16)

            Text(day == 7
                 ? tr("🎉 7일 개근! 내일은 1일째부터 다시 시작해요", "🎉 7-day streak! Tomorrow starts again at day 1", zh: "🎉 连续 7 天！明天从第 1 天重新开始",
                      es: "🎉 ¡7 días seguidos! Mañana vuelves al día 1", fr: "🎉 7 jours d’affilée ! Demain, retour au jour 1",
                      de: "🎉 7 Tage am Stück! Morgen geht’s bei Tag 1 weiter", pt: "🎉 7 dias seguidos! Amanhã recomeça no dia 1",
                      ru: "🎉 7 дней подряд! Завтра снова с 1-го дня", ja: "🎉 7日皆勤！明日は1日目から")
                 : tr("내일도 오면 💰 +\(nextCoins)", "Come back tomorrow for 💰 +\(nextCoins)", zh: "明天再来可得 💰 +\(nextCoins)",
                      es: "Vuelve mañana por 💰 +\(nextCoins)", fr: "Reviens demain pour 💰 +\(nextCoins)", de: "Morgen wieder: 💰 +\(nextCoins)",
                      pt: "Volte amanhã por 💰 +\(nextCoins)", ru: "Завтра: 💰 +\(nextCoins)", ja: "明日も来ると 💰 +\(nextCoins)"))
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(UiColor.text.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.top, 10)

            Button(action: onClose) {
                Text(tr("좋아요! 💕", "Yay! 💕", zh: "太好了！💕", es: "¡Genial! 💕", fr: "Super ! 💕", de: "Juhu! 💕", pt: "Oba! 💕", ru: "Ура! 💕", ja: "やった！💕"))
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
            }
            .padding(.top, 16)
        }
        .padding(.horizontal, 22).padding(.vertical, 24)
        .background(RoundedRectangle(cornerRadius: 30).fill(LinearGradient(colors: [Color(hex: 0xFFF7FB), Color(hex: 0xF1ECFF)], startPoint: .top, endPoint: .bottom)))
        .overlay(RoundedRectangle(cornerRadius: 30).stroke(AngularGradient(colors: [UiColor.primary, UiColor.gold, UiColor.accent, Color(hex: 0x6EC1FF), UiColor.primary], center: .center), lineWidth: 3))
        .contentShape(Rectangle())
        .onTapGesture {}
        .padding(28)
    }

    private func rewardChip(_ icon: String, _ text: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Text(icon).font(.system(size: 24))
            Text(text).font(.system(size: 24, weight: .black)).foregroundColor(UiColor.text).monospacedDigit()
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
        .background(Capsule().fill(color.opacity(0.18)))
        .overlay(Capsule().stroke(color.opacity(0.7), lineWidth: 2))
    }
}
