import BubbleCore
import GoogleSignIn
import SwiftUI

@main
struct BubbleApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                // 구글 로그인 창에서 돌아올 때, 또는 친구 초대 링크(bubble://invite?code=…)
                .onOpenURL { url in
                    if !GIDSignIn.sharedInstance.handle(url) { OnlineService.shared.receiveInvite(url) }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL { OnlineService.shared.receiveInvite(url) }
                }
        }
    }
}

enum AppScreen {
    case lobby
    case shop
    case matching(Date)
    /// 친구 대전: 링크를 보내고 기다리는 중 (코드가 nil이면 방 만드는 중)
    case friendWait(String?, Date)
    case local(LocalGame)
    case online(OnlineGame)

    var key: String {
        switch self {
        case .lobby: return "lobby"
        case .shop: return "shop"
        case .matching: return "matching"
        case .friendWait: return "friendWait"
        case let .local(g): return "local-\(ObjectIdentifier(g).hashValue)"
        case let .online(g): return "online-\(g.gameId)"
        }
    }
}

struct RootView: View {
    @ObservedObject private var lang = LangStore.shared
    @ObservedObject private var online = OnlineService.shared
    @ObservedObject private var story = StoryState.shared
    @Environment(\.scenePhase) private var scenePhase
    @State private var screen: AppScreen = .lobby
    @State private var matchTask: Task<Void, Never>? = nil
    @State private var friendTask: Task<Void, Never>? = nil
    @State private var showVs = false
    @ObservedObject private var ads = Ads.shared
    /// 보상형 광고 안내 중: (uid, gameId, 돌려받는 점수)
    @State private var reward: (uid: String, gameId: String, points: Int)? = nil

    /// 패배 후 보상형 광고로 돌려받는 최대 점수 (서버 AD_REFUND_POINTS와 같음)
    static let adRefundPoints = 8

    var body: some View {
        BubbleBackground {
            switch screen {
            case .lobby:
                LobbyView(onStartAi: startAi, onRandomMatch: startMatching, onFriendBattle: startFriendBattle, onShop: { screen = .shop },
                          onTutorial: { screen = .local(LocalGame(tutorial: true)) }).transition(.opacity)
            case .shop:
                ShopView(onClose: { online.shopTour = false; screen = .lobby }).transition(.move(edge: .trailing))
            case let .matching(started):
                MatchingView(startedAt: started, onCancel: cancelMatching).transition(.opacity)
            case let .friendWait(code, started):
                FriendWaitView(code: code, startedAt: started, onCancel: cancelFriendBattle).transition(.opacity)
            case let .local(game):
                GameView(game: game,
                         onLeave: {
                             let finished = game.finished
                             game.stop()
                             // 🎓 튜토리얼을 끝내지 않고 나가면 건너뛴 것으로 (다시 안 띄움)
                             if game.tutorial != nil {
                                 if !finished { Task { await online.finishTutorial(completed: false) } }
                                 screen = .lobby
                                 return
                             }
                             if finished { Ads.shared.maybeShowInterstitial { screen = .lobby } } else { screen = .lobby }
                         },
                         onAgain: { game.stop(); screen = .local(LocalGame(level: game.level)) },
                         onTutorialShop: game.tutorial == nil ? nil : {
                             // 🎓 튜토리얼 끝 → 상점으로 들어가 캐릭터 안내
                             game.stop()
                             online.shopTour = true
                             screen = .shop
                         })
                    .overlay { TimeHamsterAlerts(mine: game.mySkin, theirs: game.opponentSkin, delay: 0.3) }
                    .transition(.opacity)
            case let .online(game):
                ZStack {
                    GameView(game: game,
                             onLeave: { leaveOnline(game) },
                             onAgain: againAction(game),
                             rewardOffer: refundOffer(game))
                    if showVs { VsIntro(game: game) { showVs = false }.transition(.opacity) }
                    // ⏱ 시계 햄스터를 썼으면 시작 때 알림 (VS 소개가 있으면 그다음에)
                    TimeHamsterAlerts(mine: game.mySkin, theirs: game.opponentSkin, delay: showVs ? 3.1 : 0.3)
                        .id(game.gameId + "\(game.mySkin?.rawValue ?? "")\(game.opponentSkin?.rawValue ?? "")")
                }
                .transition(.opacity)
            }
        }
        .overlay {
            if let r = reward {
                RewardIntro(points: r.points, onSkip: { reward = nil }, onWatch: {
                    reward = nil
                    Ads.shared.showRewarded(uid: r.uid, gameId: r.gameId, earned: {
                        online.show(t("📺 광고 보상 확인 중… 곧 점수가 돌아와요"))
                    }, done: {})
                })
            } else if let m = online.mission, screen.key == "lobby" {
                MissionCelebration(label: m.label, coins: m.coins) { online.mission = nil }
                    .transition(.opacity)
                    .onAppear { Music.shared.sfx(.mission) }
            } else if let c = online.checkin, screen.key == "lobby" {
                CheckinCelebration(day: c.day, coins: c.coins, potion: c.potion, panda: c.panda) { online.checkin = nil }
                    .transition(.opacity)
                    .onAppear { Music.shared.sfx(.checkin) }
            } else if let d = online.rankDown, screen.key == "lobby" {
                RankDownNotice(from: d.from, score: d.score) { online.rankDown = nil }
                    .transition(.opacity)
                    .onAppear { Music.shared.sfx(.rankDown) }
            } else if let score = online.rankUpScore, screen.key == "lobby" {
                RankUpCelebration(score: score) { online.rankUpScore = nil }
                    .onAppear { Music.shared.sfx(.rankUp) }
            }
        }
        // 🌈 독립 엔딩 · 📜 프롤로그 (전체 화면, 가장 위)
        .overlay {
            if online.landEnding, screen.key == "lobby" || screen.key == "shop" {
                LandEnding(name: online.profile?.name) { online.landEnding = false }.transition(.opacity)
            } else if story.showPrologue, screen.key == "lobby" {
                PrologueView { story.finish() }.transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: story.showPrologue)
        .animation(.easeInOut(duration: 0.3), value: online.landEnding)
        .animation(.easeInOut(duration: 0.3), value: screen.key)
        .animation(.easeInOut(duration: 0.3), value: showVs)
        // 아랍어는 오른쪽에서 왼쪽
        .environment(\.layoutDirection, lang.code.isRTL ? .rightToLeft : .leftToRight)
        .id(lang.code)
        .onAppear {
            Music.shared.playLobby()
            online.start()
            applyDemoArguments()
            Ads.shared.start()
            if online.uid != nil { PointStore.shared.start() }
        }
        .onChange(of: online.uid) { id in
            // 로그인하면 결제 상품·밀린 결제 처리
            if id != nil { PointStore.shared.start() }
        }
        // 친구 대전 링크: 로그인돼 있고 로비에 있으면 바로 대전방에 들어감
        .onChange(of: online.pendingBattle) { _ in joinPendingBattle() }
        .onChange(of: online.profile?.uid) { _ in joinPendingBattle() }
        // 🎵 로비 음악으로 고른 캐릭터를 산 게 확인되면 (로그인 직후 등) 그 곡으로
        .onChange(of: online.profile?.skins) { _ in
            if !screen.key.hasPrefix("local") && !screen.key.hasPrefix("online") && screen.key != "matching" && screen.key != "friendWait" { Music.shared.playLobby() }
        }
        // 🎵 앱이 뒤로 가면 음악을 멈춤
        .onChange(of: scenePhase) { phase in Music.shared.setActive(phase == .active) }
        .onChange(of: screen.key) { key in
            // 게임이 아닌 화면은 로비 곡 (게임 곡은 GameView가 정함)
            if key == "matching" || key == "friendWait" { Music.shared.play(Music.matching) }
            else if !key.hasPrefix("local") && !key.hasPrefix("online") { Music.shared.playLobby() }
            joinPendingBattle()
            // 로비로 돌아오면 랭킹 새로고침 (대전 결과 반영)
            if key == "lobby" { Task { await online.refreshLeaderboard() } }
        }
    }

    /// 로그인했으면 점수가 걸린 서버 AI 대전, 아니면 폰 안에서 연습
    private func startAi(_ level: AiLevel) {
        guard online.available, let uid = online.uid, online.profile != nil else {
            screen = .local(LocalGame(level: level))
            return
        }
        Task {
            do {
                let id = try await online.startAiGame(level)
                openOnline(id, uid: uid, fallback: false, intro: false)
            } catch {
                screen = .local(LocalGame(level: level))
            }
        }
    }

    private func startMatching() {
        guard let uid = online.uid else { return }
        screen = .matching(Date())
        matchTask = Task {
            do {
                if let id = try await online.findMatch() {
                    openOnline(id, uid: uid, fallback: false)
                } else {
                    // 30초 동안 사람 상대가 없으면 AI 어려움 (AI라고 밝힘)
                    let id = try await online.startAiGame(.hard)
                    openOnline(id, uid: uid, fallback: true)
                }
            } catch is CancellationError {
            } catch {
                online.error = error.localizedDescription
                screen = .lobby
            }
        }
    }

    /// 친구 대전방을 만들고 친구가 링크로 들어올 때까지 기다림 (최대 10분)
    private func startFriendBattle() {
        guard let uid = online.uid, online.profile != nil, friendTask == nil else { return }
        screen = .friendWait(nil, Date())
        friendTask = Task {
            var code: String? = nil
            defer { friendTask = nil }
            do {
                let c = try await online.createFriendRoom()
                code = c
                let started = Date()
                screen = .friendWait(c, started)
                while !Task.isCancelled {
                    try await Task.sleep(nanoseconds: 2_500_000_000)
                    guard let st = try? await online.pollFriendRoom(c) else { continue }
                    if let id = st.gameId {
                        code = nil
                        openOnline(id, uid: uid, fallback: false)
                        return
                    }
                    if st.expired || Date().timeIntervalSince(started) > OnlineService.friendWait {
                        code = nil
                        online.show(tr("친구가 오지 않아 대전방을 닫았어요", "Your friend didn't come, so the room was closed",
                                       zh: "朋友没有来，房间已关闭", es: "Tu amigo no llegó, así que se cerró la sala",
                                       fr: "Ton ami n’est pas venu, la salle est fermée", de: "Dein Freund kam nicht, der Raum wurde geschlossen",
                                       pt: "Seu amigo não veio, a sala foi fechada", ru: "Друг не пришёл, комната закрыта", ja: "友だちが来なかったのでルームを閉じました"))
                        screen = .lobby
                        return
                    }
                }
            } catch is CancellationError {
            } catch {
                online.error = error.localizedDescription
                screen = .lobby
            }
            if let c = code { await online.cancelFriendRoom(c) }
        }
    }

    private func cancelFriendBattle() {
        friendTask?.cancel()
        screen = .lobby
    }

    private func joinPendingBattle() {
        guard let code = online.pendingBattle, let uid = online.uid, online.profile != nil, screen.key == "lobby" else { return }
        online.pendingBattle = nil
        Task {
            do {
                let id = try await online.joinFriendRoom(code)
                openOnline(id, uid: uid, fallback: false)
            } catch {
                online.error = error.localizedDescription
            }
        }
    }

    /// 사람과의 대전에서 졌고 보상형 광고가 준비됐으면 돌려받을 점수
    private func refundOffer(_ game: OnlineGame) -> Int? {
        guard game.finished, !game.isAi, !game.isFriend, let w = game.winner, w != game.mySide, ads.rewardedReady,
              let d = game.scoreDelta, d < 0 else { return nil }
        return min(Self.adRefundPoints, -d)
    }

    /// 서버 대전에서 나가기: 진행 중이면 기권. 끝났으면 보상형 광고 안내 또는 전면 광고 후 로비로
    private func leaveOnline(_ game: OnlineGame) {
        let finished = game.finished
        let offer = refundOffer(game)
        game.leave()
        screen = .lobby
        if let offer, let uid = online.uid {
            reward = (uid, game.gameId, offer)
        } else if finished {
            Ads.shared.maybeShowInterstitial {}
        }
    }

    /// AI 대전이 끝났을 때 "한 판 더" (사람 상대면 없음)
    private func againAction(_ game: OnlineGame) -> (() -> Void)? {
        guard let level = game.aiLevel else { return nil }
        return {
            game.stop()
            startAi(level)
        }
    }

    private func cancelMatching() {
        matchTask?.cancel()
        matchTask = nil
        screen = .lobby
    }

    /// intro: VS 소개 화면 (직접 고른 AI 대전은 바로 시작)
    private func openOnline(_ id: String, uid: String, fallback: Bool, intro: Bool = true) {
        screen = .online(OnlineGame(gameId: id, uid: uid, fallbackAi: fallback))
        guard intro else { showVs = false; return }
        showVs = true
        Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            showVs = false
        }
    }

    /// CI 스크린샷용: "-lang ja", "-demo game"
    private func applyDemoArguments() {
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-lang"), i + 1 < args.count, let code = LangCode(rawValue: args[i + 1]) {
            lang.code = code
        }
        let demoArg = args.firstIndex(of: "-demo").flatMap { i in i + 1 < args.count ? args[i + 1] : nil }
        // CI 스크린샷은 프롤로그에 가리지 않게 (프롤로그 스크린샷만 띄움)
        if args.contains("-lang") || demoArg != nil { story.showPrologue = demoArg == "prologue" }
        guard let demo = demoArg else { return }
        let profile = Profile(uid: "demo", name: t("고양이") + "77", score: 1980, wins: 42, losses: 17, potions: 2, coins: 500,
                              boosts: 1, boostOn: true, animals: [.dog: 2], equipped: .dog, refCode: "ABC234", referrals: 3)
        switch demo {
        case "game":
            let g = LocalGame(level: .easy, mySide: .cat)
            g.setDemo(board: Board(cat: [4, 0], ham: [2, 3], catSplits: 1), log: [t("고양이") + ": " + setName(0) + " ✂️"])
            screen = .local(g)
        case "revive":
            let g = LocalGame(level: .easy, mySide: .cat)
            g.setDemo(board: Board(cat: [0, 3], ham: [2, 4], catSplits: 0, catRevives: 2), log: [t("햄스터") + ": " + t("💥 펑!")])
            screen = .local(g)
        case "lobby":
            online.setDemoProfile(profile)
        case "independent":
            // 🌈 땅을 산 독립 햄스터 로비 (칭호 배지)
            var p = profile
            p.land = true
            online.setDemoProfile(p)
        case "ending":
            online.setDemoProfile(profile)
            online.landEnding = true
        case "timealert":
            // ⏱ 내가 시계 햄스터 1, 상대가 시계 햄스터 3: 시작 알림 (모습은 기본 햄스터·고양이)
            let g = LocalGame(level: .easy, mySide: .cat)
            g.setDemo(board: Board(cat: [1, 1], ham: [1, 1]), log: [])
            g.mySkin = .sheep
            g.opponentSkin = .giraffe
            screen = .local(g)
        case "shop":
            online.setDemoProfile(profile)
            screen = .shop
        case "rankup":
            online.setDemoProfile(profile)
            online.rankUpScore = 2000
        case "checkin":
            online.setDemoProfile(profile)
            online.checkin = CheckinReward(day: 7, coins: 100, potion: true)
        case "mission":
            online.setDemoProfile(profile)
            online.mission = MissionReward(label: t("2판 이기기"), coins: 30)
        default:
            break
        }
    }
}
