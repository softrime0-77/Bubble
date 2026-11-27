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
    private static let volume: Float = 0.3
    /// 큰 효과음(펑·승리 등)이 날 때 배경음을 잠깐 줄이는 크기
    private static let duckVolume: Float = 0.1

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
                self.node.volume = min(Self.volume, self.node.volume + 0.03)
                if self.node.volume >= Self.volume { t.invalidate() }
            }
        }
    }

    // MARK: 효과음 (tools/music/make_sfx.py로 합성)

    enum Sfx: String, CaseIterable {
        case select, shoot, hit, pop, split, potion, coin, win, lose, buy, equip, match
        case turn, tick, checkin, mission, weekly
        case rankUp = "rank_up", rankDown = "rank_down"
        case splitInk = "split_ink", splitIce = "split_ice", splitFire = "split_fire", splitMagic = "split_magic"
        case splitBamboo = "split_bamboo", splitBoing = "split_boing", splitGold = "split_gold"
        case coinDragon = "coin_dragon", coinRabbit = "coin_rabbit", coinUnicorn = "coin_unicorn"
        case markIce = "mark_ice", markGold = "mark_gold", markHeart = "mark_heart", markClaw = "mark_claw", markStamp = "mark_stamp"
        case markInk = "mark_ink", markSnow = "mark_snow", markBamboo = "mark_bamboo", markFire = "mark_fire", markHop = "mark_hop", markMagic = "mark_magic"
        var volume: Float {
            switch self {
            case .markGold, .markHeart, .markClaw, .markSnow, .markMagic: return 0.8
            case .markIce, .markInk, .markBamboo, .markFire, .markHop: return 0.85
            case .markStamp: return 0.9
            case .select, .tick: return 0.7
            case .shoot: return 0.75
            case .equip, .turn: return 0.8
            case .coin, .coinDragon: return 0.85
            case .hit, .split, .lose, .splitInk, .splitIce, .splitFire, .splitMagic, .splitBamboo, .splitBoing, .splitGold, .rankDown: return 0.9
            case .coinRabbit, .coinUnicorn, .mission: return 0.95
            case .pop, .potion, .win, .buy, .match, .checkin, .rankUp, .weekly: return 1.0
            }
        }

        /// 배경음을 잠깐 줄였다가 되돌릴 큰 효과음
        var loud: Bool {
            switch self {
            case .pop, .win, .lose, .potion, .match, .rankUp, .rankDown, .weekly, .checkin, .buy, .coinRabbit, .coinUnicorn: return true
            default: return false
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
        if s.loud { duck(s == .pop ? 0.6 : 1.2) }
    }

    /// 효과음이 묻히지 않게 배경음을 잠깐 줄였다가 천천히 되돌림
    private var duckUntil = Date.distantPast
    private func duck(_ sec: Double) {
        guard playing != nil else { return }
        fade?.invalidate()
        node.volume = Self.duckVolume
        let until = Date().addingTimeInterval(sec)
        duckUntil = until
        DispatchQueue.main.asyncAfter(deadline: .now() + sec) { [weak self] in
            guard let self, self.duckUntil == until else { return }
            self.fadeIn()
        }
    }

    static let lobby = "bgm_lobby"

    // MARK: 🎵 로비 음악 고르기 (산 캐릭터의 곡으로)

    private static let lobbyKey = "lobby_track"
    /// 로비 음악으로 고른 캐릭터 (nil이면 기본 로비 곡). 기기에 저장
    @Published private(set) var lobbyChoice: String? = UserDefaults.standard.string(forKey: "lobby_track")

    func chooseLobby(_ id: String?) {
        lobbyChoice = id
        UserDefaults.standard.set(id, forKey: Self.lobbyKey)
        playLobby()
    }

    /// 이 캐릭터의 음악을 쓸 수 있는지: 산 스킨 · 무료 햄스터 · 계급 유니콘
    static func owns(_ p: Profile?, _ a: Animal?) -> Bool {
        guard let a, a != .hamster else { return true }
        guard let p else { return false }
        if a.rankOnly { return p.score >= a.rankMin }
        if a.cosmetic { return p.skins.contains(a) }
        return false
    }

    /// 지금 로비 곡: 고른 캐릭터를 가지고 있으면 그 캐릭터 곡, 아니면 기본
    var lobbyTrack: String {
        guard let id = lobbyChoice else { return Self.lobby }
        if id == "CAT" { return "bgm_cat" }
        guard let a = Animal(rawValue: id), Self.owns(OnlineService.shared.profile, a) else { return Self.lobby }
        return Self.gameTrack(a, .hamster)
    }

    func playLobby() { play(lobbyTrack) }
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

/// 상점: 산 캐릭터의 음악을 로비 음악으로 (한 번 더 누르면 기본 곡으로)
struct LobbyMusicButton: View {
    let animal: Animal
    @ObservedObject private var music = Music.shared

    var body: some View {
        let on = music.lobbyChoice == animal.rawValue
        Button { music.chooseLobby(on ? nil : animal.rawValue) } label: {
            Text(on ? tr("🎵 로비에서 재생 중 · 기본 곡으로", "🎵 Playing in lobby · back to default", zh: "🎵 大厅播放中 · 换回默认", ja: "🎵 ロビーで再生中 · いつもの曲に戻す")
                    : tr("🎵 이 캐릭터 음악을 로비에서 듣기", "🎵 Use this song in the lobby", zh: "🎵 在大厅播放这首", ja: "🎵 この曲をロビーで流す"))
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(on ? UiColor.primaryDark : UiColor.accent)
        }
        .buttonStyle(.plain)
    }
}

/// 🎵 로비 음악 고르기 (곡을 누르면 바로 들려요)
struct MusicPickerSheet: View {
    let onClose: () -> Void
    @ObservedObject private var music = Music.shared
    @ObservedObject private var online = OnlineService.shared

    private var options: [(String?, String)] {
        var list: [(String?, String)] = [
            (nil, tr("🏠 기본 로비 곡", "🏠 Default lobby song", zh: "🏠 默认大厅音乐", ja: "🏠 いつものロビー曲")),
            ("CAT", tr("🐱 기본 고양이", "🐱 Basic cat", zh: "🐱 基础猫咪", ja: "🐱 ふつうのネコ")),
            (Animal.hamster.rawValue, "🐹 " + Animal.hamster.label),
        ]
        for a in Animal.allCases where (a.cosmetic && !a.retired) || a.rankOnly { list.append((a.rawValue, a.label)) }
        return list
    }

    var body: some View {
        sheetFrame(title: tr("🎵 로비 음악", "🎵 Lobby music", zh: "🎵 大厅音乐", ja: "🎵 ロビーの音楽"), onClose: onClose) {
            Text(tr("산 캐릭터의 음악을 로비에서 들을 수 있어요. 눌러서 바로 들어 보세요!", "Play any character's song you own in the lobby. Tap to listen!",
                    zh: "可以在大厅播放已拥有角色的音乐，点一下就能听！", ja: "買ったキャラの曲をロビーで流せるよ。タップですぐ聞ける！"))
                .font(.system(size: 13)).foregroundColor(UiColor.text.opacity(0.7))
            ForEach(Array(options.enumerated()), id: \.offset) { _, opt in
                row(opt.0, opt.1)
            }
        }
    }

    private func row(_ id: String?, _ label: String) -> some View {
        let a = id.flatMap { $0 == "CAT" ? nil : Animal(rawValue: $0) }
        let unlocked = id == nil || id == "CAT" || Music.owns(online.profile, a)
        let on = music.lobbyChoice == id
        return Button { if unlocked { music.chooseLobby(id) } } label: {
            HStack {
                Text(label).font(.system(size: 15, weight: on ? .black : .regular))
                    .foregroundColor(unlocked ? UiColor.text : UiColor.text.opacity(0.4))
                Spacer()
                Text(on ? "▶" : unlocked ? "" : tr("🔒 상점", "🔒 Shop", zh: "🔒 商店", ja: "🔒 ショップ"))
                    .font(.system(size: 13, weight: .bold)).foregroundColor(on ? UiColor.primaryDark : UiColor.text.opacity(0.45))
            }
            .padding(.horizontal, 10).padding(.vertical, 9)
            .background(RoundedRectangle(cornerRadius: 12).fill(on ? UiColor.primary.opacity(0.18) : .clear))
        }
        .buttonStyle(.plain)
        .disabled(!unlocked)
    }
}
