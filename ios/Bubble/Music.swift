import AVFoundation
import BubbleCore
import SwiftUI

/// 배경음악. 로비와 게임(내 캐릭터별)에 다른 곡을 이음매 없이 반복 재생합니다.
/// 곡은 tools/music/make_music.py로 직접 합성한 것이라 저작권 걱정이 없습니다.
/// 무음 스위치를 따르고(.ambient) 다른 앱 소리와 섞이며, 켜고 끄기는 기기에 저장합니다.
@MainActor
final class Music: ObservableObject {
    static let shared = Music()
    private static let key = "music_on"
    private static let volume: Float = 0.45

    @Published private(set) var enabled: Bool

    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private var buffers: [String: AVAudioPCMBuffer] = [:]
    private var playing: String?
    private var wanted: String?
    private var active = true
    private var fade: Timer?

    private init() {
        enabled = UserDefaults.standard.object(forKey: Self.key) as? Bool ?? true
        engine.attach(node)
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.restart() }
        }
    }

    func toggle() {
        enabled.toggle()
        UserDefaults.standard.set(enabled, forKey: Self.key)
        apply()
    }

    /// 이 곡을 틀어 둠 (이미 같은 곡이면 그대로)
    func play(_ name: String) {
        wanted = name
        apply()
    }

    /// 앱이 뒤로 가면 멈추고, 돌아오면 다시
    func setActive(_ on: Bool) {
        active = on
        if on { restart() } else { apply() }
    }

    private func restart() {
        playing = nil
        apply()
    }

    private func buffer(_ name: String) -> AVAudioPCMBuffer? {
        if let b = buffers[name] { return b }
        guard let url = Bundle.main.url(forResource: name, withExtension: "m4a"),
              let file = try? AVAudioFile(forReading: url),
              let buf = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)),
              (try? file.read(into: buf)) != nil else { return nil }
        buffers[name] = buf
        return buf
    }

    private func apply() {
        guard enabled, active, let name = wanted, let buf = buffer(name) else {
            fade?.invalidate()
            node.pause()
            return
        }
        if playing != name {
            node.stop()
            engine.disconnectNodeOutput(node)
            engine.connect(node, to: engine.mainMixerNode, format: buf.format)
            node.scheduleBuffer(buf, at: nil, options: .loops)
            node.volume = 0
            playing = name
        }
        if !engine.isRunning {
            try? AVAudioSession.sharedInstance().setActive(true)
            do { try engine.start() } catch { playing = nil; return }
        }
        if !node.isPlaying { node.play() }
        fadeIn()
    }

    /// 0에서 살짝 커지며 시작 (곡이 바뀔 때 툭 끊기지 않게)
    private func fadeIn() {
        fade?.invalidate()
        fade = Timer.scheduledTimer(withTimeInterval: 0.04, repeats: true) { [weak self] t in
            Task { @MainActor in
                guard let self else { t.invalidate(); return }
                self.node.volume = min(Self.volume, self.node.volume + 0.05)
                if self.node.volume >= Self.volume { t.invalidate() }
            }
        }
    }

    // MARK: 효과음 (tools/music/make_sfx.py로 합성)

    enum Sfx: String, CaseIterable {
        case select, shoot, hit, pop, split, potion, coin, win, lose, buy, equip, match
        case splitInk = "split_ink", splitIce = "split_ice", splitFire = "split_fire", splitMagic = "split_magic"
        case splitBamboo = "split_bamboo", splitBoing = "split_boing", splitGold = "split_gold"
        case coinDragon = "coin_dragon", coinRabbit = "coin_rabbit", coinUnicorn = "coin_unicorn"
        var volume: Float {
            switch self {
            case .select: return 0.5
            case .match: return 0.8
            case .equip: return 0.6
            case .buy: return 0.8
            case .shoot: return 0.55
            case .coin: return 0.6
            case .coinDragon: return 0.65
            case .hit, .split, .lose, .splitInk, .splitIce, .splitFire, .splitMagic, .splitBamboo, .splitBoing, .splitGold, .coinRabbit, .coinUnicorn: return 0.7
            case .potion, .win: return 0.8
            case .pop: return 0.9
            }
        }
    }

    /// 캐릭터별 분할 소리
    static func splitSfx(_ skin: Animal?) -> Sfx {
        switch skin {
        case .octopus: return .splitInk
        case .penguin, .snowHamster: return .splitIce
        case .dragon: return .splitFire
        case .unicorn, .pinkCat: return .splitMagic
        case .panda: return .splitBamboo
        case .rabbit: return .splitBoing
        case .luckyCat, .goldHamster: return .splitGold
        default: return .split
        }
    }

    /// 캐릭터별 💰 받는 소리 (게임 중: 복냥이·아기 용)
    static func coinSfx(_ skin: Animal?) -> Sfx { skin == .dragon ? .coinDragon : .coin }

    /// 효과음마다 플레이어 3개를 돌려 써서 빠르게 겹쳐도 끊기지 않게
    private var sfxPlayers: [Sfx: [AVAudioPlayer]] = [:]
    private var sfxTurn: [Sfx: Int] = [:]

    func sfx(_ s: Sfx) {
        guard enabled, active else { return }
        if sfxPlayers[s] == nil {
            guard let url = Bundle.main.url(forResource: "sfx_" + s.rawValue, withExtension: "m4a") else { return }
            sfxPlayers[s] = (0..<3).compactMap { _ in
                let p = try? AVAudioPlayer(contentsOf: url)
                p?.volume = s.volume
                p?.prepareToPlay()
                return p
            }
        }
        guard let list = sfxPlayers[s], !list.isEmpty else { return }
        let i = (sfxTurn[s] ?? 0) % list.count
        sfxTurn[s] = i + 1
        let p = list[i]
        p.currentTime = 0
        p.play()
    }

    static let lobby = "bgm_lobby"
    /// 매칭·친구 기다리는 동안
    static let matching = "bgm_matching"

    /// 게임 곡: 내 캐릭터별. 소모품 동물(양·강아지·기린)이나 기본이면 고양이/햄스터 곡
    static func gameTrack(_ skin: Animal?, _ side: Side) -> String {
        switch skin {
        case .pinkCat: return "bgm_pink_cat"
        case .blackCat: return "bgm_black_cat"
        case .snowHamster: return "bgm_snow_hamster"
        case .goldHamster: return "bgm_gold_hamster"
        case .luckyCat: return "bgm_lucky_cat"
        case .octopus: return "bgm_octopus"
        case .penguin: return "bgm_penguin"
        case .panda: return "bgm_panda"
        case .dragon: return "bgm_dragon"
        case .rabbit: return "bgm_rabbit"
        case .unicorn: return "bgm_unicorn"
        case .hamster: return "bgm_hamster"
        default: return side == .cat ? "bgm_cat" : "bgm_hamster"
        }
    }
}

/// 🔊/🔇 배경음악 켜고 끄기
struct MusicToggle: View {
    @ObservedObject private var music = Music.shared

    var body: some View {
        Button { music.toggle() } label: {
            Text(music.enabled ? "🔊" : "🔇").font(.system(size: 18))
                .padding(.horizontal, 10).padding(.vertical, 4)
                .background(Capsule().fill(.white.opacity(0.75)))
        }
        .buttonStyle(.plain)
    }
}
