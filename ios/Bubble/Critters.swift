import BubbleCore
import SwiftUI

/// 안드로이드 Ui 색과 같은 팔레트
enum UiColor {
    static let bgTop = Color(hex: 0xFFE3F1)
    static let bgBottom = Color(hex: 0xDCD6FF)
    static let primary = Color(hex: 0xFF7EB3)
    static let primaryDark = Color(hex: 0xE0518F)
    static let accent = Color(hex: 0x7C6CF2)
    static let gold = Color(hex: 0xFFC94D)
    static let danger = Color(hex: 0xFF5A6E)
    static let text = Color(hex: 0x4A3B5C)
    static let card = Color.white.opacity(0.95)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

private enum Palette {
    static let catFur = Color(hex: 0xFFB74D)
    static let catStripe = Color(hex: 0xF57C00)
    static let catInner = Color(hex: 0xFFE0B2)
    static let hamFur = Color(hex: 0xE6B98A)
    static let hamBelly = Color(hex: 0xFFF3E0)
    static let hamInner = Color(hex: 0xF8BBD0)
    static let ink = Color(hex: 0x4E342E)
    static let blush = Color(hex: 0xFF80AB, alpha: 0.6)
    static let nose = Color(hex: 0xFF6F91)
    static let wool = Color.white
    static let woolShade = Color(hex: 0xEDE7F6)
    static let sheepFace = Color(hex: 0xFFE4CC)
    static let dogFur = Color(hex: 0xF3D2A2)
    static let dogEar = Color(hex: 0xB7794A)
    static let giraffe = Color(hex: 0xFFD45C)
    static let giraffeSpot = Color(hex: 0xD9893A)
    static let giraffeMuzzle = Color(hex: 0xFFEDB8)
}

/// 눈을 깜빡이며 살짝 흔들리는 캐릭터. skin이 있으면 상점 동물로 그림.
struct Critter: View {
    let side: Side
    var skin: Animal? = nil
    var phase: Int = 0
    var happy: Bool = false

    var body: some View {
        TimelineView(.animation) { tl in
            let time = tl.date.timeIntervalSinceReferenceDate
            let swayPeriod = 1.8 + Double(phase * 137 % 400) / 500
            let sway = sin(time * 2 * .pi / swayPeriod) * 6
            let blinkPeriod = 3.2 + Double(phase * 311 % 1500) / 1000
            let closed = (time.truncatingRemainder(dividingBy: blinkPeriod) / blinkPeriod) > 0.94
            Canvas { ctx, size in
                switch skin {
                case .sheep: drawSheep(&ctx, size, closed, happy)
                case .dog: drawDog(&ctx, size, closed, happy)
                case .giraffe: drawGiraffe(&ctx, size, closed, happy)
                case .hamster: drawHamster(&ctx, size, closed, happy)
                case nil: side == .cat ? drawCat(&ctx, size, closed, happy) : drawHamster(&ctx, size, closed, happy)
                }
            }
            .rotationEffect(.degrees(sway), anchor: UnitPoint(x: 0.5, y: 0.9))
        }
    }
}

// MARK: - 그리기 도우미

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

private func oval(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> Path {
    Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
}

private func drawEyes(_ ctx: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ gap: CGFloat, _ r: CGFloat, _ closed: Bool, _ happy: Bool) {
    for dx in [-gap, gap] {
        if closed || happy {
            var p = Path()
            p.move(to: CGPoint(x: cx + dx - r, y: cy))
            p.addQuadCurve(to: CGPoint(x: cx + dx + r, y: cy), control: CGPoint(x: cx + dx, y: cy - r * 1.3))
            ctx.stroke(p, with: .color(Palette.ink), style: StrokeStyle(lineWidth: r * 0.55, lineCap: .round))
        } else {
            ctx.fill(circle(CGPoint(x: cx + dx, y: cy), r), with: .color(Palette.ink))
            ctx.fill(circle(CGPoint(x: cx + dx + r * 0.3, y: cy - r * 0.35), r * 0.38), with: .color(.white))
        }
    }
}

private func drawMouth(_ ctx: inout GraphicsContext, _ cx: CGFloat, _ cy: CGFloat, _ w: CGFloat) {
    var p = Path()
    p.move(to: CGPoint(x: cx - w, y: cy))
    p.addQuadCurve(to: CGPoint(x: cx, y: cy), control: CGPoint(x: cx - w / 2, y: cy + w * 0.8))
    p.addQuadCurve(to: CGPoint(x: cx + w, y: cy), control: CGPoint(x: cx + w / 2, y: cy + w * 0.8))
    ctx.stroke(p, with: .color(Palette.ink), style: StrokeStyle(lineWidth: w * 0.28, lineCap: .round))
}

private func line(_ ctx: inout GraphicsContext, _ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: CGFloat) {
    var p = Path()
    p.move(to: a)
    p.addLine(to: b)
    ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
}

// MARK: - 캐릭터

private func drawCat(_ ctx: inout GraphicsContext, _ size: CGSize, _ closed: Bool, _ happy: Bool) {
    let cx = size.width / 2, cy = size.height * 0.58, r = size.width * 0.36
    for s: CGFloat in [-1, 1] {
        var ear = Path()
        ear.move(to: CGPoint(x: cx + s * r * 0.95, y: cy - r * 0.35))
        ear.addLine(to: CGPoint(x: cx + s * r * 0.85, y: cy - r * 1.35))
        ear.addLine(to: CGPoint(x: cx + s * r * 0.2, y: cy - r * 0.85))
        ear.closeSubpath()
        ctx.fill(ear, with: .color(Palette.catFur))
        var inner = Path()
        inner.move(to: CGPoint(x: cx + s * r * 0.8, y: cy - r * 0.55))
        inner.addLine(to: CGPoint(x: cx + s * r * 0.78, y: cy - r * 1.1))
        inner.addLine(to: CGPoint(x: cx + s * r * 0.4, y: cy - r * 0.8))
        inner.closeSubpath()
        ctx.fill(inner, with: .color(Palette.hamInner))
    }
    ctx.fill(oval(cx - r * 1.05, cy - r * 0.95, r * 2.1, r * 1.85), with: .color(Palette.catFur))
    for i: CGFloat in [-1, 0, 1] {
        line(&ctx, CGPoint(x: cx + i * r * 0.28, y: cy - r * 0.92), CGPoint(x: cx + i * r * 0.22, y: cy - r * 0.6), Palette.catStripe, r * 0.12)
    }
    ctx.fill(oval(cx - r * 0.55, cy + r * 0.05, r * 1.1, r * 0.7), with: .color(Palette.catInner))
    drawEyes(&ctx, cx, cy - r * 0.12, r * 0.42, r * 0.15, closed, happy)
    ctx.fill(circle(CGPoint(x: cx - r * 0.7, y: cy + r * 0.18), r * 0.17), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx + r * 0.7, y: cy + r * 0.18), r * 0.17), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx, y: cy + r * 0.15), r * 0.08), with: .color(Palette.nose))
    drawMouth(&ctx, cx, cy + r * 0.25, r * 0.17)
    for s: CGFloat in [-1, 1] {
        for k: CGFloat in [-0.08, 0.1] {
            line(&ctx, CGPoint(x: cx + s * r * 0.45, y: cy + r * (0.25 + k)), CGPoint(x: cx + s * r * 1.15, y: cy + r * (0.18 + k * 1.8)),
                 Palette.ink.opacity(0.6), r * 0.04)
        }
    }
}

private func drawHamster(_ ctx: inout GraphicsContext, _ size: CGSize, _ closed: Bool, _ happy: Bool) {
    let cx = size.width / 2, cy = size.height * 0.58, r = size.width * 0.38
    for s: CGFloat in [-1, 1] {
        ctx.fill(circle(CGPoint(x: cx + s * r * 0.7, y: cy - r * 0.8), r * 0.32), with: .color(Palette.hamFur))
        ctx.fill(circle(CGPoint(x: cx + s * r * 0.7, y: cy - r * 0.78), r * 0.18), with: .color(Palette.hamInner))
    }
    ctx.fill(oval(cx - r * 1.1, cy - r * 0.95, r * 2.2, r * 1.9), with: .color(Palette.hamFur))
    ctx.fill(oval(cx - r * 0.75, cy - r * 0.1, r * 1.5, r * 0.95), with: .color(Palette.hamBelly))
    ctx.fill(oval(cx - r * 0.25, cy - r * 0.75, r * 0.5, r * 0.8), with: .color(Palette.hamBelly))
    drawEyes(&ctx, cx, cy - r * 0.15, r * 0.45, r * 0.14, closed, happy)
    ctx.fill(circle(CGPoint(x: cx - r * 0.72, y: cy + r * 0.15), r * 0.2), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx + r * 0.72, y: cy + r * 0.15), r * 0.2), with: .color(Palette.blush))
    ctx.fill(oval(cx - r * 0.08, cy + r * 0.05, r * 0.16, r * 0.11), with: .color(Palette.nose))
    drawMouth(&ctx, cx, cy + r * 0.2, r * 0.14)
    for s: CGFloat in [-1, 1] {
        ctx.fill(oval(cx + s * r * 0.32 - r * 0.12, cy + r * 0.55, r * 0.24, r * 0.18), with: .color(Palette.hamInner))
    }
}

private func drawSheep(_ ctx: inout GraphicsContext, _ size: CGSize, _ closed: Bool, _ happy: Bool) {
    let cx = size.width / 2, cy = size.height * 0.56, r = size.width * 0.36
    let puffs: [CGPoint] = [
        CGPoint(x: 0, y: -1), CGPoint(x: -0.7, y: -0.75), CGPoint(x: 0.7, y: -0.75), CGPoint(x: -1, y: -0.1),
        CGPoint(x: 1, y: -0.1), CGPoint(x: -0.75, y: 0.55), CGPoint(x: 0.75, y: 0.55), CGPoint(x: 0, y: 0.8),
    ]
    for o in puffs { ctx.fill(circle(CGPoint(x: cx + o.x * r, y: cy + o.y * r + r * 0.05), r * 0.42), with: .color(Palette.woolShade)) }
    for o in puffs { ctx.fill(circle(CGPoint(x: cx + o.x * r, y: cy + o.y * r), r * 0.4), with: .color(Palette.wool)) }
    ctx.fill(circle(CGPoint(x: cx, y: cy), r * 0.85), with: .color(Palette.wool))
    for s: CGFloat in [-1, 1] {
        ctx.fill(oval(cx + s * r * 0.95 - r * 0.3, cy - r * 0.2, r * 0.6, r * 0.3), with: .color(Palette.sheepFace))
    }
    ctx.fill(oval(cx - r * 0.6, cy - r * 0.45, r * 1.2, r * 1.15), with: .color(Palette.sheepFace))
    for i: CGFloat in [-1, 0, 1] { ctx.fill(circle(CGPoint(x: cx + i * r * 0.3, y: cy - r * 0.5), r * 0.24), with: .color(Palette.wool)) }
    drawEyes(&ctx, cx, cy, r * 0.3, r * 0.12, closed, happy)
    ctx.fill(circle(CGPoint(x: cx - r * 0.42, y: cy + r * 0.25), r * 0.13), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx + r * 0.42, y: cy + r * 0.25), r * 0.13), with: .color(Palette.blush))
    ctx.fill(oval(cx - r * 0.09, cy + r * 0.2, r * 0.18, r * 0.11), with: .color(Palette.nose))
    drawMouth(&ctx, cx, cy + r * 0.34, r * 0.12)
}

private func drawDog(_ ctx: inout GraphicsContext, _ size: CGSize, _ closed: Bool, _ happy: Bool) {
    let cx = size.width / 2, cy = size.height * 0.56, r = size.width * 0.37
    for s: CGFloat in [-1, 1] {
        var ear = Path()
        ear.move(to: CGPoint(x: cx + s * r * 0.55, y: cy - r * 0.8))
        ear.addQuadCurve(to: CGPoint(x: cx + s * r * 1.15, y: cy + r * 0.35), control: CGPoint(x: cx + s * r * 1.35, y: cy - r * 0.7))
        ear.addQuadCurve(to: CGPoint(x: cx + s * r * 0.7, y: cy + r * 0.15), control: CGPoint(x: cx + s * r * 0.95, y: cy + r * 0.55))
        ear.closeSubpath()
        ctx.fill(ear, with: .color(Palette.dogEar))
    }
    ctx.fill(oval(cx - r, cy - r * 0.95, r * 2, r * 1.85), with: .color(Palette.dogFur))
    ctx.fill(oval(cx + r * 0.12, cy - r * 0.42, r * 0.55, r * 0.5), with: .color(Palette.dogEar.opacity(0.55)))
    ctx.fill(oval(cx - r * 0.45, cy + r * 0.02, r * 0.9, r * 0.62), with: .color(.white))
    drawEyes(&ctx, cx, cy - r * 0.15, r * 0.38, r * 0.14, closed, happy)
    ctx.fill(circle(CGPoint(x: cx - r * 0.65, y: cy + r * 0.2), r * 0.15), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx + r * 0.65, y: cy + r * 0.2), r * 0.15), with: .color(Palette.blush))
    ctx.fill(oval(cx - r * 0.14, cy + r * 0.06, r * 0.28, r * 0.19), with: .color(Palette.ink))
    drawMouth(&ctx, cx, cy + r * 0.3, r * 0.14)
    ctx.fill(oval(cx - r * 0.09, cy + r * 0.36, r * 0.18, r * 0.22), with: .color(Palette.nose))
}

private func drawGiraffe(_ ctx: inout GraphicsContext, _ size: CGSize, _ closed: Bool, _ happy: Bool) {
    let cx = size.width / 2, cy = size.height * 0.6, r = size.width * 0.34
    for s: CGFloat in [-1, 1] {
        line(&ctx, CGPoint(x: cx + s * r * 0.35, y: cy - r * 0.7), CGPoint(x: cx + s * r * 0.45, y: cy - r * 1.35), Palette.giraffeSpot, r * 0.14)
        ctx.fill(circle(CGPoint(x: cx + s * r * 0.45, y: cy - r * 1.38), r * 0.14), with: .color(Palette.giraffeSpot))
    }
    for s: CGFloat in [-1, 1] {
        ctx.fill(oval(cx + s * r * 0.95 - r * 0.32, cy - r * 0.75, r * 0.64, r * 0.3), with: .color(Palette.giraffe))
        ctx.fill(oval(cx + s * r * 0.95 - r * 0.2, cy - r * 0.7, r * 0.4, r * 0.17), with: .color(Palette.hamInner))
    }
    ctx.fill(oval(cx - r * 0.8, cy - r * 0.95, r * 1.6, r * 1.95), with: .color(Palette.giraffe))
    ctx.fill(circle(CGPoint(x: cx - r * 0.42, y: cy - r * 0.62), r * 0.14), with: .color(Palette.giraffeSpot.opacity(0.8)))
    ctx.fill(circle(CGPoint(x: cx + r * 0.5, y: cy - r * 0.5), r * 0.1), with: .color(Palette.giraffeSpot.opacity(0.8)))
    ctx.fill(circle(CGPoint(x: cx + r * 0.15, y: cy - r * 0.78), r * 0.09), with: .color(Palette.giraffeSpot.opacity(0.8)))
    ctx.fill(oval(cx - r * 0.6, cy + r * 0.15, r * 1.2, r * 0.8), with: .color(Palette.giraffeMuzzle))
    drawEyes(&ctx, cx, cy - r * 0.2, r * 0.36, r * 0.13, closed, happy)
    ctx.fill(circle(CGPoint(x: cx - r * 0.6, y: cy + r * 0.12), r * 0.14), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx + r * 0.6, y: cy + r * 0.12), r * 0.14), with: .color(Palette.blush))
    ctx.fill(circle(CGPoint(x: cx - r * 0.2, y: cy + r * 0.38), r * 0.06), with: .color(Palette.ink))
    ctx.fill(circle(CGPoint(x: cx + r * 0.2, y: cy + r * 0.38), r * 0.06), with: .color(Palette.ink))
    drawMouth(&ctx, cx, cy + r * 0.6, r * 0.13)
}

/// 반짝이 (4갈래 별)
func sparklePath(_ c: CGPoint, _ r: CGFloat) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: c.x, y: c.y - r))
    p.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: c)
    p.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: c)
    p.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: c)
    p.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: c)
    p.closeSubpath()
    return p
}

/// 작은 하트 (핑크다이아 반짝이)
func heartPath(_ c: CGPoint, _ r: CGFloat) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: c.x, y: c.y + r * 0.9))
    p.addCurve(to: CGPoint(x: c.x, y: c.y - r * 0.45),
               control1: CGPoint(x: c.x - r * 1.6, y: c.y - r * 0.2), control2: CGPoint(x: c.x - r * 0.6, y: c.y - r * 1.3))
    p.addCurve(to: CGPoint(x: c.x, y: c.y + r * 0.9),
               control1: CGPoint(x: c.x + r * 0.6, y: c.y - r * 1.3), control2: CGPoint(x: c.x + r * 1.6, y: c.y - r * 0.2))
    p.closeSubpath()
    return p
}

/// 세트 안 캐릭터 위치 (세트 반지름 대비)
func critterSlots(_ n: Int) -> [CGPoint] {
    switch n {
    case 1: return [CGPoint(x: 0, y: 0)]
    case 2: return [CGPoint(x: -0.33, y: 0), CGPoint(x: 0.33, y: 0)]
    case 3: return [CGPoint(x: 0, y: -0.3), CGPoint(x: -0.33, y: 0.25), CGPoint(x: 0.33, y: 0.25)]
    case 4: return [CGPoint(x: -0.32, y: -0.3), CGPoint(x: 0.32, y: -0.3), CGPoint(x: -0.32, y: 0.3), CGPoint(x: 0.32, y: 0.3)]
    default: return []
    }
}
