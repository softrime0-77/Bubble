import BubbleCore
import SwiftUI

/// 배경 방울 위치 (제네릭 타입 안에는 static 저장 속성을 둘 수 없어 파일 수준에 둠)
private let backgroundSeeds: [(CGFloat, CGFloat, CGFloat)] = {
    var g = SeededRandom(seed: 7)
    return (0..<16).map { _ in (g.next(), g.next(), 10 + g.next() * 34) }
}()

/// 둥실둥실 떠오르는 비눗방울 배경
struct BubbleBackground<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            LinearGradient(colors: [UiColor.bgTop, UiColor.bgBottom], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            TimelineView(.animation) { tl in
                let time = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 18) / 18
                Canvas { ctx, size in
                    for (i, s) in backgroundSeeds.enumerated() {
                        let speed = 0.6 + Double(i % 4) * 0.25
                        let y = 1.1 - (Double(s.1) + time * speed).truncatingRemainder(dividingBy: 1.2)
                        let wobble = sin(time * 2 * .pi * Double(1 + i % 3) + Double(i)) * 14
                        let c = CGPoint(x: s.0 * size.width + wobble, y: y * size.height)
                        let r = s.2 / 2.5 * 1.2
                        let p = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
                        ctx.fill(p, with: .color(.white.opacity(0.28)))
                        ctx.stroke(p, with: .color(.white.opacity(0.6)), lineWidth: 1.5)
                    }
                }
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
            content
        }
    }
}

/// 재현 가능한 간단한 난수 (배경 방울 위치용)
struct SeededRandom {
    private var state: UInt64
    init(seed: UInt64) { state = seed &* 6364136223846793005 &+ 1 }
    mutating func next() -> CGFloat {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return CGFloat(Double(state >> 11) / Double(1 << 53))
    }
}

enum BubbleMode { case normal, selected, target, disabled }

/// 세트 하나 = 비눗방울. 4마리면 빨갛게 떨고, explodeKey가 바뀌면 펑 터짐.
struct SetBubbleView: View {
    let side: Side
    let count: Int
    let title: String
    let mode: BubbleMode
    let size: CGFloat
    var preview: String? = nil
    var explodeKey: Int? = nil
    var skin: Animal? = nil
    /// 🐰 연승 토끼 리본 수
    var ribbons: Int = 0
    var onTap: (() -> Void)? = nil

    @State private var bounce: CGFloat = 1
    @State private var boomStart: Date? = nil

    private var danger: Bool { count == 4 }
    private var dead: Bool { count == 0 }

    var body: some View {
        TimelineView(.animation) { tl in
            let now = tl.date.timeIntervalSinceReferenceDate
            let breathe = 1 + sin(now * 2 * .pi / 2.8) * 0.03
            let shake = danger ? sin(now * 2 * .pi / 0.18) * 2.2 : 0
            let pulse = (sin(now * 2 * .pi / 1.8) + 1) / 2
            ZStack {
                if mode == .selected && !dead {
                    SelectedSparkles(side: side, skin: skin, time: now)
                }
                bubble(pulse: pulse)
                    .scaleEffect(dead ? 0.9 : breathe * bounce)
                if !dead {
                    critters.scaleEffect(breathe * bounce)
                } else {
                    Text(t("텅…")).font(.system(size: 14)).foregroundColor(UiColor.text.opacity(0.45))
                }
                if let start = boomStart {
                    let p = min(1, tl.date.timeIntervalSince(start) / 1.2)
                    // 캐릭터마다 다른 모양으로 화려하게 터짐
                    if p < 1 { PopBurst(side: side, skin: skin, p: p) }
                }
                if let preview {
                    Text(preview)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10).padding(.vertical, 3)
                        .background(Capsule().fill(preview.contains("💥") ? UiColor.danger : UiColor.accent))
                        .frame(maxHeight: .infinity, alignment: .top)
                        .offset(y: -6)
                }
                Text(dead ? "\(title) 💀" : "\(title) · \(count)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(danger ? UiColor.danger : UiColor.text)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Capsule().fill(.white.opacity(0.75)))
                    .frame(maxHeight: .infinity, alignment: .bottom)
                    .offset(y: 10)
            }
            .offset(x: shake, y: mode == .selected ? -10 : 0)
        }
        .frame(width: size, height: size)
        .contentShape(Circle())
        .onTapGesture { onTap?() }
        .onChange(of: count) { _ in
            bounce = 0.82
            withAnimation(.spring(response: 0.45, dampingFraction: 0.35)) { bounce = 1 }
        }
        .onChange(of: explodeKey) { key in
            if key != nil { boomStart = Date() }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.5), value: mode == .selected)
    }

    private func bubble(pulse: Double) -> some View {
        Canvas { ctx, sz in
            let r = min(sz.width, sz.height) / 2 * 0.92
            let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
            let rect = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
            if dead {
                ctx.stroke(Path(ellipseIn: rect), with: .color(.white.opacity(0.35)),
                           style: StrokeStyle(lineWidth: 3, dash: [9, 7]))
                return
            }
            let tint: Color = danger ? UiColor.danger : (SideLook.of(side) == .cat ? Color(hex: 0xFFB86B) : Color(hex: 0xC8A2FF))
            ctx.fill(Path(ellipseIn: rect), with: .radialGradient(
                Gradient(colors: [.white.opacity(0.85), tint.opacity(0.35), tint.opacity(0.55)]),
                center: CGPoint(x: c.x - r * 0.3, y: c.y - r * 0.35), startRadius: 0, endRadius: r * 1.5))
            ctx.stroke(Path(ellipseIn: rect), with: .color(.white.opacity(0.9)), lineWidth: 2.5)
            let hl = CGPoint(x: c.x - r * 0.5, y: c.y - r * 0.55)
            ctx.fill(Path(ellipseIn: CGRect(x: hl.x - r * 0.13, y: hl.y - r * 0.13, width: r * 0.26, height: r * 0.26)), with: .color(.white.opacity(0.85)))
            switch mode {
            case .selected:
                ctx.stroke(Path(ellipseIn: rect.insetBy(dx: -5, dy: -5)), with: .color(UiColor.gold), lineWidth: 5)
            case .target:
                ctx.stroke(Path(ellipseIn: rect.insetBy(dx: -4, dy: -4)), with: .color(UiColor.primary.opacity(0.5 + pulse * 0.5)),
                           style: StrokeStyle(lineWidth: 4, dash: [11, 6], dashPhase: pulse * 34))
            default:
                break
            }
            if danger {
                ctx.stroke(Path(ellipseIn: rect.insetBy(dx: -2, dy: -2)), with: .color(UiColor.danger.opacity(0.25 + pulse * 0.45)), lineWidth: 3)
            }
        }
    }

    private var critters: some View {
        GeometryReader { geo in
            let half = geo.size.width / 2
            let cs = count == 1 ? geo.size.width * 0.55 : geo.size.width * 0.36
            ForEach(Array(critterSlots(count).enumerated()), id: \.offset) { i, o in
                Critter(side: side, skin: skin, phase: i + (side == .cat ? 0 : 5), ribbons: ribbons)
                    .frame(width: cs, height: cs)
                    .position(x: half + half * o.x * 1.25, y: half + half * o.y * 1.25)
                    .transition(.scale)
            }
        }
    }
}

/// 남은 분할 횟수 ●●○
struct SplitCounter: View {
    let left: Int
    var body: some View {
        HStack(spacing: 3) {
            Text("✂️").font(.system(size: 14))
            ForEach(0..<GameLogic.maxSplits, id: \.self) { i in
                Circle().fill(i < left ? UiColor.accent : Color(hex: 0xBDBDBD)).frame(width: 9, height: 9)
            }
            Text("\(left)/\(GameLogic.maxSplits)")
                .font(.system(size: 14, weight: .black))
                .foregroundColor(left == 0 ? Color(hex: 0x9E9E9E) : UiColor.accent)
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Capsule().fill(left == 0 ? Color(hex: 0xE0E0E0) : UiColor.accent.opacity(0.18)))
        .fixedSize()
    }
}

/// 이름표: 캐릭터 + 진영/이름 (차례면 금색으로 반짝)
struct PlayerTag: View {
    let side: Side
    let name: String
    let active: Bool
    var skin: Animal? = nil

    var body: some View {
        TimelineView(.animation) { tl in
            let glow = (sin(tl.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.4) + 1) / 2
            HStack(spacing: 6) {
                Critter(side: side, skin: skin).frame(width: 26, height: 26)
                Text("\(skin?.label ?? side.label) · \(name)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(UiColor.text)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 4)
            .background(Capsule().fill(active ? UiColor.gold.opacity(0.35 + glow * 0.4) : .white.opacity(0.6)))
        }
    }
}

/// 남은 턴 시간: 큰 숫자 + 줄어드는 게이지. 초록 → 주황 → 빨강, 마지막 5초는 깜빡임.
struct TurnTimerView: View {
    let deadline: Date
    let totalSec: Int
    let mine: Bool
    /// ❄️ 눈송이 · ✨ 황금 햄스터: 내 시간일 때 게이지 테마 (모습만)
    var skin: Animal? = nil

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.1)) { tl in
            let total = Double(totalSec)
            let left = max(0, min(total, deadline.timeIntervalSince(tl.date)))
            let fraction = total > 0 ? left / total : 0
            let urgent = left <= 5
            let color: Color = urgent ? UiColor.danger : (fraction > 0.5 ? Color(hex: 0x43A047) : Color(hex: 0xFF9800))
            let blink = urgent ? (Int(tl.date.timeIntervalSinceReferenceDate * 4) % 2 == 0 ? 1.0 : 0.3) : 1.0
            let theme = mine ? TimerTheme.of(skin) : nil
            let drift = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 2.4) / 2.4
            let leftSec = Int(left.rounded(.up))
            VStack(spacing: 4) {
                HStack(spacing: 8) {
                    Text(mine ? t("내 시간") : t("상대 시간"))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(UiColor.text.opacity(0.7))
                    Text("\(theme?.icon ?? "⏱") \(Int(left.rounded(.up)))")
                        .font(.system(size: urgent ? 40 : 32, weight: .black))
                        .foregroundColor(theme != nil && !urgent ? theme!.colors[2] : color)
                        .opacity(blink)
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.7))
                        if let th = theme, !urgent {
                            Capsule().fill(LinearGradient(colors: th.colors, startPoint: .leading, endPoint: .trailing))
                                .frame(width: geo.size.width * fraction).opacity(blink)
                        } else {
                            Capsule().fill(color).frame(width: geo.size.width * fraction).opacity(blink)
                        }
                    }
                }
                .frame(height: 12)
                .overlay {
                    // 게이지 위로 눈송이 / 금화가 흩날림
                    if let th = theme {
                        TimerFlakes(theme: th, fraction: CGFloat(fraction), drift: drift).frame(height: 26).offset(y: -2)
                    }
                }
            }
            .padding(.horizontal, 24)
            // ⏱ 내 시간 5초 남으면 1초마다 째깍
            .onChange(of: leftSec) { n in if mine && (1...5).contains(n) { Music.shared.sfx(.tick) } }
        }
    }
}

/// 🐰 게임 중 연승 리본 배지: 🎀n (5연승 이상은 금빛 🎀5+)
struct RibbonBadge: View {
    let n: Int
    var body: some View {
        if n > 0 {
            let gold = n >= Shop.rabbitMaxRibbons
            Text(gold ? "🎀\(Shop.rabbitMaxRibbons)+" : "🎀\(n)")
                .font(.system(size: 14, weight: .black))
                .foregroundColor(gold ? Color(hex: 0x6B4A00) : .white)
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(Capsule().fill(LinearGradient(
                    colors: gold ? [Color(hex: 0xFFE27A), Color(hex: 0xFFB300)] : [Color(hex: 0xFF7EB3), Color(hex: 0xFF5C8A)],
                    startPoint: .leading, endPoint: .trailing)))
                .fixedSize()
        }
    }
}

/// 이번 판에 남은 부활물약 (없으면 안 보임)
struct ReviveCounter: View {
    let left: Int
    var body: some View {
        if left > 0 {
            Text("💖×\(left)").font(.system(size: 14, weight: .black)).foregroundColor(Color(hex: 0xE0457B))
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(Capsule().fill(Color(hex: 0xFF5C8A, alpha: 0.2)))
                .fixedSize()
        }
    }
}
