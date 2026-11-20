package com.bubble.game.game

import kotlin.random.Random

enum class AiLevel(val label: String, val depth: Int, val mistakeRate: Double) {
    EASY("쉬움", 1, 0.45),
    NORMAL("보통", 4, 0.12),
    HARD("어려움", 9, 0.0),
}

/** 알파-베타 미니맥스 AI. 판이 작아서 깊게 읽어도 순식간에 끝납니다. */
class Ai(private val level: AiLevel, private val random: Random = Random.Default) {

    fun choose(board: Board, side: Side): Move {
        val moves = GameLogic.legalMoves(board, side)
        require(moves.isNotEmpty()) { "둘 수 있는 수가 없습니다" }
        if (random.nextDouble() < level.mistakeRate) return moves.random(random)

        var best = Int.MIN_VALUE
        val bestMoves = mutableListOf<Move>()
        for (m in moves) {
            val next = GameLogic.apply(board, side, m).board
            val score = -search(next, side.opponent, level.depth - 1, -WIN * 2, WIN * 2)
            if (score > best) {
                best = score
                bestMoves.clear()
                bestMoves += m
            } else if (score == best) {
                bestMoves += m
            }
        }
        return bestMoves.random(random)
    }

    /** [toMove] 입장에서의 점수 (네가맥스). */
    private fun search(board: Board, toMove: Side, depth: Int, alphaIn: Int, beta: Int): Int {
        board.winner()?.let { w -> return if (w == toMove) WIN + depth else -WIN - depth }
        if (depth <= 0) return evaluate(board, toMove)
        var alpha = alphaIn
        var best = Int.MIN_VALUE
        for (m in GameLogic.legalMoves(board, toMove)) {
            val next = GameLogic.apply(board, toMove, m).board
            val score = -search(next, toMove.opponent, depth - 1, -beta, -alpha)
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }

    /** 살아있는 세트 수가 중요하고, 4마리 세트는 터지기 직전이라 위험합니다. */
    private fun evaluate(board: Board, side: Side): Int {
        fun value(sets: List<Int>): Int {
            var v = 0
            for (n in sets) {
                if (n > 0) v += 100
                v += when (n) { 1 -> 6; 2 -> 10; 3 -> 8; 4 -> -4; else -> 0 }
            }
            return v
        }
        return value(board.sets(side)) - value(board.sets(side.opponent))
    }

    private companion object {
        const val WIN = 100_000
    }
}
