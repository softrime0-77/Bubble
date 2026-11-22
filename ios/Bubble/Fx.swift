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
enum FxShape { case star, heart, paw, snow, coin, bone, leaf, cloud, moon, seed, ink, flame }

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
        case .octopus:
            return FxTheme(colors: [Color(hex: 0x2E1F4D), Color(hex: 0xB57BEE), Color(hex: 0x8E52D6), .white], shapes: [.ink, .star], emoji: "🐙")
        case .penguin:
            return FxTheme(colors: [Color(hex: 0x7FD3FF), .white, Color(hex: 0x3A4A68), Color(hex: 0xBFE9FF)], shapes: [.snow, .star], emoji: "🐧")
        case .panda:
            return FxTheme(colors: [Color(hex: 0x6CCB5F), .white, Color(hex: 0x3B3B45), Color(hex: 0xB8F0A8)], shapes: [.leaf, .heart], emoji: "🐼")
        case .dragon:
            return FxTheme(colors: [Color(hex: 0xFF6A2B), Color(hex: 0xFFC21A), Color(hex: 0xFF2D55), .white], shapes: [.flame, .star], emoji: "🔥")
        case .rabbit:
            return FxTheme(colors: [Color(hex: 0xFF8FB8), .white, Color(hex: 0xFFC94D), Color(hex: 0x7C6CF2)], shapes: [.heart, .star], emoji: "🐰")
        case .unicorn:
            return FxTheme(colors: [Color(hex: 0xFF7EB3), Color(hex: 0xFFE066), Color(hex: 0x7FC8FF), Color(hex: 0xB98CFF), Color(hex: 0x7DDB9B), .white], shapes: [.star, .heart], emoji: "🌈")
        case .luckyCat:
            return FxTheme(colors: [Color(hex: 0xFFC21A), Color(hex: 0xE5303F), .white, Color(hex: 0xFFE680)], shapes: [.coin, .star, .heart], emoji: "💰")
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
    case .ink:
        var p = circle(o, r * 0.75)
        p.addPath(circle(CGPoint(x: r * 0.7, y: -r * 0.35), r * 0.4))
        p.addPath(circle(CGPoint(x: -r * 0.65, y: r * 0.45), r * 0.32))
        p.addPath(circle(CGPoint(x: r * 0.2, y: r * 0.95), r * 0.2))
        g.fill(p, with: .color(color))
        g.fill(circle(CGPoint(x: -r * 0.25, y: -r * 0.3), r * 0.2), with: .color(.white.opacity(0.35)))
    case .flame:
        var p = Path()
        p.move(to: CGPoint(x: 0, y: -r * 1.1))
        p.addCurve(to: CGPoint(x: 0, y: r * 0.9), control1: CGPoint(x: r * 0.9, y: -r * 0.2), control2: CGPoint(x: r * 0.8, y: r * 0.9))
        p.addCurve(to: CGPoint(x: 0, y: -r * 1.1), control1: CGPoint(x: -r * 0.8, y: r * 0.9), control2: CGPoint(x: -r * 0.9, y: -r * 0.2))
        g.fill(p, with: .color(color))
        g.fill(Path(ellipseIn: CGRect(x: -r * 0.32, y: 0, width: r * 0.64, height: r * 0.8)), with: .color(Color(hex: 0xFFF4B0)))
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
    /// 분할: 가위로 싹둑 자른 절반이 옆 방울로 날아감
    var split: Bool = false
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
        let raw: CGFloat = clamp01(CGFloat(el / FlyingShot.flySec))
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                if shot.split && flying { Self.drawSlash(&ctx, shot, theme, raw) }
                if flying {
                    Self.drawFlying(&ctx, shot, theme, f)
                } else {
                    Self.drawImpact(&ctx, shot, theme, h)
                }
            }
            if flying {
                if shot.split && raw < 0.6 { scissors(raw / 0.6) }
                critter(f)
            } else {
                plus(theme, h)
            }
        }
    }

    /// ✂️ 분할: 원래 방울을 대각선으로 싹둑 (빛나는 칼자국 + 반짝이 폭발)
    private static func drawSlash(_ ctx: inout GraphicsContext, _ shot: FlyingShot, _ theme: FxTheme, _ raw: CGFloat) {
        let orb = shot.orb
        let slashT: CGFloat = clamp01(raw / 0.3)
        let fade: CGFloat = raw < 0.55 ? 1 : clamp01(1 - (raw - 0.55) / 0.45)
        let len: CGFloat = orb * 2.2
        let a0 = CGPoint(x: shot.from.x - 0.75 * len, y: shot.from.y + 0.66 * len)
        let a1 = CGPoint(x: a0.x + 0.75 * len * 2 * slashT, y: a0.y - 0.66 * len * 2 * slashT)
        var line = Path()
        line.move(to: a0)
        line.addLine(to: a1)
        ctx.stroke(line, with: .color(theme.main.opacity(Double(0.55 * fade))), style: StrokeStyle(lineWidth: orb * 0.45, lineCap: .round))
        ctx.stroke(line, with: .color(.white.opacity(Double(fade))), style: StrokeStyle(lineWidth: orb * 0.16, lineCap: .round))
        ctx.fill(sparklePath(a1, orb * 0.55 * fade), with: .color(.white.opacity(Double(fade))))
        let sp: CGFloat = clamp01(raw / 0.55)
        if sp > 0.3 {
            drawBurst(&ctx, shot.from, orb * 1.6, clamp01((sp - 0.3) / 0.7), theme, fxParts(18, seed: 81), rays: 10)
        }
    }

    /// ✂️ 가위가 휙 지나감 ([k]: 0 → 1)
    private func scissors(_ k: CGFloat) -> some View {
        let travel: CGFloat = shot.orb * 2.2
        let x: CGFloat = shot.from.x - travel * 0.75 + travel * 1.5 * k
        let y: CGFloat = shot.from.y + travel * 0.66 - travel * 1.32 * k - 30
        let angle: Double = Double(-40 + 30 * fsin(k * 12))
        let scale: CGFloat = 0.6 + 0.8 * fsin(k * .pi)
        let alpha: Double = k < 0.8 ? 1 : Double((1 - k) / 0.2)
        return Text("✂️").font(.system(size: 44))
            .rotationEffect(.degrees(angle))
            .scaleEffect(scale)
            .opacity(alpha)
            .position(x: x, y: y)
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
        return Text(shot.split ? "✂️ +\(shot.power)" : "+\(shot.power)")
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
    var panda: Int = 0
    let onClose: () -> Void
    @State private var startedAt = Date()
    @State private var pop = false

    var body: some View {
        TimelineView(.animation) { tl in
            CheckinFrame(day: day, coins: coins, potion: potion, panda: panda, onClose: onClose,
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
    let panda: Int
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
    fileprivate static func drawBackdrop(_ ctx: inout GraphicsContext, _ size: CGSize, _ now: Double, _ shine: CGFloat) {
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
            if panda > 0 {
                // 🐼 출석 판다 보너스
                rewardChip("🐼", tr("판다 보너스 💰 +\(panda)", "Panda bonus 💰 +\(panda)", zh: "熊猫奖励 💰 +\(panda)", es: "Bono panda 💰 +\(panda)",
                                   fr: "Bonus panda 💰 +\(panda)", de: "Panda-Bonus 💰 +\(panda)", pt: "Bônus panda 💰 +\(panda)", ru: "Бонус панды 💰 +\(panda)",
                                   ja: "パンダボーナス 💰 +\(panda)"), Color(hex: 0x6CCB5F))
                    .scaleEffect(0.6 + 0.4 * countEase)
                    .padding(.top, 8)
            }
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

// ======================= 미션 완료 팝업 =======================

/// 미션 보상 받기! 과녁이 빙글 돌며 튀어나오고 팡 터짐 · 💰 숫자 올라가기
struct MissionCelebration: View {
    let label: String
    let coins: Int
    let onClose: () -> Void
    @State private var startedAt = Date()
    @State private var pop = false

    var body: some View {
        TimelineView(.animation) { tl in
            MissionFrame(label: label, coins: coins, onClose: onClose,
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

private struct MissionFrame: View {
    let label: String
    let coins: Int
    let onClose: () -> Void
    let el: Double
    let now: Double
    let pop: Bool

    private static let burstParts = fxParts(30, seed: 41)
    private static let burstTheme = FxTheme(colors: [UiColor.gold, Color(hex: 0x5CD6B4), .white, UiColor.primary], shapes: [.coin, .star], emoji: "🎯")
    private static let mint = Color(hex: 0x5CD6B4)

    /// 과녁: 0.25초 뒤 통통 튀며 한 바퀴 돌고 나타남
    private var badge: CGFloat {
        let x = clamp01(CGFloat((el - 0.25) / 0.6))
        // 살짝 넘쳤다가 돌아오는 느낌
        let over: CGFloat = fsin(x * .pi) * 0.25 * (1 - x)
        return x + over
    }
    private var burstP: CGFloat { clamp01(CGFloat((el - 0.42) / 1.1)) }
    private var countEase: CGFloat { let c = clamp01(CGFloat((el - 0.5) / 0.8)); return 1 - (1 - c) * (1 - c) * (1 - c) }

    private static func drawBadgeFx(_ ctx: inout GraphicsContext, _ sz: CGSize, _ burstP: CGFloat, _ now: Double) {
        let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
        let m: CGFloat = min(sz.width, sz.height)
        if burstP > 0 && burstP < 1 {
            drawBurst(&ctx, c, m / 2 * 0.75, burstP, burstTheme, burstParts, rays: 16)
        }
        let tw0 = CGFloat(now.truncatingRemainder(dividingBy: 1.6) / 1.6)
        let colors: [Color] = [UiColor.gold, mint, UiColor.accent]
        for i in 0..<6 {
            let fi = CGFloat(i)
            let tw: CGFloat = 0.5 + 0.5 * fsin(tw0 * 2 * tau + fi * 1.3)
            ctx.fill(sparklePath(polar(c, (tw0 + fi / 6) * tau, m * 0.36), 9 * tw), with: .color(colors[i % 3]))
        }
    }

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(hex: 0x1F3A5C, alpha: 0.8), Color(hex: 0x0B1622, alpha: 0.93)], center: .center, startRadius: 0, endRadius: 600)
                .ignoresSafeArea()
            Canvas { ctx, size in CheckinFrame.drawBackdrop(&ctx, size, now, pop ? 1 : 0) }
                .ignoresSafeArea()
                .allowsHitTesting(false)
            card
                .scaleEffect(pop ? 1 : 0.6)
                .opacity(pop ? 1 : 0)
        }
    }

    private var card: some View {
        VStack(spacing: 0) {
            badgeView
            title
            Text("✅ " + label)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(UiColor.text.opacity(0.8))
                .padding(.top, 6)
            coinChip.padding(.top, 16)
            Text(tr("상점에서 쓸 수 있는 💰 포인트가 들어왔어요", "💰 shop points added — spend them in the shop", zh: "💰 商店积分已到账", es: "💰 Puntos de tienda añadidos",
                    fr: "💰 Points boutique ajoutés", de: "💰 Shop-Punkte gutgeschrieben", pt: "💰 Pontos da loja adicionados", ru: "💰 Очки магазина начислены",
                    ja: "💰 ショップポイントが入りました"))
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(UiColor.text.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            okButton.padding(.top, 16)
        }
        .padding(.horizontal, 22).padding(.vertical, 24)
        .background(RoundedRectangle(cornerRadius: 30).fill(LinearGradient(colors: [Color(hex: 0xF4FFFB), Color(hex: 0xEFF3FF)], startPoint: .top, endPoint: .bottom)))
        .overlay(RoundedRectangle(cornerRadius: 30).stroke(AngularGradient(colors: [Self.mint, UiColor.gold, UiColor.accent, Color(hex: 0x6EC1FF), Self.mint], center: .center), lineWidth: 3))
        .contentShape(Rectangle())
        .onTapGesture {}
        .padding(28)
    }

    private var badgeView: some View {
        let b = badge
        let bp = burstP
        let t0 = now
        let spin: Double = Double((1 - min(1, b)) * -360)
        return ZStack {
            Canvas { ctx, sz in Self.drawBadgeFx(&ctx, sz, bp, t0) }
                .frame(width: 220, height: 220)
                .allowsHitTesting(false)
            ZStack {
                Circle().fill(RadialGradient(colors: [Color(hex: 0x7BE3C4), Color(hex: 0x2FB58F)], center: .center, startRadius: 0, endRadius: 54))
                Circle().stroke(Color.white, lineWidth: 4)
                Text("🎯").font(.system(size: 54))
            }
            .frame(width: 108, height: 108)
            .scaleEffect(max(0.01, b))
            .rotationEffect(.degrees(spin))
        }
        .frame(width: 130, height: 130)
    }

    private var title: some View {
        Text(tr("미션 완료!", "Mission complete!", zh: "任务完成！", es: "¡Misión cumplida!", fr: "Mission réussie !", de: "Mission geschafft!",
                pt: "Missão cumprida!", ru: "Задание выполнено!", ja: "ミッション達成！"))
            .font(.system(size: 30, weight: .black))
            .foregroundStyle(LinearGradient(colors: [Color(hex: 0x1F9E7A), UiColor.accent], startPoint: .leading, endPoint: .trailing))
            .shadow(color: UiColor.gold.opacity(0.6), radius: 5, y: 3)
            .padding(.top, 10)
    }

    private var coinChip: some View {
        let shown = Int((CGFloat(coins) * countEase).rounded())
        let scale: CGFloat = 0.85 + 0.15 * countEase
        return HStack(spacing: 6) {
            Text("💰").font(.system(size: 24))
            Text("+\(shown)").font(.system(size: 24, weight: .black)).foregroundColor(UiColor.text).monospacedDigit()
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
        .background(Capsule().fill(UiColor.gold.opacity(0.18)))
        .overlay(Capsule().stroke(UiColor.gold.opacity(0.7), lineWidth: 2))
        .scaleEffect(scale)
    }

    private var okButton: some View {
        Button(action: onClose) {
            Text(tr("좋아요! 💕", "Yay! 💕", zh: "太好了！💕", es: "¡Genial! 💕", fr: "Super ! 💕", de: "Juhu! 💕", pt: "Oba! 💕", ru: "Ура! 💕", ja: "やった！💕"))
                .font(.system(size: 18, weight: .black))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Capsule().fill(LinearGradient(colors: [Color(hex: 0x3CC9A0), UiColor.accent], startPoint: .leading, endPoint: .trailing)))
        }
    }
}

// ======================= 💰 복냥이 동전 =======================

/// 복냥이 효과로 튀어나오는 동전 (좌표는 게임 화면 기준)
struct CoinPop: Identifiable, Equatable {
    let id = UUID()
    let at: CGPoint
    var amount: Int = 1
    let start = Date()
    static let sec = 1.7
}

/// 금화 하나 ([spin]: 1이면 정면, 0에 가까우면 옆면)
private func drawGoldCoin(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ spin: CGFloat, _ alpha: Double) {
    let w: CGFloat = r * min(1, max(0.12, spin))
    let edge = CGRect(x: c.x - w + r * 0.08, y: c.y - r + r * 0.06, width: w * 2, height: r * 2)
    ctx.fill(Path(ellipseIn: edge), with: .color(Color(hex: 0xC77800).opacity(alpha)))
    let face = CGRect(x: c.x - w, y: c.y - r, width: w * 2, height: r * 2)
    let grad = Gradient(colors: [Color(hex: 0xFFF3A0).opacity(alpha), Color(hex: 0xFFC21A).opacity(alpha), Color(hex: 0xE59A00).opacity(alpha)])
    ctx.fill(Path(ellipseIn: face), with: .linearGradient(grad, startPoint: CGPoint(x: face.minX, y: face.minY), endPoint: CGPoint(x: face.maxX, y: face.maxY)))
    let rim = CGRect(x: c.x - w * 0.72, y: c.y - r * 0.72, width: w * 1.44, height: r * 1.44)
    ctx.stroke(Path(ellipseIn: rim), with: .color(Color(hex: 0xFFF7C2).opacity(alpha * 0.9)), lineWidth: r * 0.09)
    if spin > 0.45 {
        ctx.fill(sparklePath(c, r * 0.42 * spin), with: .color(Color(hex: 0xFFFBE6).opacity(alpha)))
    }
}

/// 💰 복냥이 동전 터짐: 금빛 햇살 · 통 튀어 오르는 큰 금화 · 분수처럼 흩어지는 작은 금화 · 반짝이 가루 · "💰 +1"
struct CoinPopFx: View {
    let pop: CoinPop

    private static let theme = FxTheme(colors: [Color(hex: 0xFFC21A), Color(hex: 0xFFE680), .white, Color(hex: 0xFF8A3D)], shapes: [.star, .coin], emoji: "💰")
    private static let parts = fxParts(22, seed: 51)
    private static let dust = fxParts(26, seed: 61)
    /// 분수 금화: (방향, 세기, 회전 속도)
    private static let fountain: [(CGFloat, CGFloat, CGFloat)] = {
        var g = SeededRandom(seed: 71)
        return (0..<9).map { i in
            let dir: CGFloat = -.pi / 2 + CGFloat(i - 4) * 0.33 + (g.next() - 0.5) * 0.2
            return (dir, 0.75 + g.next() * 0.5, 10 + g.next() * 14)
        }
    }()
    private static let coinR: CGFloat = 20

    private static func headY(_ at: CGPoint, _ t: CGFloat) -> CGFloat {
        let up = min(1, t / 0.8)
        return at.y - 110 * (1 - (1 - up) * (1 - up)) + 20 * t * t
    }

    private static func draw(_ ctx: inout GraphicsContext, _ at: CGPoint, _ t: CGFloat) {
        let coinR = Self.coinR
        let head = CGPoint(x: at.x, y: headY(at, t))
        // 1) 맞은 자리 금빛 폭발
        if t < 0.6 { drawBurst(&ctx, at, coinR * 3.4, t / 0.6, theme, parts, rays: 14) }
        // 2) 큰 금화 뒤 햇살
        let glowA: CGFloat = t < 0.15 ? t / 0.15 : (t > 0.75 ? (1 - t) / 0.25 : 1)
        for i in 0..<12 {
            let a0: CGFloat = t * 2.2 + CGFloat(i) * tau / 12
            let len: CGFloat = coinR * (3.2 + 0.4 * fsin(t * 20 + CGFloat(i)))
            var ray = Path()
            ray.move(to: head)
            ray.addLine(to: polar(head, a0, len))
            ray.addLine(to: polar(head, a0 + .pi / 18, len))
            ray.closeSubpath()
            let g = Gradient(colors: [Color(hex: 0xFFE680).opacity(Double(0.75 * glowA)), .clear])
            ctx.fill(ray, with: .radialGradient(g, center: head, startRadius: 0, endRadius: len))
        }
        let haloR: CGFloat = coinR * 2.6
        let halo = Gradient(colors: [Color(hex: 0xFFF8D0).opacity(Double(0.95 * glowA)), Color(hex: 0xFFC21A).opacity(Double(0.35 * glowA)), .clear])
        ctx.fill(circle(head, haloR), with: .radialGradient(halo, center: head, startRadius: 0, endRadius: haloR))
        // 3) 분수 금화
        let ft: CGFloat = clamp01((t - 0.05) / 0.9)
        if ft > 0 {
            for (i, f) in fountain.enumerated() {
                let v: CGFloat = 210 * f.1
                let x: CGFloat = at.x + fcos(f.0) * v * ft
                let y: CGFloat = at.y + fsin(f.0) * v * ft + 260 * ft * ft
                let a: CGFloat = ft < 0.7 ? 1 : (1 - ft) / 0.3
                let spin: CGFloat = abs(fcos(ft * f.2 + CGFloat(i)))
                drawGoldCoin(&ctx, CGPoint(x: x, y: y), coinR * 0.45, spin, Double(a))
                if i % 2 == 0 {
                    ctx.fill(sparklePath(CGPoint(x: x, y: y - coinR * 0.6), coinR * 0.3 * a), with: .color(.white.opacity(Double(a))))
                }
            }
        }
        // 4) 반짝이 가루
        for (i, q) in dust.enumerated() {
            let tw: CGFloat = 0.5 + 0.5 * fsin(t * 40 + CGFloat(i))
            let a: CGFloat = (1 - t) * tw
            let pos = CGPoint(x: at.x + (q.a - 0.5) * 180 * t, y: at.y - (40 + q.s * 160) * t)
            if i % 3 == 0 {
                ctx.fill(sparklePath(pos, (4 + q.s * 5) * a), with: .color(Color(hex: 0xFFE680).opacity(Double(a))))
            } else {
                ctx.fill(circle(pos, 1.5 + q.s * 2), with: .color(.white.opacity(Double(a))))
            }
        }
        // 5) 큰 금화 + 둘레 반짝이
        if t < 0.9 {
            let a: CGFloat = t < 0.8 ? 1 : (0.9 - t) / 0.1
            let spin: CGFloat = abs(fcos(t * 16))
            let grow: CGFloat = t < 0.1 ? 0.4 + t / 0.1 * 0.8 : 1.2 - (t - 0.1) * 0.25
            drawGoldCoin(&ctx, head, coinR * grow, spin, Double(a))
            for k in 0..<4 {
                let ang: CGFloat = t * 9 + CGFloat(k) * .pi / 2
                let sz: CGFloat = coinR * 0.38 * a * (0.6 + 0.4 * fsin(t * 30 + CGFloat(k)))
                ctx.fill(sparklePath(polar(head, ang, coinR * 1.8), sz), with: .color(.white.opacity(Double(a))))
            }
        }
    }

    private func label(_ t: CGFloat) -> some View {
        let tt: CGFloat = clamp01((t - 0.08) / 0.92)
        let scale: CGFloat = tt < 0.12 ? 0.3 + tt / 0.12 * 1.1 : 1.4 - (tt - 0.12) * 0.35
        let alpha: Double = tt < 0.7 ? 1 : Double(clamp01((1 - tt) / 0.3))
        let y: CGFloat = Self.headY(pop.at, t) - 46 - tt * 24
        return Text("💰 +\(pop.amount)")
            .font(.system(size: 28, weight: .black))
            .foregroundStyle(LinearGradient(colors: [Color(hex: 0xFFFBE0), Color(hex: 0xFFD43B), Color(hex: 0xFF9F1C)], startPoint: .top, endPoint: .bottom))
            .shadow(color: Color(hex: 0x9A4A00), radius: 5, y: 3)
            .scaleEffect(scale)
            .rotationEffect(.degrees(Double(-6 + 6 * tt)))
            .opacity(alpha)
            .position(x: pop.at.x, y: y)
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let t = clamp01(CGFloat(tl.date.timeIntervalSince(pop.start) / CoinPop.sec))
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in Self.draw(&ctx, pop.at, t) }
                label(t)
            }
        }
        .allowsHitTesting(false)
    }
}


// ======================= 💖 부활 =======================

/// 부활 효과 하나 (좌표는 게임 화면 기준)
struct ReviveBurst: Identifiable, Equatable {
    let id = UUID()
    let at: CGPoint
    let start = Date()
    static let sec = 1.8
    /// 물약이 떨어져 깨지기까지 (이때 살아난 방울이 보임)
    static let dropSec = 0.54
}

/// 💖 부활물약: 빛기둥이 내려오고, 물약이 떨어져 톡 깨지며 분홍 폭발 · 천사 고리 · 하트가 솟아오르고 "💖 부활!"
struct ReviveFx: View {
    let burst: ReviveBurst

    private static let pink = Color(hex: 0xFF5C8A)
    private static let theme = FxTheme(colors: [pink, Color(hex: 0xFFB3D1), .white, UiColor.gold], shapes: [.heart, .star], emoji: "💖")
    private static let parts = fxParts(28, seed: 91)
    private static let hearts = fxParts(14, seed: 101)

    private static func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ at: CGPoint, _ t: CGFloat) {
        let dropT: CGFloat = clamp01(t / 0.3)
        let after: CGFloat = clamp01((t - 0.3) / 0.7)
        // 빛기둥 (아래 끝은 방울 안에서 부드럽게 사라짐)
        let beamA: CGFloat = t < 0.15 ? t / 0.15 : 1 - clamp01((t - 0.5) / 0.5)
        let beamW: CGFloat = 90 * (0.6 + 0.4 * fsin(t * 14))
        let beamEnd: CGFloat = at.y + 30
        let outer = Gradient(stops: [
            .init(color: .clear, location: 0),
            .init(color: Color(hex: 0xFFE3EF).opacity(Double(0.7 * beamA)), location: 0.55),
            .init(color: .white.opacity(Double(0.85 * beamA)), location: 0.85),
            .init(color: .clear, location: 1),
        ])
        ctx.fill(Path(CGRect(x: at.x - beamW / 2, y: 0, width: beamW, height: beamEnd)),
                 with: .linearGradient(outer, startPoint: CGPoint(x: at.x, y: 0), endPoint: CGPoint(x: at.x, y: beamEnd)))
        let core = Gradient(stops: [
            .init(color: .clear, location: 0),
            .init(color: .white.opacity(Double(0.95 * beamA)), location: 0.85),
            .init(color: .clear, location: 1),
        ])
        ctx.fill(Path(CGRect(x: at.x - beamW * 0.15, y: 0, width: beamW * 0.3, height: beamEnd)),
                 with: .linearGradient(core, startPoint: CGPoint(x: at.x, y: 0), endPoint: CGPoint(x: at.x, y: beamEnd)))
        let pool = Gradient(colors: [.white.opacity(Double(0.8 * beamA)), Color(hex: 0xFFB3D1).opacity(Double(0.4 * beamA)), .clear])
        ctx.fill(circle(at, 80), with: .radialGradient(pool, center: at, startRadius: 0, endRadius: 80))
        // 깨질 때 분홍 폭발
        if after > 0 && after < 1 { drawBurst(&ctx, at, 95, after, theme, parts, rays: 16) }
        // 천사 고리
        if after > 0 {
            let ringA: CGFloat = after < 0.2 ? after / 0.2 : 1 - clamp01((after - 0.6) / 0.4)
            let rc = CGPoint(x: at.x, y: at.y - 78 - 8 * fsin(after * 10))
            let ring = Path(ellipseIn: CGRect(x: rc.x - 40, y: rc.y - 10, width: 80, height: 20))
            ctx.stroke(ring, with: .color(UiColor.gold.opacity(Double(0.95 * ringA))), lineWidth: 6)
            ctx.stroke(ring, with: .color(.white.opacity(Double(0.8 * ringA))), lineWidth: 2)
            for k in 0..<5 {
                let ang: CGFloat = after * 8 + CGFloat(k) * tau / 5
                let pos = CGPoint(x: rc.x + fcos(ang) * 40, y: rc.y + fsin(ang) * 10)
                ctx.fill(sparklePath(pos, 6 * ringA), with: .color(.white.opacity(Double(ringA))))
            }
        }
        // 솟아오르는 하트
        for (i, q) in hearts.enumerated() {
            let ht: CGFloat = clamp01((after - q.a * 0.3) / 0.7)
            if ht <= 0 || ht >= 1 { continue }
            let x: CGFloat = at.x + (q.s - 0.5) * 150 + fsin(ht * 9 + CGFloat(i)) * 14
            let y: CGFloat = at.y - ht * (120 + q.s * 90)
            let a: CGFloat = ht < 0.7 ? 1 : (1 - ht) / 0.3
            let color = i % 3 == 0 ? Color(hex: 0xFFB3D1) : pink
            drawFx(&ctx, .heart, CGPoint(x: x, y: y), 7 + q.s * 7, color.opacity(Double(a)), rot: q.spin * 20)
        }
        // 떨어지는 물약 병
        if t < 0.32 {
            let c = CGPoint(x: at.x, y: at.y - 170 * (1 - dropT * dropT))
            let br: CGFloat = 16
            let glow = Gradient(colors: [Color(hex: 0xFFD6E6), .clear])
            ctx.fill(circle(c, br * 2.4), with: .radialGradient(glow, center: c, startRadius: 0, endRadius: br * 2.4))
            let body = Gradient(colors: [Color(hex: 0xFF9EC0), pink])
            let hl = CGPoint(x: c.x - br * 0.3, y: c.y - br * 0.3)
            ctx.fill(circle(c, br), with: .radialGradient(body, center: hl, startRadius: 0, endRadius: br * 1.3))
            ctx.fill(Path(CGRect(x: c.x - br * 0.35, y: c.y - br * 1.55, width: br * 0.7, height: br * 0.7)), with: .color(Color(hex: 0xFFD6E6)))
            ctx.fill(Path(CGRect(x: c.x - br * 0.42, y: c.y - br * 1.85, width: br * 0.84, height: br * 0.4)), with: .color(Color(hex: 0xB07A4F)))
            drawFx(&ctx, .heart, CGPoint(x: c.x, y: c.y + br * 0.1), br * 0.45, .white.opacity(0.9))
            ctx.fill(sparklePath(CGPoint(x: c.x + br * 0.9, y: c.y - br * 0.9), br * 0.5), with: .color(.white))
        }
    }

    private func label(_ t: CGFloat) -> some View {
        let after: CGFloat = clamp01((t - 0.3) / 0.7)
        let scale: CGFloat = after < 0.12 ? 0.3 + after / 0.12 * 1.1 : 1.4 - (after - 0.12) * 0.4
        let alpha: Double = after <= 0 ? 0 : (after < 0.75 ? 1 : Double(clamp01((1 - after) / 0.25)))
        return Text("💖 " + tr("부활!", "Revived!", zh: "复活！", es: "¡Revive!", fr: "Ressuscité !", de: "Wiederbelebt!", pt: "Reviveu!", ru: "Ожил!", ja: "復活！"))
            .font(.system(size: 26, weight: .black))
            .foregroundStyle(LinearGradient(colors: [Color(hex: 0xFF7AA8), Color(hex: 0xE0115F)], startPoint: .top, endPoint: .bottom))
            .shadow(color: .white, radius: 6)
            .scaleEffect(scale)
            .opacity(alpha)
            .position(x: burst.at.x, y: burst.at.y - 60 - after * 30)
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let t = clamp01(CGFloat(tl.date.timeIntervalSince(burst.start) / ReviveBurst.sec))
            ZStack(alignment: .topLeading) {
                Canvas { ctx, size in Self.draw(&ctx, size, burst.at, t) }
                label(t)
            }
        }
        .allowsHitTesting(false)
    }
}


// ======================= 스킨별 승리 효과 · 타이머 테마 =======================

/// 타이머 테마: ❄️ 눈송이 햄스터 = 얼음 게이지 · ✨ 황금 햄스터 = 황금 게이지
struct TimerTheme {
    let icon: String
    let colors: [Color]
    let flake: FxShape

    static func of(_ skin: Animal?) -> TimerTheme? {
        switch skin {
        case .snowHamster: return TimerTheme(icon: "❄️", colors: [Color(hex: 0xBFE9FF), Color(hex: 0x5CC8FF), Color(hex: 0x3A8DFF)], flake: .snow)
        case .goldHamster: return TimerTheme(icon: "✨", colors: [Color(hex: 0xFFF4B0), Color(hex: 0xFFC21A), Color(hex: 0xFF9F1C)], flake: .coin)
        case .pinkCat: return TimerTheme(icon: "💗", colors: [Color(hex: 0xFFD6EC), Color(hex: 0xFF8FC4), Color(hex: 0xFF4FA0)], flake: .heart)
        case .blackCat: return TimerTheme(icon: "🌙", colors: [Color(hex: 0x9B8CFF), Color(hex: 0x6A4DE0), Color(hex: 0x3B2A6B)], flake: .star)
        default: return nil
        }
    }
}

/// 타이머 게이지 위로 흩날리는 눈송이 / 금화
struct TimerFlakes: View {
    let theme: TimerTheme
    let fraction: CGFloat
    let drift: Double

    var body: some View {
        Canvas { ctx, size in
            let d = CGFloat(drift)
            for i in 0..<7 {
                let fi = CGFloat(i)
                let x: CGFloat = size.width * fraction * ((fi + d) / 7)
                let y: CGFloat = size.height * (0.5 + 0.4 * fsin(d * tau + fi * 1.7))
                drawFx(&ctx, theme.flake, CGPoint(x: x, y: y), 4 + CGFloat(i % 3), theme.colors[1 + i % 2].opacity(0.9), rot: d * 360 + fi * 40)
            }
        }
        .allowsHitTesting(false)
    }
}

/// 이겼을 때 내 스킨 테마로 쏟아지는 비. 🐹 황금 햄스터는 금가루 폭죽. 승패에는 영향 없음 (모습만)
struct WinRain: View {
    let side: Side
    let skin: Animal?

    private static let drops = fxParts(46, seed: 111)
    private static let fireParts = fxParts(26, seed: 121)
    private static let goldTheme = FxTheme(colors: [Color(hex: 0xFFC21A), Color(hex: 0xFFF4B0), .white, Color(hex: 0xFF9F1C)], shapes: [.coin, .star], emoji: "✨")

    private static let flakes = fxParts(34, seed: 131)

    /// ❄️ 눈송이 햄스터: 가장자리 서리 + 큰 눈꽃이 바람에 휘날림
    private static func drawSnowstorm(_ ctx: inout GraphicsContext, _ size: CGSize, _ fall: CGFloat) {
        let frost = Color(hex: 0xDFF4FF)
        let w = size.width, h = size.height
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h * 0.22)),
                 with: .linearGradient(Gradient(colors: [frost.opacity(0.75), .clear]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h * 0.22)))
        ctx.fill(Path(CGRect(x: 0, y: h * 0.8, width: w, height: h * 0.2)),
                 with: .linearGradient(Gradient(colors: [.clear, frost.opacity(0.6)]), startPoint: CGPoint(x: 0, y: h * 0.8), endPoint: CGPoint(x: 0, y: h)))
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w * 0.18, height: h)),
                 with: .linearGradient(Gradient(colors: [frost.opacity(0.5), .clear]), startPoint: .zero, endPoint: CGPoint(x: w * 0.18, y: 0)))
        ctx.fill(Path(CGRect(x: w * 0.82, y: 0, width: w * 0.18, height: h)),
                 with: .linearGradient(Gradient(colors: [.clear, frost.opacity(0.5)]), startPoint: CGPoint(x: w * 0.82, y: 0), endPoint: CGPoint(x: w, y: 0)))
        for (i, q) in flakes.enumerated() {
            let fi = CGFloat(i)
            let tt: CGFloat = (fall * (0.8 + q.s * 0.5) + q.a).truncatingRemainder(dividingBy: 1)
            let x: CGFloat = -60 + tt * (w + 160) + fsin(tt * 9 + fi) * 40
            let y: CGFloat = (q.spin + 1) / 2 * h * 0.9 + tt * h * 0.35 - h * 0.1 + fcos(tt * 7 + fi) * 30
            let r: CGFloat = 10 + q.s * 14
            let color: Color = i % 3 == 0 ? Color(hex: 0x7FD3FF) : .white
            drawFx(&ctx, .snow, CGPoint(x: x, y: y), r, color.opacity(0.95), rot: tt * 540 + fi * 20)
            if i % 4 == 0 {
                ctx.fill(sparklePath(CGPoint(x: x + r, y: y - r), r * 0.5 * (0.5 + 0.5 * fsin(fall * 40 + fi))), with: .color(.white))
            }
        }
    }

    private static func draw(_ ctx: inout GraphicsContext, _ size: CGSize, _ theme: FxTheme, _ gold: Bool, _ snow: Bool, _ now: Double) {
        let fall = CGFloat(now.truncatingRemainder(dividingBy: 4.2) / 4.2)
        for (i, q) in drops.enumerated() {
            let y: CGFloat = ((fall * (0.7 + q.s * 0.6) + q.a).truncatingRemainder(dividingBy: 1)) * (size.height + 120) - 60
            let x: CGFloat = CGFloat(q.k % 997) / 997 * size.width + fsin(fall * tau * (1 + q.spin) + CGFloat(i)) * 26
            let shape = theme.shape(q.k)
            // 하얀 금화는 빈 고리처럼 보여서 테마 색으로
            let c0 = theme.color(q.k)
            let color = (shape == .coin && q.k % theme.colors.count == 2) ? theme.main : c0
            drawFx(&ctx, shape, CGPoint(x: x, y: y), 7 + q.s * 9, color.opacity(0.9), rot: fall * 360 * q.spin + CGFloat(q.k))
        }
        if snow { drawSnowstorm(&ctx, size, fall) }
        if gold {
            let boom = CGFloat(now.truncatingRemainder(dividingBy: 1.4) / 1.4)
            let round = Int(fall * 3)
            for k in 0..<3 {
                let p: CGFloat = (boom + CGFloat(k) / 3).truncatingRemainder(dividingBy: 1)
                let seed = (round * 7 + k * 13) % 10
                let c = CGPoint(x: size.width * (0.2 + 0.3 * CGFloat(k) + CGFloat(seed - 5) * 0.01),
                                y: size.height * (0.18 + 0.07 * CGFloat((seed + k) % 4)))
                drawBurst(&ctx, c, min(size.width, size.height) * 0.16, p, goldTheme, fireParts, rays: 12)
            }
        }
    }

    var body: some View {
        let theme = FxTheme.of(side, skin)
        let gold = skin == .goldHamster
        let snow = skin == .snowHamster
        return TimelineView(.animation) { tl in
            Canvas { ctx, size in Self.draw(&ctx, size, theme, gold, snow, tl.date.timeIntervalSinceReferenceDate) }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}
