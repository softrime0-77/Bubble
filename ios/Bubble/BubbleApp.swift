import BubbleCore
import GoogleSignIn
import SwiftUI

@main
struct BubbleApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                // 구글 로그인 창에서 돌아올 때
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}

enum AppScreen {
    case lobby
    case matching(Date)
    case local(LocalGame)
    case online(OnlineGame)

    var key: String {
        switch self {
        case .lobby: return "lobby"
        case .matching: return "matching"
        case let .local(g): return "local-\(ObjectIdentifier(g).hashValue)"
        case let .online(g): return "online-\(g.gameId)"
        }
    }
}

struct RootView: View {
    @ObservedObject private var lang = LangStore.shared
    @ObservedObject private var online = OnlineService.shared
    @State private var screen: AppScreen = .lobby
    @State private var matchTask: Task<Void, Never>? = nil
    @State private var showVs = false

    var body: some View {
        BubbleBackground {
            switch screen {
            case .lobby:
                LobbyView(onStartAi: startAi, onRandomMatch: startMatching).transition(.opacity)
            case let .matching(started):
                MatchingView(startedAt: started, onCancel: cancelMatching).transition(.opacity)
            case let .local(game):
                GameView(game: game,
                         onLeave: { game.stop(); screen = .lobby },
                         onAgain: { game.stop(); screen = .local(LocalGame(level: game.level)) })
                    .transition(.opacity)
            case let .online(game):
                ZStack {
                    GameView(game: game,
                             onLeave: { game.leave(); screen = .lobby },
                             onAgain: againAction(game))
                    if showVs { VsIntro(game: game) { showVs = false }.transition(.opacity) }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: screen.key)
        .animation(.easeInOut(duration: 0.3), value: showVs)
        // 아랍어는 오른쪽에서 왼쪽
        .environment(\.layoutDirection, lang.code.isRTL ? .rightToLeft : .leftToRight)
        .id(lang.code)
        .onAppear {
            online.start()
            applyDemoArguments()
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
                openOnline(id, uid: uid, fallback: false)
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

    private func openOnline(_ id: String, uid: String, fallback: Bool) {
        screen = .online(OnlineGame(gameId: id, uid: uid, fallbackAi: fallback))
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
        guard let i = args.firstIndex(of: "-demo"), i + 1 < args.count, args[i + 1] == "game" else { return }
        let g = LocalGame(level: .easy, mySide: .cat)
        g.setDemo(board: Board(cat: [4, 0], ham: [2, 3], catSplits: 1), log: [t("고양이") + ": " + setName(0) + " ✂️"])
        screen = .local(g)
    }
}
