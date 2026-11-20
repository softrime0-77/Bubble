import BubbleCore
import SwiftUI

@main
struct BubbleApp: App {
    var body: some Scene {
        WindowGroup { RootView() }
    }
}

struct RootView: View {
    @ObservedObject private var lang = LangStore.shared
    @State private var game: LocalGame? = nil

    var body: some View {
        BubbleBackground {
            if let game {
                GameView(
                    game: game,
                    myName: t("나"),
                    onLeave: { game.stop(); self.game = nil },
                    onAgain: { game.stop(); self.game = LocalGame(level: game.level) }
                )
                .transition(.opacity)
            } else {
                LobbyView { level in self.game = LocalGame(level: level) }
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: game == nil)
        // 아랍어는 오른쪽에서 왼쪽
        .environment(\.layoutDirection, lang.code.isRTL ? .rightToLeft : .leftToRight)
        .id(lang.code)
        .onAppear(perform: applyDemoArguments)
    }

    /// CI 스크린샷용: 실행 인자 "-demo game"이면 예시 판으로 바로 게임 화면
    private func applyDemoArguments() {
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-lang"), i + 1 < args.count, let code = LangCode(rawValue: args[i + 1]) {
            lang.code = code
        }
        guard let i = args.firstIndex(of: "-demo"), i + 1 < args.count, args[i + 1] == "game" else { return }
        let g = LocalGame(level: .easy, mySide: .cat)
        g.setDemo(
            board: Board(cat: [4, 0], ham: [2, 3], catSplits: 1),
            log: [t("고양이") + ": " + setName(0) + " ✂️"]
        )
        game = g
    }
}
