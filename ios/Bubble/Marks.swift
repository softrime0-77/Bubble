import BubbleCore
import SwiftUI

// 안드로이드 ui/Marks.kt와 같은 "맞은 방울 흔적" (모습만, 승패에는 영향 없음).
// 계산은 모두 CGFloat로 (Double과 섞으면 컴파일러가 타입을 못 정함)
private let mTau: CGFloat = .pi * 2
private func mcos(_ x: CGFloat) -> CGFloat { CGFloat(cos(Double(x))) }
private func msin(_ x: CGFloat) -> CGFloat { CGFloat(sin(Double(x))) }
private func clamp(_ x: CGFloat) -> CGFloat { min(1, max(0, x)) }

enum MarkKind: CaseIterable {
    case ice, gold, heart, claw, stamp, ink, snow, bamboo, fire, hop, magic, paw, seed, splash, check, flag

    static func of(_ skin: Animal?, _ side: Side) -> MarkKind {
        switch skin {
        case .snowHamster: return .ice
        case .goldHamster: return .gold
        case .pinkCat: return .heart
        case .blackCat: return .claw
        case .luckyCat: return .stamp
        case .octopus: return .splash   // 💧 물햄스터: 물보라
        case .penguin: return .snow
        case .panda: return .check      // 💮 출석 햄스터: 출석 도장
        case .dragon: return .fire
        case .rabbit: return .flag      // 🚩 연승 햄스터: Win 깃발
        case .unicorn: return .magic
        case .hamster: return .seed
        default: return SideLook.of(side) == .cat ? .paw : .seed
        }
    }

    /// 흔적 소리 (기본 고양이·햄스터는 통!)
    var sfx: Music.Sfx {
        switch self {
        case .ice: return .markIce
        case .gold: return .markGold
        case .heart: return .markHeart
        case .claw: return .markClaw
        case .stamp: return .markStamp
        case .ink: return .markInk
        case .snow: return .markSnow
        case .bamboo: return .markBamboo
        case .fire: return .markFire
        case .hop: return .markHop
        case .magic: return .markMagic
        case .paw, .seed: return .hit
        // 💧 물보라 · 💮 출석 도장 · 🚩 깃발 (비슷한 소리 재사용)
        case .splash: return .markInk
        case .check: return .markStamp
        case .flag: return .markHop
        }
    }
}

struct HitMark: Identifiable, Equatable {
    let id = UUID()
    let at: CGPoint
    let kind: MarkKind
    let start = Date()
    static let sec = 1.4
}

struct HitMarkFx: View {
    let mark: HitMark
    private let seeds: [(CGFloat, CGFloat, CGFloat)]

    init(mark: HitMark) {
        self.mark = mark
        var g = SeededRandom(seed: UInt64(abs(mark.id.hashValue) % 100_000 + 1))
        seeds = (0..<14).map { _ in (g.next(), g.next(), g.next()) }
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let p = clamp(CGFloat(tl.date.timeIntervalSince(mark.start) / HitMark.sec))
            Canvas { ctx, _ in draw(&ctx, p) }
        }
        .allowsHitTesting(false)
    }

    /// 0→0.15 나타남, 끝 0.25 동안 사라짐
    private func fade(_ p: CGFloat) -> CGFloat { clamp(p / 0.15) * (1 - clamp((p - 0.75) / 0.25)) }

    private func draw(_ ctx: inout GraphicsContext, _ p: CGFloat) {
        let c = mark.at
        let r: CGFloat = 62
        let a = Double(fade(p))
        switch mark.kind {
        case .ice: ice(&ctx, c, r, p, a)
        case .gold: gold(&ctx, c, r, p, a)
        case .heart: hearts(&ctx, c, r, p, a)
        case .claw: claw(&ctx, c, r, p, a)
        case .stamp: stamp(&ctx, c, r, p)
        case .ink: ink(&ctx, c, r, p, a)
        case .snow: snow(&ctx, c, r, p)
        case .bamboo: bamboo(&ctx, c, r, p, a)
        case .fire: fire(&ctx, c, r, p, a)
        case .hop: hop(&ctx, c, r, p, a)
        case .magic: magic(&ctx, c, r, p, a)
        case .paw: paw(&ctx, CGPoint(x: c.x, y: c.y + r * 0.1), r * 0.5 * pop(p), Color(hex: 0xFF8FB8).opacity(0.85 * a))
        case .seed: seed(&ctx, c, r, p, a)
        case .splash: splash(&ctx, c, r, p, a)
        case .check: check(&ctx, c, r, p)
        case .flag: flag(&ctx, c, r, p, a)
        }
    }

    // 💧 물보라: 물결 고리 두 겹 + 사방으로 튀는 물방울
    private func splash(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        let water = Color(hex: 0x4FC3F7)
        for k in 0...1 {
            let q = clamp((p - CGFloat(k) * 0.15) / 0.7)
            if q > 0 { ctx.stroke(circle(c, r * (0.3 + q * 0.8)), with: .color(water.opacity(0.7 * Double(1 - q) * a)), lineWidth: 5 - CGFloat(k) * 2) }
        }
        ctx.fill(circle(c, r), with: .radialGradient(Gradient(colors: [water.opacity(0.4 * a), .clear]), center: c, startRadius: 0, endRadius: r))
        let q = clamp(p / 0.6)
        for (u, v, w) in seeds.prefix(10) {
            let ang = u * mTau
            let d = r * (0.25 + q * (0.6 + v * 0.5))
            let pos = CGPoint(x: c.x + mcos(ang) * d, y: c.y + msin(ang) * d - msin(q * .pi) * r * 0.35 + q * q * r * 0.3)
            let rr = (3 + w * 5) * (1 - q * 0.4)
            ctx.fill(circle(pos, rr), with: .color(water.opacity(0.85 * a)))
            ctx.fill(circle(CGPoint(x: pos.x - 1.5, y: pos.y - 1.5), 1.2 + w * 1.5), with: .color(.white.opacity(0.7 * a)))
        }
    }

    // 💮 출석 도장: 빨간 동그라미 쾅 + 체크
    private func check(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat) {
        let slam: CGFloat = p < 0.15 ? 1.8 - 0.8 * (p / 0.15) : 1
        let rr = r * 0.5 * slam
        let al = Double(clamp(p / 0.1) * (1 - clamp((p - 0.75) / 0.25)))
        let red = Color(hex: 0xE53935)
        var g = ctx
        g.translateBy(x: c.x, y: c.y)
        g.rotate(by: .degrees(-10))
        g.stroke(circle(.zero, rr), with: .color(red.opacity(0.85 * al)), lineWidth: 5)
        g.stroke(circle(.zero, rr * 0.78), with: .color(red.opacity(0.5 * al)), lineWidth: 2)
        var chk = Path()
        chk.move(to: CGPoint(x: -rr * 0.45, y: 0))
        chk.addLine(to: CGPoint(x: -rr * 0.1, y: rr * 0.35))
        chk.addLine(to: CGPoint(x: rr * 0.5, y: -rr * 0.35))
        g.stroke(chk, with: .color(red.opacity(0.9 * al)), style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
        if p >= 0.15 && p <= 0.4 {
            let q = (p - 0.15) / 0.25
            ctx.stroke(circle(c, r * (0.6 + (p - 0.15) * 3)), with: .color(red.opacity(Double(0.4 * (1 - q)))), lineWidth: 3)
        }
    }

    // 🚩 Win 깃발이 콕 꽂히고 펄럭
    private func flag(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        let drop: CGFloat = p < 0.18 ? 1 - p / 0.18 : 0
        let base = CGPoint(x: c.x - r * 0.15, y: c.y + r * 0.45 - drop * r * 1.2)
        let top = CGPoint(x: base.x, y: base.y - r)
        line(&ctx, base, top, Color(hex: 0x8D6E63).opacity(a), 4)
        let wave = msin(p * 20) * r * 0.06
        var f = Path()
        f.move(to: top)
        f.addQuadCurve(to: CGPoint(x: top.x + r * 0.7, y: top.y + r * 0.05 + wave), control: CGPoint(x: top.x + r * 0.35, y: top.y - r * 0.05 + wave))
        f.addLine(to: CGPoint(x: top.x + r * 0.7, y: top.y + r * 0.42 + wave))
        f.addQuadCurve(to: CGPoint(x: top.x, y: top.y + r * 0.4), control: CGPoint(x: top.x + r * 0.35, y: top.y + r * 0.32 - wave))
        f.closeSubpath()
        ctx.fill(f, with: .color(Color(hex: 0xE53935).opacity(a)))
        ctx.draw(Text("Win").font(.system(size: r * 0.24, weight: .black)).foregroundColor(.white.opacity(a)),
                 at: CGPoint(x: top.x + r * 0.35, y: top.y + r * 0.21 + wave))
        if p >= 0.18 && p <= 0.4 {
            let q = (p - 0.18) / 0.22
            for (u, _, w) in seeds.prefix(5) {
                ctx.fill(circle(CGPoint(x: base.x + (u - 0.5) * r * q, y: base.y - q * r * 0.15), 3 + w * 3),
                         with: .color(Color(hex: 0xBCAAA4).opacity(Double(0.6 * (1 - q)))))
            }
        }
    }

    private func pop(_ p: CGFloat) -> CGFloat { p < 0.12 ? 1.4 - 0.4 * p / 0.12 : 1 }

    private func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

    private func line(_ ctx: inout GraphicsContext, _ a: CGPoint, _ b: CGPoint, _ color: Color, _ w: CGFloat) {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: .round))
    }

    private func star(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color) {
        var p = Path()
        let k = r * 1.6
        p.move(to: CGPoint(x: c.x, y: c.y - k))
        p.addQuadCurve(to: CGPoint(x: c.x + k, y: c.y), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + k), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x - k, y: c.y), control: c)
        p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - k), control: c)
        ctx.fill(p, with: .color(color))
    }

    // ❄️ 얼음이 덮였다가 쨍! 깨져 조각이 튐
    private func ice(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        if p < 0.72 {
            let grow = 0.7 + 0.3 * min(1, p / 0.15)
            ctx.fill(circle(c, r * grow), with: .radialGradient(Gradient(colors: [Color(hex: 0xE6F7FF, alpha: 0.6 * a), Color(hex: 0xAEE2FF, alpha: 0.8 * a), Color(hex: 0xFFFFFF, alpha: 0.6 * a)]),
                                                                center: c, startRadius: 0, endRadius: r))
            for i in 0..<6 {
                let ang = CGFloat(i) * mTau / 6 + 0.3
                let e = CGPoint(x: c.x + mcos(ang) * r * 0.85, y: c.y + msin(ang) * r * 0.85)
                line(&ctx, c, e, .white.opacity(0.9 * a), 3)
                let mid = CGPoint(x: c.x + mcos(ang) * r * 0.5, y: c.y + msin(ang) * r * 0.5)
                for d: CGFloat in [-0.6, 0.6] {
                    line(&ctx, mid, CGPoint(x: mid.x + mcos(ang + d) * r * 0.22, y: mid.y + msin(ang + d) * r * 0.22), .white.opacity(0.8 * a), 2)
                }
            }
            ctx.stroke(circle(c, r * 0.95), with: .color(.white.opacity(0.7 * a)), lineWidth: 3)
        } else {
            let q = (p - 0.72) / 0.28
            for (u, v, w) in seeds.prefix(10) {
                let ang = u * mTau
                let d = r * (0.3 + q * (0.9 + v))
                let pos = CGPoint(x: c.x + mcos(ang) * d, y: c.y + msin(ang) * d + q * q * r * 0.6)
                let sz = 5 + w * 8
                ctx.fill(Path(CGRect(x: pos.x - sz / 2, y: pos.y - sz / 2, width: sz, height: sz * 0.7)), with: .color(Color(hex: 0xCDEFFF).opacity(Double(1 - q))))
            }
        }
    }

    // 🏆 금빛 코팅 + 빛 줄기 + 반짝
    private func gold(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        ctx.fill(circle(c, r), with: .radialGradient(Gradient(colors: [Color(hex: 0xFFF3B0, alpha: 0.53 * a), Color(hex: 0xFFC107, alpha: 0.67 * a), Color(hex: 0xFFB300, alpha: 0.33 * a)]),
                                                     center: c, startRadius: 0, endRadius: r))
        let x = c.x - r + p * 2.4 * r
        line(&ctx, CGPoint(x: x - r * 0.4, y: c.y + r * 0.7), CGPoint(x: x + r * 0.2, y: c.y - r * 0.8), .white.opacity(0.7 * a), 10)
        for (u, v, w) in seeds.prefix(6) {
            star(&ctx, CGPoint(x: c.x + (u - 0.5) * r * 1.8, y: c.y + (v - 0.5) * r * 1.8), 4 + 6 * max(0, msin((p * 3 + w) * .pi)), Color(hex: 0xFFF59D).opacity(a))
        }
    }

    private func heartPath(_ c: CGPoint, _ r: CGFloat) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: c.x, y: c.y + r * 0.9))
        p.addCurve(to: CGPoint(x: c.x, y: c.y - r * 0.45), control1: CGPoint(x: c.x - r * 1.6, y: c.y - r * 0.2), control2: CGPoint(x: c.x - r * 0.6, y: c.y - r * 1.3))
        p.addCurve(to: CGPoint(x: c.x, y: c.y + r * 0.9), control1: CGPoint(x: c.x + r * 0.6, y: c.y - r * 1.3), control2: CGPoint(x: c.x + r * 1.6, y: c.y - r * 0.2))
        return p
    }

    // 🌸 볼 빨개지고 하트가 뿅뿅
    private func hearts(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        for sx: CGFloat in [-1, 1] {
            ctx.fill(Path(ellipseIn: CGRect(x: c.x + sx * r * 0.45 - r * 0.18, y: c.y + r * 0.1, width: r * 0.36, height: r * 0.2)), with: .color(Color(hex: 0xFF7EB3).opacity(0.45 * a)))
        }
        for (i, s) in seeds.prefix(5).enumerated() {
            let q = clamp((p - CGFloat(i) * 0.08) / 0.7)
            guard q > 0 else { continue }
            let pos = CGPoint(x: c.x + (s.0 - 0.5) * r * 1.2 + msin(q * 6 + s.2 * 6) * 8, y: c.y - q * r * 1.3)
            ctx.fill(heartPath(pos, 9 + s.2 * 6), with: .color(Color(hex: 0xFF5FA2).opacity(Double(1 - q) * a)))
        }
    }

    // 🐈‍⬛ 발톱 자국 세 줄
    private func claw(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        let d = clamp(p / 0.25)
        for i in 0..<3 {
            let off = CGFloat(i - 1) * r * 0.32
            let st = CGPoint(x: c.x - r * 0.4 + off, y: c.y - r * 0.6)
            let en = CGPoint(x: c.x + r * 0.4 + off, y: c.y + r * 0.6)
            let e = CGPoint(x: st.x + (en.x - st.x) * d, y: st.y + (en.y - st.y) * d)
            line(&ctx, st, e, Color(hex: 0x3A2A4F).opacity(0.85 * a), 7)
            line(&ctx, CGPoint(x: st.x + 3, y: st.y), CGPoint(x: e.x + 3, y: e.y), Color(hex: 0xB39DDB).opacity(0.7 * a), 2)
        }
    }

    // 💰 빨간 福 도장 쾅
    private func stamp(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat) {
        let slam: CGFloat = p < 0.15 ? 1.8 - 0.8 * (p / 0.15) : 1
        let sz = r * 0.95 * slam
        let al = Double(clamp(p / 0.1) * (1 - clamp((p - 0.75) / 0.25)))
        var g = ctx
        g.translateBy(x: c.x, y: c.y)
        g.rotate(by: .degrees(-12))
        let rect = CGRect(x: -sz / 2, y: -sz / 2, width: sz, height: sz)
        g.fill(Path(roundedRect: rect, cornerRadius: sz * 0.18), with: .color(Color(hex: 0xE53935).opacity(0.85 * al)))
        g.stroke(Path(roundedRect: rect.insetBy(dx: sz * 0.08, dy: sz * 0.08), cornerRadius: sz * 0.14), with: .color(.white.opacity(0.8 * al)), lineWidth: 2)
        g.draw(Text("福").font(.system(size: sz * 0.62, weight: .black)).foregroundColor(.white.opacity(al)), at: .zero)
        if p >= 0.15 && p <= 0.4 {
            let q = (p - 0.15) / 0.25
            ctx.stroke(circle(c, r * (0.6 + (p - 0.15) * 3)), with: .color(Color(hex: 0xE53935).opacity(Double(0.5 * (1 - q)))), lineWidth: 4)
        }
    }

    // 🐙 먹물 얼룩 철퍽
    private func ink(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        let col = Color(hex: 0x3B2557)
        ctx.fill(circle(c, r * 0.45 * min(1, p / 0.12)), with: .color(col.opacity(0.75 * a)))
        for (u, v, w) in seeds.prefix(9) {
            let ang = u * mTau
            let d = r * (0.35 + v * 0.55) * min(1, p / 0.15)
            ctx.fill(circle(CGPoint(x: c.x + mcos(ang) * d, y: c.y + msin(ang) * d + p * w * r * 0.4), 5 + w * 10), with: .color(col.opacity(0.7 * a)))
        }
        ctx.fill(circle(CGPoint(x: c.x - r * 0.15, y: c.y - r * 0.15), r * 0.12), with: .color(.white.opacity(0.35 * a)))
    }

    // 🐧 눈이 소복이 쌓였다가 털림
    private func snow(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat) {
        if p <= 0.7 {
            let grow = clamp(p / 0.3)
            for k in -3...3 {
                let ang = (-90 + CGFloat(k) * 22) * .pi / 180
                let pos = CGPoint(x: c.x + mcos(ang) * r * 0.82, y: c.y + msin(ang) * r * 0.82)
                ctx.fill(circle(pos, r * (0.2 + 0.05 * CGFloat(3 - abs(k)) / 3) * grow), with: .color(.white.opacity(0.97)))
            }
            for (u, v, _) in seeds.prefix(8) {
                let y = c.y - r * 1.4 + (p * 2 + v).truncatingRemainder(dividingBy: 1) * r * 1.1
                ctx.fill(circle(CGPoint(x: c.x + (u - 0.5) * r * 1.6, y: y), 4), with: .color(.white.opacity(0.9)))
            }
        } else {
            let q = clamp((p - 0.7) / 0.3)
            for (u, v, w) in seeds.prefix(12) {
                let pos = CGPoint(x: c.x + (u - 0.5) * r * 1.6 + (u - 0.5) * q * r, y: c.y - r * 0.7 + q * (0.5 + v) * r * 1.6)
                ctx.fill(circle(pos, 3 + w * 4), with: .color(.white.opacity(Double(1 - q))))
            }
        }
    }

    /// 발바닥: 큰 패드 + 발가락 넷
    private func paw(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color) {
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r * 0.45, y: c.y - r * 0.1, width: r * 0.9, height: r * 0.7)), with: .color(color))
        let toes: [(CGFloat, CGFloat)] = [(-0.55, -0.35), (-0.2, -0.65), (0.2, -0.65), (0.55, -0.35)]
        for t in toes {
            ctx.fill(Path(ellipseIn: CGRect(x: c.x + t.0 * r - r * 0.15, y: c.y + t.1 * r - r * 0.18, width: r * 0.3, height: r * 0.36)), with: .color(color))
        }
    }

    // 🐼 판다 발도장 + 대나무 잎
    private func bamboo(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        paw(&ctx, CGPoint(x: c.x, y: c.y + r * 0.1), r * 0.55 * pop(p), Color(hex: 0x2B2B2B).opacity(0.8 * a))
        for (i, s) in seeds.prefix(3).enumerated() {
            let pos = CGPoint(x: c.x + (s.0 - 0.5) * r * 1.3, y: c.y - r * 0.55 + s.1 * r * 0.3)
            var g = ctx
            g.translateBy(x: pos.x, y: pos.y)
            g.rotate(by: .degrees(Double(-30 + i * 35)))
            g.fill(Path(ellipseIn: CGRect(x: -r * 0.22, y: -r * 0.08, width: r * 0.44, height: r * 0.16)), with: .color(Color(hex: 0x66BB6A).opacity(a)))
        }
    }

    private func flame(_ ctx: inout GraphicsContext, _ b: CGPoint, _ h: CGFloat, _ a: Double) {
        var o = Path()
        o.move(to: CGPoint(x: b.x, y: b.y - h))
        o.addCurve(to: b, control1: CGPoint(x: b.x + h * 0.5, y: b.y - h * 0.5), control2: CGPoint(x: b.x + h * 0.4, y: b.y))
        o.addCurve(to: CGPoint(x: b.x, y: b.y - h), control1: CGPoint(x: b.x - h * 0.4, y: b.y), control2: CGPoint(x: b.x - h * 0.5, y: b.y - h * 0.5))
        ctx.fill(o, with: .color(Color(hex: 0xFF7043).opacity(0.9 * a)))
        var i = Path()
        i.move(to: CGPoint(x: b.x, y: b.y - h * 0.6))
        i.addCurve(to: b, control1: CGPoint(x: b.x + h * 0.25, y: b.y - h * 0.3), control2: CGPoint(x: b.x + h * 0.2, y: b.y))
        i.addCurve(to: CGPoint(x: b.x, y: b.y - h * 0.6), control1: CGPoint(x: b.x - h * 0.2, y: b.y), control2: CGPoint(x: b.x - h * 0.25, y: b.y - h * 0.3))
        ctx.fill(i, with: .color(Color(hex: 0xFFEB3B).opacity(0.95 * a)))
    }

    // 🐉 불꽃 화르륵 + 그을림 + 연기
    private func fire(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        ctx.fill(circle(c, r * 1.1), with: .radialGradient(Gradient(colors: [Color(hex: 0xFFC107, alpha: 0.33 * a), Color(hex: 0xFF5722, alpha: 0.13 * a), .clear]),
                                                           center: CGPoint(x: c.x, y: c.y + r * 0.4), startRadius: 0, endRadius: r * 1.1))
        ctx.fill(circle(CGPoint(x: c.x, y: c.y + r * 0.1), r * 0.85), with: .color(Color(hex: 0x3E2723).opacity(0.15 * a)))
        for (u, v, w) in seeds.prefix(7) {
            let fl = 0.75 + 0.25 * msin(p * 18 + w * 10)
            let base = CGPoint(x: c.x + (u - 0.5) * r * 1.3, y: c.y + r * 0.6 - abs(u - 0.5) * r * 0.4)
            flame(&ctx, base, r * (0.5 + v * 0.35) * fl * (1 - p * 0.5), a)
        }
        for (u, v, w) in seeds.dropFirst(7).prefix(5) {
            let q = (p * 1.2 + v).truncatingRemainder(dividingBy: 1)
            ctx.fill(circle(CGPoint(x: c.x + (u - 0.5) * r + msin(q * 5 + w) * 10, y: c.y - q * r * 1.5), 8 + q * 16), with: .color(Color(hex: 0x9E9E9E).opacity(0.45 * Double(1 - q) * a)))
        }
    }

    // 🐰 발자국 콩콩 (세 번)
    private func hop(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        for i in 0..<3 {
            let t0 = CGFloat(i) * 0.18
            guard p >= t0 else { continue }
            let pos = CGPoint(x: c.x - r * 0.5 + CGFloat(i) * r * 0.5, y: c.y + r * 0.35 - CGFloat(i) * r * 0.35)
            let pp: CGFloat = p < t0 + 0.08 ? 1.3 - 0.3 * (p - t0) / 0.08 : 1
            for sx: CGFloat in [-1, 1] {
                ctx.fill(Path(ellipseIn: CGRect(x: pos.x + sx * r * 0.1 - r * 0.07 * pp, y: pos.y - r * 0.16 * pp, width: r * 0.14 * pp, height: r * 0.3 * pp)),
                         with: .color(Color(hex: 0xF48FB1).opacity(0.85 * a)))
            }
        }
    }

    // 🦄 무지개 고리 + 별가루
    private func magic(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        let ring = (0..<7).map { i in Color(hue: Double((CGFloat(i) * 50 + p * 360).truncatingRemainder(dividingBy: 360) / 360), saturation: 0.55, brightness: 1) }
        var g = ctx
        g.opacity = a
        g.stroke(circle(c, r * 0.95), with: .conicGradient(Gradient(colors: ring), center: c), lineWidth: 6)
        for (i, s) in seeds.prefix(10).enumerated() {
            let ang = s.0 * mTau + p * 4
            let d = r * (0.6 + s.1 * 0.5)
            star(&ctx, CGPoint(x: c.x + mcos(ang) * d, y: c.y + msin(ang) * d), 3 + s.2 * 5,
                 Color(hue: Double(i) * 36 / 360, saturation: 0.35, brightness: 1).opacity(a))
        }
    }

    // 🐹 해바라기씨 톡톡
    private func seed(_ ctx: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ p: CGFloat, _ a: Double) {
        for (i, s) in seeds.prefix(5).enumerated() {
            let q = clamp((p - CGFloat(i) * 0.05) / 0.5)
            let pos = CGPoint(x: c.x + (s.0 - 0.5) * r * 1.2, y: c.y - r * 0.2 + (s.1 - 0.5) * r * 0.8 - msin(q * .pi) * r * 0.3)
            var g = ctx
            g.translateBy(x: pos.x, y: pos.y)
            g.rotate(by: .degrees(Double(s.0 * 180)))
            g.fill(Path(ellipseIn: CGRect(x: -r * 0.1, y: -r * 0.17, width: r * 0.2, height: r * 0.34)), with: .color(Color(hex: 0x4E342E).opacity(a)))
            line(&g, CGPoint(x: 0, y: -r * 0.12), CGPoint(x: 0, y: r * 0.12), Color(hex: 0xEFEBE9).opacity(a), 2)
        }
    }
}

/// 상점 설명: 공격받은 상대 방울에 남는 흔적 (모습만)
func markDesc(_ a: Animal) -> String {
    switch a {
    case .snowHamster: return tr("❄️ 공격하면 맞은 방울이 꽁꽁 얼었다가 쨍그랑! (모습만)", "❄️ Hit bubbles freeze over, then shatter! (look only)", zh: "❄️ 被打的泡泡结冰后啪地碎裂！（仅外观）", ja: "❄️ 攻撃した泡がカチコチに凍ってパリン！（見た目だけ）")
    case .goldHamster: return tr("✨ 공격하면 맞은 방울이 금빛으로 반짝 코팅 (모습만)", "✨ Hit bubbles get a shiny gold coat (look only)", zh: "✨ 被打的泡泡镀上金光（仅外观）", ja: "✨ 攻撃した泡が金色にキラリ（見た目だけ）")
    case .pinkCat: return tr("💕 공격하면 맞은 방울에 하트 뿅뿅, 볼이 발그레 (모습만)", "💕 Hit bubbles blush and float hearts (look only)", zh: "💕 被打的泡泡冒出爱心、脸红（仅外观）", ja: "💕 攻撃した泡にハートがポンポン（見た目だけ）")
    case .blackCat: return tr("🐾 공격하면 맞은 방울에 발톱 자국 슥슥 (모습만)", "🐾 Hit bubbles get claw marks (look only)", zh: "🐾 被打的泡泡留下爪痕（仅外观）", ja: "🐾 攻撃した泡にツメあとシュッシュッ（見た目だけ）")
    case .luckyCat: return tr("🧧 공격하면 맞은 방울에 빨간 福 도장 쾅! (모습만)", "🧧 Hit bubbles get a red 福 stamp! (look only)", zh: "🧧 被打的泡泡盖上红色「福」印章！（仅外观）", ja: "🧧 攻撃した泡に赤い「福」のハンコ！（見た目だけ）")
    case .octopus: return tr("💧 공격하면 맞은 방울에 물보라 촤악 (모습만)", "💧 Hit bubbles get a splash of water (look only)", zh: "💧 被打的泡泡溅起水花（仅外观）", ja: "💧 攻撃した泡に水しぶきザバッ（見た目だけ）")
    case .penguin: return tr("☃️ 공격하면 맞은 방울에 눈이 소복이 쌓였다 털려요 (모습만)", "☃️ Snow piles up on hit bubbles, then shakes off (look only)", zh: "☃️ 被打的泡泡积满雪又抖落（仅外观）", ja: "☃️ 攻撃した泡に雪がこんもり（見た目だけ）")
    case .panda: return tr("💮 공격하면 맞은 방울에 빨간 출석 도장 쾅 (모습만)", "💮 Hit bubbles get a red check-in stamp (look only)", zh: "💮 被打的泡泡盖上红色签到章（仅外观）", ja: "💮 攻撃した泡に赤い出席スタンプ（見た目だけ）")
    case .dragon: return tr("🔥 공격하면 맞은 방울에 불꽃 화르륵, 연기 모락 (모습만)", "🔥 Hit bubbles catch fire and smoke (look only)", zh: "🔥 被打的泡泡着火冒烟（仅外观）", ja: "🔥 攻撃した泡にほのおボウッ（見た目だけ）")
    case .rabbit: return tr("🚩 공격하면 맞은 방울에 Win 깃발이 콕! (모습만)", "🚩 A \"Win\" flag gets planted on hit bubbles (look only)", zh: "🚩 被打的泡泡插上 Win 小旗（仅外观）", ja: "🚩 攻撃した泡に「Win」の旗がグサッ（見た目だけ）")
    case .unicorn: return tr("🌈 공격하면 맞은 방울에 무지개 고리와 별가루 반짝 (모습만)", "🌈 Rainbow rings and stardust swirl on hit bubbles (look only)", zh: "🌈 被打的泡泡环绕彩虹和星尘（仅外观）", ja: "🌈 攻撃した泡に虹の輪と星くず（見た目だけ）")
    default: return ""
    }
}
