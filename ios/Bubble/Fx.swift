import BubbleCore
import SwiftUI

// 계산은 모두 CGFloat로 (Double과 섞으면 컴파일러가 타입을 못 정함)
private let tau: CGFloat = .pi * 2

private func fcos(_ x: CGFloat) -> CGFloat { CGFloat(cos(Double(x))) }
private func fsin(_ x: CGFloat) -> CGFloat { CGFloat(sin(Double(x))) }
private func clamp01(_ x: CGFloat) -> CGFloat { min(1, max(0, x)) }

/// [c]에서 [ang] 방향으로 [d]만큼 간 점
private func polar(_ c: CGPoint, _ ang: CGFloat, _ d: CGFloat) -> CGPoint {
    CGPoint(x: c.x + fcos(ang) * d, y: c.y + fsin(ang) * d)
}

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

// ======================= 캐릭터별 이펙트 테마 =======================

/// 터질 때·날아갈 때 흩뿌리는 모양
enum FxShape { case star, heart, paw, snow, coin, bone, leaf, cloud, moon, seed }

/// 캐릭터마다 다른 반짝이 색과 모양, 터질 때 붙는 이모지
struct FxTheme {
    let colors: [Color]
    let shapes: [FxShape]
    let emoji: String
    var main: Color { colors[0] }

    func color(_ k: Int) -> Color { colors[k % colors.count] }
    func shape(_ k: Int) -> FxShape { shapes[k % shapes.count] }

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

/// 눈꽃 모양 선
private func snowPath(_ r: CGFloat) -> Path {
    var p = Path()
    for k in 0..<3 {
        let a: CGFloat = CGFloat(k) * .pi / 3
        let d = CGPoint(x: fcos(a) * r, y: fsin(a) * r)
        p.move(to: CGPoint(x: -d.x, y: -d.y))
        p.addLine(to: d)
        for s: CGFloat in [-1, 1] {
            let tip = CGPoint(x: d.x * 0.6 * s, y: d.y * 0.6 * s)
            for da: CGFloat in [0.7, -0.7] {
                let b = r * 0.32 * s
                p.move(to: tip)
                p.addLine(to: CGPoint(x: tip.x + fcos(a + da) * b, y: tip.y + fsin(a + da) * b))
            }
        }
    }
    return p
}

/// [shape]을 [c]에 반지름 [r]로 그림 ([rot]는 도 단위 회전)
func drawFx(_ ctx: inout GraphicsContext, _ shape: FxShape, _ c: CGPoint, _ r: CGFloat, _ color: Color, rot: CGFloat = 0) {
    guard r > 0.3 else { return }
    var g = ctx
    g.translateBy(x: c.x, y: c.y)
    g.rotate(by: .degrees(Double(rot)))
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
        let toes: [CGPoint] = [CGPoint(x: -0.62, y: -0.3), CGPoint(x: -0.22, y: -0.72), CGPoint(x: 0.22, y: -0.72), CGPoint(x: 0.62, y: -0.3)]
        for t in toes {
            g.fill(circle(CGPoint(x: t.x * r, y: t.y * r), r * 0.26), with: .color(color))
        }
    case .snow:
        g.stroke(snowPath(r), with: .color(color), style: StrokeStyle(lineWidth: r * 0.2, lineCap: .round))
    case .coin:
        g.fill(circle(o, r), with: .color(color))
        g.stroke(circle(o, r * 0.72), with: .color(Color(hex: 0xE08A00)), lineWidth: r * 0.16)
        g.fill(circle(CGPoint(x: -r * 0.35, y: -r * 0.35), r * 0.22), with: .color(shine))
    case .bone:
        g.fill(Path(CGRect(x: -r * 0.7, y: -r * 0.2, width: r * 1.4, height: r * 0.4)), with: .color(color))
        let ends: [CGPoint] = [CGPoint(x: -0.75, y: -0.22), CGPoint(x: -0.75, y: 0.22), CGPoint(x: 0.75, y: -0.22), CGPoint(x: 0.75, y: 0.22)]
        for e in ends {
            g.fill(circle(CGPoint(x: e.x * r, y: e.y * r), r * 0.27), with: .color(color))
        }
    case .leaf:
        var p = Path()
        p.move(to: CGPoint(x: 0, y: -r))
        p.addQuadCurve(to: CGPoint(x: 0, y: r), control: CGPoint(x: r * 0.95, y: 0))
        p.addQuadCurve(to: CGPoint(x: 0, y: -r), control: CGPoint(x: -r * 0.95, y: 0))
        g.fill(p, with: .color(color))
        var vein = Path()
        vein.move(to: CGPoint(x: 0, y: -r * 0.8))
        vein.addLine(to: CGPoint(x: 0, y: r * 0.8))
        g.stroke(vein, with: .color(.white.opacity(0.6)), lineWidth: r * 0.1)
    case .cloud:
        var p = circle(CGPoint(x: -r * 0.45, y: r * 0.12), r * 0.5)
        p.addPath(circle(CGPoint(x: 0, y: -r * 0.12), r * 0.62))
        p.addPath(circle(CGPoint(x: r * 0.48, y: r * 0.12), r * 0.48))
        p.addPath(Path(roundedRect: CGRect(x: -r * 0.9, y: 0, width: r * 1.8, height: r * 0.6), cornerRadius: r * 0.3))
        g.fill(p, with: .color(color))
    case .moon:
        let bite = circle(CGPoint(x: r * 0.45, y: -r * 0.25), r * 0.85)
        g.drawLayer { l in
            l.clip(to: bite, options: .inverse)
            l.fill(circle(o, r), with: .color(color))
        }
    case .seed:
        g.fill(Path(ellipseIn: CGRect(x: -r * 0.45, y: -r, width: r * 0.9, height: r * 2)), with: .color(color))
        var line = Path()
        line.move(to: CGPoint(x: 0, y: -r * 0.7))
        line.addLine(to: CGPoint(x: 0, y: r * 0.7))
        g.stroke(line, with: .color(.white.opacity(0.7)), style: StrokeStyle(lineWidth: r * 0.14, lineCap: .round))
    }
}

/// 파티클 하나의 무작위 값 (같은 seed면 같은 모양)
struct FxPart { let a: CGFloat; let s: CGFloat; let spin: CGFloat; let k: Int }

func fxParts(_ n: Int, seed: UInt64) -> [FxPart] {
    var g = SeededRandom(seed: seed)
    var out: [FxPart] = []
    for _ in 0..<n {
        let a = g.next(), s = g.next(), spin = g.next() * 2 - 1, k = Int(g.next() * 1000)
        out.append(FxPart(a: a, s: s, spin: spin, k: k))
    }
    return out
}

/// 화려한 폭발: 섬광 + 빛줄기 + 충격파 2겹 + 캐릭터 모양 파편 + 반짝이 가루. [p]는 0 → 1.
func drawBurst(_ ctx: inout GraphicsContext, _ c: CGPoint, _ radius: CGFloat, _ p: CGFloat, _ theme: FxTheme, _ ps: [FxPart], rays: Int = 12) {
    let fade: CGFloat = 1 - p
    // 섬광
    let fr: CGFloat = radius * (0.5 + p * 0.9)
    let flash = Gradient(colors: [.white.opacity(Double(fade * fade * 0.95)), theme.main.opacity(Double(fade * fade * 0.5)), .clear])
    ctx.fill(circle(c, fr), with: .radialGradient(flash, center: c, startRadius: 0, endRadius: fr))
    // 빛줄기
    if p < 0.6 {
        let rp: CGFloat = p / 0.6
        let width: CGFloat = (1 - rp) * radius * 0.09
        for i in 0..<rays {
            let ang: CGFloat = CGFloat(i) * tau / CGFloat(rays) + p * 0.6
            var ray = Path()
            ray.move(to: polar(c, ang, radius * (0.2 + rp * 0.5)))
            ray.addLine(to: polar(c, ang, radius * (0.6 + rp * 1.1)))
            ctx.stroke(ray, with: .color(theme.color(i).opacity(Double((1 - rp) * 0.9))),
                       style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }
    // 충격파 2겹
    ctx.stroke(circle(c, radius * (0.5 + p)), with: .color(theme.main.opacity(Double(fade * 0.85))), lineWidth: fade * radius * 0.06)
    let p2: CGFloat = clamp01((p - 0.12) / 0.88)
    if p2 > 0 {
        ctx.stroke(circle(c, radius * (0.4 + p2 * 1.4)), with: .color(.white.opacity(Double((1 - p2) * 0.9))), lineWidth: (1 - p2) * radius * 0.04)
    }
    // 파편 (캐릭터 모양)
    let ease: CGFloat = 1 - fade * fade
    let n = CGFloat(ps.count)
    for (i, q) in ps.enumerated() {
        let ang: CGFloat = (CGFloat(i) / n + q.a * 0.08) * tau
        let dist: CGFloat = radius * (0.25 + ease * (0.9 + q.s * 0.9))
        var pos = polar(c, ang, dist)
        pos.y += p * p * radius * 0.5
        let sz: CGFloat = radius * (0.06 + q.s * 0.06) * (1 - p * 0.6)
        drawFx(&ctx, theme.shape(q.k), pos, sz, theme.color(q.k).opacity(Double(1 - p * p)), rot: q.spin * 360 * p)
    }
    // 반짝이 가루 (깜빡임)
    for i in 0..<ps.count {
        let q = ps[(i * 7) % ps.count]
        let ang: CGFloat = (q.a + CGFloat(i) * 0.13) * tau
        let dist: CGFloat = radius * (0.3 + p * (0.5 + q.s * 1.3))
        let tw: CGFloat = 0.5 + 0.5 * fsin(p * 30 + CGFloat(i))
        ctx.fill(circle(polar(c, ang, dist), radius * 0.025 * (1 + q.s)), with: .color(.white.opacity(Double(fade * tw))))
    }
}

// ======================= 방울 안 효과 (터짐 · 선택) =======================

/// 방울이 터질 때 캐릭터별 화려한 효과
struct PopBurst: View {
    let side: Side
    let skin: Animal?
    let p: Double

    private static let parts = fxParts(30, seed: 11)

    private var textScale: CGFloat {
        let q = CGFloat(p)
        return q < 0.25 ? 0.4 + q / 0.25 * 0.9 : 1.3 - (q - 0.25) * 0.3
    }

    var body: some View {
        let theme = FxTheme.of(side, skin)
        let q = CGFloat(p)
        let fade: Double = min(1, (1 - p) * 1.4)
        let angle: Double = -8 + p * 10
        return ZStack {
            // 방울 밖으로도 튀어나가도록 그리는 영역을 넓힘
            Canvas { ctx, sz in
                let r: CGFloat = (min(sz.width, sz.height) - 120) / 2
                drawBurst(&ctx, CGPoint(x: sz.width / 2, y: sz.height / 2), r * 1.15, q, theme, Self.parts, rays: 14)
            }
            .padding(-60)
            .allowsHitTesting(false)
            Text(theme.emoji + " " + t("펑!"))
                .font(.system(size: 30, weight: .black))
                .foregroundColor(.white)
                .shadow(color: theme.main, radius: 8)
                .shadow(color: theme.main, radius: 3)
                .scaleEffect(textScale)
                .rotationEffect(.degrees(angle))
                .opacity(fade)
        }
    }
}

/// 고른 방울 주위를 빙글빙글 도는 반짝이
struct SelectedSparkles: View {
    let side: Side
    let skin: Animal?
    let time: Double

    private static func draw(_ ctx: inout GraphicsContext, _ sz: CGSize, _ theme: FxTheme, _ spin: CGFloat) {
        let r: CGFloat = (min(sz.width, sz.height) - 60) / 2
        let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
        let glow = Gradient(colors: [.clear, UiColor.gold.opacity(0.45), .clear])
        ctx.fill(circle(c, r * 1.3), with: .radialGradient(glow, center: c, startRadius: 0, endRadius: r * 1.3))
        for i in 0..<8 {
            let fi = CGFloat(i)
            let ang: CGFloat = (spin + fi / 8) * tau
            let rr: CGFloat = r * (1.08 + 0.06 * fsin(spin * 2 * tau + fi))
            let pos = polar(c, ang, rr)
            let tw: CGFloat = 0.55 + 0.45 * fsin(spin * 3 * tau + fi * 1.7)
            if i % 2 == 0 {
                drawFx(&ctx, theme.shape(i / 2), pos, r * 0.13 * tw, theme.color(i / 2), rot: spin * 360)
            } else {
                ctx.fill(sparklePath(pos, r * 0.16 * tw), with: .color(UiColor.gold))
            }
        }
    }

    var body: some View {
        let theme = FxTheme.of(side, skin)
        let spin = CGFloat(time.truncatingRemainder(dividingBy: 2.6) / 2.6)
        return Canvas { ctx, sz in Self.draw(&ctx, sz, theme, spin) }
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

    /// 날아가는 곡선의 조절점 (옆으로 휘어서 날아감)
    var ctrl: CGPoint {
        let dx: CGFloat = to.x - from.x, dy: CGFloat = to.y - from.y
        let k: CGFloat = from.x <= to.x ? 0.28 : -0.28
        return CGPoint(x: (from.x + to.x) / 2 - dy * k, y: (from.y + to.y) / 2 + dx * k)
    }

    /// 곡선 방향에 수직인 단위 벡터 (꼬리 흔들림용)
    var perp: CGPoint {
        let dx: CGFloat = to.x - from.x, dy: CGFloat = to.y - from.y
        let len: CGFloat = max(1, (dx * dx + dy * dy).squareRoot())
        return CGPoint(x: -dy / len, y: dx / len)
    }

    func at(_ t: CGFloat) -> CGPoint {
        let u: CGFloat = 1 - t
        let a: CGFloat = u * u, b: CGFloat = 2 * u * t, c: CGFloat = t * t
        let cp = ctrl
        return CGPoint(x: from.x * a + cp.x * b + to.x * c, y: from.y * a + cp.y * b + to.y * c)
    }

    var orb: CGFloat { CGFloat(34 + power * 4) }
}

/// 날아가는 방울 하나: 반짝이 꼬리를 남기며 휘어서 날아가 부딪힘
struct ShotFx: View {
    let shot: FlyingShot

    private static let trail = fxParts(22, seed: 5)

    /// 살짝 뒤로 당겼다가 쓩
    private static func flyEase(_ x: CGFloat) -> CGFloat {
        let s: CGFloat = 1.2
        let back: CGFloat = x * x * ((s + 1) * x - s)
        return back * 0.5 + x * 0.5
    }

    private static func drawFlying(_ ctx: inout GraphicsContext, _ shot: FlyingShot, _ theme: FxTheme, _ f: CGFloat) {
        let orb = shot.orb
        let perp = shot.perp
        let count = CGFloat(trail.count)
        for (k, q) in trail.enumerated() {
            let tk: CGFloat = f - CGFloat(k + 1) * 0.028
            if tk <= 0 { continue }
            let fade: CGFloat = 1 - CGFloat(k) / count
            let base = shot.at(tk)
            let w: CGFloat = fsin(tk * 22 + q.a * 6) * orb * 0.35 * q.s
            let pos = CGPoint(x: base.x + perp.x * w, y: base.y + perp.y * w)
            ctx.fill(circle(base, orb * 0.5 * fade), with: .color(theme.main.opacity(Double(fade * 0.25))))
            if k % 2 == 0 {
                drawFx(&ctx, theme.shape(q.k), pos, orb * 0.22 * fade, theme.color(q.k).opacity(Double(fade)), rot: q.spin * 300 * f)
            } else {
                ctx.fill(sparklePath(pos, orb * 0.2 * fade), with: .color(.white.opacity(Double(fade))))
            }
        }
        // 빛나는 방울
        let head = shot.at(f)
        let glow = Gradient(colors: [.white, theme.main.opacity(0.7), theme.main.opacity(0)])
        ctx.fill(circle(head, orb * 1.1), with: .radialGradient(glow, center: head, startRadius: 0, endRadius: orb * 1.1))
        ctx.stroke(circle(head, orb * 0.62), with: .color(.white.opacity(0.95)), lineWidth: orb * 0.07)
        ctx.fill(circle(CGPoint(x: head.x - orb * 0.28, y: head.y - orb * 0.3), orb * 0.12), with: .color(.white.opacity(0.9)))
    }

    private static func drawImpact(_ ctx: inout GraphicsContext, _ shot: FlyingShot, _ theme: FxTheme, _ h: CGFloat) {
        let ps = fxParts(14 + shot.power * 3 + (shot.boom ? 8 : 0), seed: UInt64(shot.power * 7 + 3))
        let radius: CGFloat = shot.orb * (1.5 + CGFloat(shot.power) * 0.08) * (shot.boom ? 1.2 : 1)
        drawBurst(&ctx, shot.to, radius, h, theme, ps, rays: shot.boom ? 18 : 12)
    }

    @ViewBuilder
    private func scene(_ el: Double) -> some View {
        let theme = FxTheme.of(shot.side, shot.skin)
        let flying = el < FlyingShot.flySec
        let f: CGFloat = clamp01(Self.flyEase(clamp01(CGFloat(el / FlyingShot.flySec))))
        let h: CGFloat = clamp01(CGFloat((el - FlyingShot.flySec) / FlyingShot.impactSec))
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                if flying {
                    Self.drawFlying(&ctx, shot, theme, f)
                } else {
                    Self.drawImpact(&ctx, shot, theme, h)
                }
            }
            if flying {
                critter(f)
            } else {
                plus(theme, h)
            }
        }
    }

    private func critter(_ f: CGFloat) -> some View {
        let spin: Double = Double(f) * 360 * (shot.attack ? 1 : -1)
        return Critter(side: shot.side, skin: shot.skin, happy: true)
            .frame(width: shot.orb, height: shot.orb)
            .scaleEffect(0.8 + f * 0.3)
            .rotationEffect(.degrees(spin))
            .position(shot.at(f))
    }

    /// 부딪힌 자리에 떠오르는 +N
    private func plus(_ theme: FxTheme, _ h: CGFloat) -> some View {
        let scale: CGFloat = h < 0.2 ? 0.5 + h * 4 : 1.3 - (h - 0.2) * 0.35
        let alpha: Double = h < 0.7 ? 1 : Double((1 - h) / 0.3)
        return Text("+\(shot.power)")
            .font(.system(size: 28, weight: .black))
            .foregroundColor(.white)
            .shadow(color: theme.main, radius: 8)
            .shadow(color: theme.main, radius: 3)
            .scaleEffect(scale)
            .opacity(alpha)
            .position(x: shot.to.x, y: shot.to.y - 36 - h * 48)
    }

    var body: some View {
        TimelineView(.animation) { tl in
            scene(tl.date.timeIntervalSince(shot.start))
        }
        .allowsHitTesting(false)
    }
}

/// 상대를 치면 화면이 살짝 흔들림 (animatableData를 1씩 올리면 한 번 흔들림)
struct ShakeEffect: GeometryEffect {
    var amount: CGFloat = 7
    var animatableData: CGFloat
    func effectValue(size: CGSize) -> ProjectionTransform {
        let k: CGFloat = 1 - (animatableData - animatableData.rounded(.down))
        let x: CGFloat = fsin(animatableData * tau * 4) * amount * k
        let y: CGFloat = fcos(animatableData * tau * 3) * amount * 0.5 * k
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

    var body: some View {
        TimelineView(.animation) { tl in
            CheckinFrame(day: day, coins: coins, potion: potion, onClose: onClose,
                         el: tl.date.timeIntervalSince(startedAt), now: tl.date.timeIntervalSinceReferenceDate, pop: pop)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClose)
        .onAppear {
            startedAt = Date()
            withAnimation(.spring(response: 0.55, dampingFraction: 0.5)) { pop = true }
        }
    }
}

/// 출석 팝업의 한 장면 (el: 시작 후 지난 초)
private struct CheckinFrame: View {
    let day: Int
    let coins: Int
    let potion: Bool
    let onClose: () -> Void
    let el: Double
    let now: Double
    let pop: Bool

    private static let confetti = fxParts(70, seed: 21)
    private static let burstParts = fxParts(34, seed: 31)
    private static let confettiColors: [Color] = [UiColor.primary, UiColor.gold, UiColor.accent, Color(hex: 0x5CD6B4), Color(hex: 0x6EC1FF), Color(hex: 0xFF8A65), .white]
    private static let burstTheme = FxTheme(colors: [UiColor.gold, UiColor.primary, .white, UiColor.accent], shapes: [.star, .heart, .coin], emoji: "🎉")

    private var stamp: CGFloat { let s = clamp01(CGFloat((el - 0.38) / 0.32)); return s * s }
    private var burstP: CGFloat { clamp01(CGFloat((el - 0.7) / 1.1)) }
    private var countEase: CGFloat { let c = clamp01(CGFloat((el - 0.65) / 0.9)); return 1 - (1 - c) * (1 - c) * (1 - c) }

    /// 회전하는 빛줄기 + 떨어지는 색종이
    private static func drawBackdrop(_ ctx: inout GraphicsContext, _ size: CGSize, _ now: Double, _ shine: CGFloat) {
        let c = CGPoint(x: size.width / 2, y: size.height * 0.42)
        let big: CGFloat = max(size.width, size.height)
        let spin = CGFloat(now.truncatingRemainder(dividingBy: 9) / 9)
        let rayGrad = Gradient(colors: [UiColor.gold.opacity(Double(0.32 * shine)), .clear])
        for i in 0..<16 {
            let a0: CGFloat = (spin + CGFloat(i) / 16) * tau
            var p = Path()
            p.move(to: c)
            p.addLine(to: polar(c, a0, big))
            p.addLine(to: polar(c, a0 + .pi / 22, big))
            p.closeSubpath()
            ctx.fill(p, with: .radialGradient(rayGrad, center: c, startRadius: 0, endRadius: big * 0.6))
        }
        let fall = CGFloat(now.truncatingRemainder(dividingBy: 3.6) / 3.6)
        for (i, q) in confetti.enumerated() {
            let yy: CGFloat = (fall + q.a).truncatingRemainder(dividingBy: 1) * (size.height + 80) - 40
            let xx: CGFloat = q.s * size.width + fsin(fall * tau * (1 + q.spin) + CGFloat(i)) * 30
            let pt = CGPoint(x: xx, y: yy)
            let color = confettiColors[q.k % confettiColors.count]
            let sz: CGFloat = 5 + q.s * 4
            switch q.k % 3 {
            case 0:
                var g = ctx
                g.translateBy(x: xx, y: yy)
                g.rotate(by: .degrees(Double(fall * 720 * q.spin) + Double(q.k)))
                g.fill(Path(CGRect(x: -sz, y: -sz * 0.45, width: sz * 2, height: sz * 0.9)), with: .color(color))
            case 1:
                ctx.fill(circle(pt, sz * 0.6), with: .color(color))
            default:
                ctx.fill(sparklePath(pt, sz), with: .color(color))
            }
        }
    }

    /// 도장 뒤 폭죽 + 둘레 반짝이
    private static func drawStampFx(_ ctx: inout GraphicsContext, _ sz: CGSize, _ burstP: CGFloat, _ now: Double) {
        let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
        let m: CGFloat = min(sz.width, sz.height)
        if burstP > 0 && burstP < 1 {
            drawBurst(&ctx, c, m / 2 * 0.75, burstP, burstTheme, burstParts, rays: 16)
        }
        let tw0 = CGFloat(now.truncatingRemainder(dividingBy: 1.6) / 1.6)
        let colors: [Color] = [UiColor.gold, UiColor.primary, UiColor.accent]
        for i in 0..<6 {
            let fi = CGFloat(i)
            let tw: CGFloat = 0.5 + 0.5 * fsin(tw0 * 2 * tau + fi * 1.3)
            ctx.fill(sparklePath(polar(c, (tw0 + fi / 6) * tau, m * 0.36), 9 * tw), with: .color(colors[i % 3]))
        }
    }

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(hex: 0x3A1F5C, alpha: 0.8), Color(hex: 0x140B22, alpha: 0.93)], center: .center, startRadius: 0, endRadius: 600)
                .ignoresSafeArea()
            Canvas { ctx, size in Self.drawBackdrop(&ctx, size, now, pop ? 1 : 0) }
                .ignoresSafeArea()
                .allowsHitTesting(false)
            card
                .scaleEffect(pop ? 1 : 0.6)
                .opacity(pop ? 1 : 0)
        }
    }

    private var card: some View {
        VStack(spacing: 0) {
            stampView
            title
            dayRow.padding(.top, 14)
            rewards.padding(.top, 16)
            hint.padding(.top, 10)
            okButton.padding(.top, 16)
        }
        .padding(.horizontal, 22).padding(.vertical, 24)
        .background(RoundedRectangle(cornerRadius: 30).fill(LinearGradient(colors: [Color(hex: 0xFFF7FB), Color(hex: 0xF1ECFF)], startPoint: .top, endPoint: .bottom)))
        .overlay(RoundedRectangle(cornerRadius: 30).stroke(AngularGradient(colors: [UiColor.primary, UiColor.gold, UiColor.accent, Color(hex: 0x6EC1FF), UiColor.primary], center: .center), lineWidth: 3))
        .contentShape(Rectangle())
        .onTapGesture {}
        .padding(28)
    }

    /// 도장 쾅!
    private var stampView: some View {
        let s = stamp
        let b = burstP
        let t0 = now
        let scale: CGFloat = 2.6 - 1.6 * s
        let angle: Double = Double(-14 * s)
        let alpha: Double = Double(min(1, s * 2))
        let dayText = tr("\(day)일째", "DAY \(day)", zh: "第\(day)天", es: "DÍA \(day)", fr: "JOUR \(day)", de: "TAG \(day)",
                         pt: "DIA \(day)", ru: "ДЕНЬ \(day)", ja: "\(day)日目")
        return ZStack {
            Canvas { ctx, sz in Self.drawStampFx(&ctx, sz, b, t0) }
                .frame(width: 220, height: 220)
                .allowsHitTesting(false)
            ZStack {
                Circle().fill(RadialGradient(colors: [UiColor.primary, UiColor.primaryDark], center: .center, startRadius: 0, endRadius: 54))
                Circle().stroke(Color.white, lineWidth: 4)
                VStack(spacing: 0) {
                    Text(dayText).font(.system(size: 20, weight: .black)).foregroundColor(.white)
                    Image(systemName: "checkmark").font(.system(size: 30, weight: .black)).foregroundColor(.white)
                }
            }
            .frame(width: 108, height: 108)
            .scaleEffect(scale)
            .rotationEffect(.degrees(angle))
            .opacity(alpha)
        }
        .frame(width: 130, height: 130)
    }

    private var title: some View {
        Text(tr("출석 완료!", "Checked in!", zh: "签到成功！", es: "¡Asistencia lista!", fr: "Présence validée !", de: "Eingecheckt!",
                pt: "Presença feita!", ru: "Вход отмечен!", ja: "出席完了！"))
            .font(.system(size: 30, weight: .black))
            .foregroundStyle(LinearGradient(colors: [UiColor.primaryDark, UiColor.accent], startPoint: .leading, endPoint: .trailing))
            .shadow(color: UiColor.gold.opacity(0.6), radius: 5, y: 3)
            .padding(.top, 10)
    }

    /// 7일 진행 동그라미
    private var dayRow: some View {
        let todayScale: CGFloat = min(stamp * 1.25, 1 + 0.25 * (1 - burstP))
        return HStack(spacing: 5) {
            ForEach(1...7, id: \.self) { d in
                dayDot(d).scaleEffect(d == day ? todayScale : 1)
            }
        }
    }

    private func dayDot(_ d: Int) -> some View {
        let done = d <= day
        let today = d == day
        let colors: [Color] = today ? [UiColor.gold, Color(hex: 0xFF9F1C)]
            : done ? [UiColor.primary, UiColor.primaryDark]
            : [Color.black.opacity(0.12), Color.black.opacity(0.09)]
        let label = done ? "✓" : (d == 7 ? "💖" : "\(d)")
        return ZStack {
            Circle().fill(LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom))
            if today { Circle().stroke(Color.white, lineWidth: 2) }
            Text(label)
                .font(.system(size: done ? 16 : 12, weight: .black))
                .foregroundColor(done ? .white : UiColor.text.opacity(0.5))
        }
        .frame(width: 32, height: 32)
    }

    private var rewards: some View {
        let shown = Int((CGFloat(coins) * countEase).rounded())
        let potionScale: CGFloat = 0.6 + 0.4 * countEase
        return HStack(spacing: 10) {
            rewardChip("💰", "+\(shown)", UiColor.gold)
            if potion { rewardChip("💖", "+1", Color(hex: 0xFF5C8A)).scaleEffect(potionScale) }
        }
    }

    private var hint: some View {
        let next = day % 7 + 1
        let nc = OnlineService.checkinCoins[next - 1]
        let text = day == 7
            ? tr("🎉 7일 개근! 내일은 1일째부터 다시 시작해요", "🎉 7-day streak! Tomorrow starts again at day 1", zh: "🎉 连续 7 天！明天从第 1 天重新开始",
                 es: "🎉 ¡7 días seguidos! Mañana vuelves al día 1", fr: "🎉 7 jours d’affilée ! Demain, retour au jour 1",
                 de: "🎉 7 Tage am Stück! Morgen geht’s bei Tag 1 weiter", pt: "🎉 7 dias seguidos! Amanhã recomeça no dia 1",
                 ru: "🎉 7 дней подряд! Завтра снова с 1-го дня", ja: "🎉 7日皆勤！明日は1日目から")
            : tr("내일도 오면 💰 +\(nc)", "Come back tomorrow for 💰 +\(nc)", zh: "明天再来可得 💰 +\(nc)",
                 es: "Vuelve mañana por 💰 +\(nc)", fr: "Reviens demain pour 💰 +\(nc)", de: "Morgen wieder: 💰 +\(nc)",
                 pt: "Volte amanhã por 💰 +\(nc)", ru: "Завтра: 💰 +\(nc)", ja: "明日も来ると 💰 +\(nc)")
        return Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(UiColor.text.opacity(0.7))
            .multilineTextAlignment(.center)
    }

    private var okButton: some View {
        Button(action: onClose) {
            Text(tr("좋아요! 💕", "Yay! 💕", zh: "太好了！💕", es: "¡Genial! 💕", fr: "Super ! 💕", de: "Juhu! 💕", pt: "Oba! 💕", ru: "Ура! 💕", ja: "やった！💕"))
                .font(.system(size: 18, weight: .black))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Capsule().fill(LinearGradient(colors: [UiColor.primary, UiColor.accent], startPoint: .leading, endPoint: .trailing)))
        }
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
