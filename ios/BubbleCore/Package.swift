// swift-tools-version:5.9
// 게임 규칙·AI·계급/상점 데이터. 앱(ios/Bubble)과 테스트가 함께 씁니다.
// 규칙은 안드로이드(app/.../game)와 서버(functions/src/game.ts)와 같아야 합니다.
import PackageDescription

let package = Package(
    name: "BubbleCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "BubbleCore", targets: ["BubbleCore"])],
    targets: [
        .target(name: "BubbleCore"),
        .testTarget(name: "BubbleCoreTests", dependencies: ["BubbleCore"]),
    ]
)
