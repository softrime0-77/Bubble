package com.bubble.game.ui

import com.bubble.game.game.GameLogic
import com.bubble.game.game.Move
import com.bubble.game.game.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 🎓 튜토리얼 각본: 내 수와 상대 반격이 모두 규칙에 맞고, 마지막 수로 내가 이겨야 함 */
class TutorialScriptTest {
    @Test
    fun scriptIsLegalAndEndsInWin() {
        var board = TutorialScript.board
        assertEquals(TutorialScript.moves.size, TutorialScript.replies.size)
        TutorialScript.moves.forEachIndexed { i, m ->
            assertTrue("내 수 $i: $m", GameLogic.isLegal(board, Side.CAT, m))
            board = GameLogic.apply(board, Side.CAT, m).board
            val r = TutorialScript.replies[i]
            if (i == TutorialScript.moves.lastIndex) {
                assertNull(r)
                assertEquals(Side.CAT, board.winner())
            } else {
                assertNull("아직 안 끝나야 함 ($i)", board.winner())
                requireNotNull(r)
                assertTrue("상대 수 $i: $r", GameLogic.isLegal(board, Side.HAMSTER, r))
                board = GameLogic.apply(board, Side.HAMSTER, r).board
            }
        }
    }

    @Test
    fun teachesSplitAndRevive() {
        assertTrue(TutorialScript.moves.any { it is Move.Split })
        assertTrue(TutorialScript.moves.any { it is Move.Revive })
    }
}
