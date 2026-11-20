package com.bubble.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameLogicTest {

    @Test
    fun attackAddsAttackerCount() {
        // 내 2번 세트(2마리)로 상대 1마리 세트를 공격하면 3마리
        val b = Board(cat = listOf(1, 2), ham = listOf(1, 1))
        val r = GameLogic.apply(b, Side.CAT, Move.Add(from = 1, toOpponent = true, to = 0))
        assertEquals(listOf(3, 1), r.board.ham)
        assertNull(r.exploded)
    }

    @Test
    fun reachingFiveExplodes() {
        // 내 1번 세트가 4마리면, 1마리 이상인 상대 세트는 공격받으면 터짐
        val b = Board(cat = listOf(4, 1), ham = listOf(1, 3))
        val r = GameLogic.apply(b, Side.CAT, Move.Add(0, true, 1))
        assertEquals(listOf(1, 0), r.board.ham)
        assertEquals(Side.HAMSTER to 1, r.exploded)
    }

    @Test
    fun boostOwnSet() {
        val b = Board(cat = listOf(2, 1), ham = listOf(1, 1))
        val r = GameLogic.apply(b, Side.CAT, Move.Add(0, false, 1))
        assertEquals(listOf(2, 3), r.board.cat)
    }

    @Test
    fun boostingOwnSetToFiveExplodesIt() {
        val b = Board(cat = listOf(3, 2), ham = listOf(1, 1))
        val r = GameLogic.apply(b, Side.CAT, Move.Add(0, false, 1))
        assertEquals(listOf(3, 0), r.board.cat)
    }

    @Test
    fun deadSetCannotBeBoostedOrAttacked() {
        val b = Board(cat = listOf(3, 0), ham = listOf(0, 2))
        assertFalse(GameLogic.isLegal(b, Side.CAT, Move.Add(0, false, 1)))
        assertFalse(GameLogic.isLegal(b, Side.CAT, Move.Add(0, true, 0)))
        assertFalse(GameLogic.isLegal(b, Side.CAT, Move.Add(1, true, 1)))
        assertTrue(GameLogic.isLegal(b, Side.CAT, Move.Add(0, true, 1)))
    }

    @Test
    fun splitRevivesDeadSet() {
        val four = Board(cat = listOf(4, 0), ham = listOf(1, 1))
        assertEquals(listOf(2, 2), GameLogic.apply(four, Side.CAT, Move.Split(0)).board.cat)
        val two = Board(cat = listOf(0, 2), ham = listOf(1, 1))
        assertEquals(listOf(1, 1), GameLogic.apply(two, Side.CAT, Move.Split(1)).board.cat)
    }

    @Test
    fun splitNeedsEvenAndEmptyOtherSet() {
        assertFalse(GameLogic.canSplit(Board(cat = listOf(3, 0)), Side.CAT, 0))
        assertFalse(GameLogic.canSplit(Board(cat = listOf(2, 1)), Side.CAT, 0))
        assertFalse(GameLogic.canSplit(Board(cat = listOf(0, 0)), Side.CAT, 0))
    }

    @Test
    fun splitIsLimitedToThreePerPlayer() {
        var b = Board(cat = listOf(2, 0), ham = listOf(1, 1))
        assertEquals(3, b.splitsLeft(Side.CAT))
        b = GameLogic.apply(b, Side.CAT, Move.Split(0)).board
        assertEquals(2, b.splitsLeft(Side.CAT))
        assertEquals(3, b.splitsLeft(Side.HAMSTER))

        val noneLeft = Board(cat = listOf(4, 0), ham = listOf(1, 1), catSplits = 0)
        assertFalse(GameLogic.isLegal(noneLeft, Side.CAT, Move.Split(0)))
        assertTrue(GameLogic.splitShapeOk(noneLeft, Side.CAT, 0))
        assertTrue(GameLogic.legalMoves(noneLeft, Side.CAT).none { it is Move.Split })

        val oneLeft = noneLeft.copy(catSplits = 1)
        val after = GameLogic.apply(oneLeft, Side.CAT, Move.Split(0)).board
        assertEquals(listOf(2, 2), after.cat)
        assertEquals(0, after.splitsLeft(Side.CAT))
    }

    @Test
    fun revivePotionBringsBackDeadSetOnce() {
        val b = Board(cat = listOf(3, 0), ham = listOf(1, 1), catRevives = 1)
        assertTrue(GameLogic.isLegal(b, Side.CAT, Move.Revive(1)))
        assertFalse(GameLogic.isLegal(b, Side.CAT, Move.Revive(0)))
        val after = GameLogic.apply(b, Side.CAT, Move.Revive(1)).board
        assertEquals(listOf(3, 1), after.cat)
        assertEquals(0, after.revivesLeft(Side.CAT))
        assertFalse(GameLogic.isLegal(after.copy(cat = listOf(3, 0)), Side.CAT, Move.Revive(1)))
        assertFalse(GameLogic.isLegal(Board(cat = listOf(3, 0)), Side.CAT, Move.Revive(1)))
        assertEquals(Move.Revive(1), Move.decode(Move.Revive(1).encode()))
    }

    @Test
    fun losingBothSetsLoses() {
        val b = Board(cat = listOf(4, 1), ham = listOf(0, 1))
        val r = GameLogic.apply(b, Side.CAT, Move.Add(0, true, 1))
        assertEquals(Side.CAT, r.board.winner())
        assertTrue(GameLogic.legalMoves(r.board, Side.HAMSTER).isEmpty())
    }

    @Test
    fun moveEncodingRoundTrips() {
        val moves = listOf(Move.Add(1, true, 0), Move.Add(0, false, 1), Move.Split(1))
        moves.forEach { assertEquals(it, Move.decode(it.encode())) }
    }

    @Test
    fun aiTakesImmediateWin() {
        val b = Board(cat = listOf(4, 0), ham = listOf(0, 1))
        val m = Ai(AiLevel.HARD, Random(1)).choose(b, Side.CAT)
        assertEquals(Side.CAT, GameLogic.apply(b, Side.CAT, m).board.winner())
    }

    @Test
    fun aiGamesAlwaysHaveLegalMoves() {
        val rnd = Random(42)
        repeat(50) {
            var board = Board()
            var side = Side.CAT
            var turns = 0
            while (board.winner() == null && turns < 300) {
                val level = AiLevel.entries[rnd.nextInt(AiLevel.entries.size)]
                val move = Ai(level, rnd).choose(board, side)
                board = GameLogic.apply(board, side, move).board
                side = side.opponent
                turns++
            }
            if (board.winner() != null) assertNotNull(board.winner())
        }
    }
}
