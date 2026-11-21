import XCTest
@testable import BubbleCore

final class GameLogicTests: XCTestCase {
    func testAttackAddsAttackerCount() throws {
        let b = Board(cat: [1, 2], ham: [1, 1])
        let r = try GameLogic.apply(b, .cat, .add(from: 1, toOpponent: true, to: 0))
        XCTAssertEqual(r.board.ham, [3, 1])
        XCTAssertNil(r.exploded)
    }

    func testFiveOrMoreExplodes() throws {
        let r = try GameLogic.apply(Board(cat: [4, 1], ham: [1, 3]), .cat, .add(from: 0, toOpponent: true, to: 1))
        XCTAssertEqual(r.board.ham, [1, 0])
        XCTAssertEqual(r.exploded, Explosion(side: .hamster, index: 1))
    }

    func testDeadSetsCannotBeBoostedOrAttacked() {
        let b = Board(cat: [3, 0], ham: [0, 2])
        XCTAssertFalse(GameLogic.isLegal(b, .cat, .add(from: 0, toOpponent: false, to: 1)))
        XCTAssertFalse(GameLogic.isLegal(b, .cat, .add(from: 0, toOpponent: true, to: 0)))
        XCTAssertTrue(GameLogic.isLegal(b, .cat, .add(from: 0, toOpponent: true, to: 1)))
    }

    func testSplitLimitedToThree() throws {
        var b = Board(cat: [2, 0], ham: [1, 1])
        b = try GameLogic.apply(b, .cat, .split(from: 0)).board
        XCTAssertEqual(b.cat, [1, 1])
        XCTAssertEqual(b.catSplits, 2)
        XCTAssertFalse(GameLogic.isLegal(Board(cat: [4, 0], ham: [1, 1], catSplits: 0), .cat, .split(from: 0)))
    }

    func testReviveUsesPotion() throws {
        let b = Board(cat: [3, 0], ham: [1, 1], catRevives: 1)
        let r = try GameLogic.apply(b, .cat, .revive(index: 1))
        XCTAssertEqual(r.board.cat, [3, 1])
        XCTAssertEqual(r.board.catRevives, 0)
    }

    func testWinnerWhenBothSetsPop() throws {
        let r = try GameLogic.apply(Board(cat: [4, 1], ham: [0, 1]), .cat, .add(from: 0, toOpponent: true, to: 1))
        XCTAssertEqual(r.board.winner, .cat)
        XCTAssertTrue(GameLogic.legalMoves(r.board, .hamster).isEmpty)
    }

    func testMoveEncodingMatchesServer() {
        XCTAssertEqual(Move.add(from: 1, toOpponent: true, to: 0).encoded, "A:1:1:0")
        for m in [Move.add(from: 0, toOpponent: false, to: 1), .split(from: 1), .revive(index: 0)] {
            XCTAssertEqual(Move.decode(m.encoded), m)
        }
        XCTAssertNil(Move.decode("A:2:1:0"))
    }

    func testHardAiTakesImmediateWin() throws {
        let b = Board(cat: [4, 0], ham: [0, 1])
        let m = Ai(level: .hard).choose(b, .cat)
        XCTAssertEqual(try GameLogic.apply(b, .cat, m).board.winner, .cat)
    }

    func testAiGamesAlwaysFinishOrStayLegal() throws {
        for g in 0..<20 {
            var b = Board()
            var side = Side.cat
            var turns = 0
            while b.winner == nil && turns < 200 {
                let lv = AiLevel.allCases[(g + turns) % 3]
                b = try GameLogic.apply(b, side, Ai(level: lv).choose(b, side)).board
                side = side.opponent
                turns += 1
            }
        }
    }

    func testRanksAndBonusMatchServer() {
        XCTAssertEqual(Rank.of(1000), .dirt)
        XCTAssertEqual(Rank.of(1001), .copper)
        XCTAssertEqual(Rank.of(1400), .copper)
        XCTAssertEqual(Rank.of(1401), .silver)
        XCTAssertEqual(Rank.of(2001), .gold)
        XCTAssertEqual(Rank.of(4001), .ruby)
        XCTAssertEqual(Rank.of(7001), .sapphire)
        XCTAssertEqual(Rank.of(10000), .sapphire)
        XCTAssertEqual(Rank.of(10001), .diamond)
        XCTAssertEqual(Rank.of(50000), .diamond)
        XCTAssertEqual(Rank.of(50001), .pinkDiamond)
        XCTAssertEqual(Rank.diamond.bonus(for: 30), 9)
        XCTAssertEqual(Rank.pinkDiamond.bonus(for: 30), 11)
        XCTAssertEqual(Rank.copper.bonus(for: 30), 2)
    }
}
