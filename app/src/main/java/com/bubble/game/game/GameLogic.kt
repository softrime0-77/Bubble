package com.bubble.game.game

/** 진영: 고양이 또는 햄스터. 고양이가 항상 먼저 둡니다. */
enum class Side {
    CAT, HAMSTER;

    val opponent: Side get() = if (this == CAT) HAMSTER else CAT
    val label: String get() = if (this == CAT) "고양이" else "햄스터"
}

/**
 * 한 수.
 * - [Add]: 내 세트 [from]의 마리 수만큼, 상대 세트 또는 내 다른 세트 [to]에 더함
 * - [Split]: 짝수 마리인 내 세트 [from]을 반으로 나눠, 비어 있는(0) 다른 세트를 살림
 */
sealed class Move {
    data class Add(val from: Int, val toOpponent: Boolean, val to: Int) : Move()
    data class Split(val from: Int) : Move()

    fun encode(): String = when (this) {
        is Add -> "A:$from:${if (toOpponent) 1 else 0}:$to"
        is Split -> "S:$from"
    }

    companion object {
        fun decode(s: String): Move? {
            val p = s.split(":")
            return when (p.firstOrNull()) {
                "A" -> if (p.size == 4) Add(p[1].toInt(), p[2] == "1", p[3].toInt()) else null
                "S" -> if (p.size == 2) Split(p[1].toInt()) else null
                else -> null
            }
        }
    }
}

/** 수를 둔 결과. 터진 세트를 화면 애니메이션에 쓰기 위해 함께 돌려줍니다. */
data class MoveResult(
    val board: Board,
    val exploded: Pair<Side, Int>?,
    val description: String,
)

/** 판 상태. 각 진영은 세트 2개(인덱스 0, 1)와 남은 분할 횟수를 가집니다. */
data class Board(
    val cat: List<Int> = listOf(1, 1),
    val ham: List<Int> = listOf(1, 1),
    val catSplits: Int = GameLogic.MAX_SPLITS,
    val hamSplits: Int = GameLogic.MAX_SPLITS,
) {
    fun sets(side: Side): List<Int> = if (side == Side.CAT) cat else ham

    fun splitsLeft(side: Side): Int = if (side == Side.CAT) catSplits else hamSplits

    fun withSplitsLeft(side: Side, n: Int): Board =
        if (side == Side.CAT) copy(catSplits = n) else copy(hamSplits = n)

    fun with(side: Side, sets: List<Int>): Board =
        if (side == Side.CAT) copy(cat = sets) else copy(ham = sets)

    fun isDefeated(side: Side): Boolean = sets(side).all { it == 0 }

    /** 승자. 아직 끝나지 않았으면 null. */
    fun winner(): Side? = when {
        isDefeated(Side.CAT) -> Side.HAMSTER
        isDefeated(Side.HAMSTER) -> Side.CAT
        else -> null
    }
}

object GameLogic {
    const val SET_COUNT = 2
    const val EXPLODE_AT = 5

    /** 한 게임에서 한 사람이 쓸 수 있는 분할 횟수. */
    const val MAX_SPLITS = 3

    fun setName(i: Int) = "${i + 1}번 세트"

    /** 분할 모양 조건: 세트가 짝수(2 이상)이고 다른 세트가 0일 때. (횟수는 따지지 않음) */
    fun splitShapeOk(board: Board, side: Side, from: Int): Boolean {
        val mine = board.sets(side)
        val n = mine[from]
        return n > 0 && n % 2 == 0 && mine[1 - from] == 0
    }

    /** 분할 가능 여부: 모양 조건 + 남은 분할 횟수가 있을 때. */
    fun canSplit(board: Board, side: Side, from: Int): Boolean =
        board.splitsLeft(side) > 0 && splitShapeOk(board, side, from)

    fun isLegal(board: Board, side: Side, move: Move): Boolean {
        if (board.winner() != null) return false
        val mine = board.sets(side)
        return when (move) {
            is Move.Add -> {
                if (move.from !in 0 until SET_COUNT || move.to !in 0 until SET_COUNT) return false
                if (mine[move.from] <= 0) return false
                if (move.toOpponent) {
                    board.sets(side.opponent)[move.to] > 0
                } else {
                    // 내 다른 세트 강화: 0개(죽은) 세트는 분할로만 살릴 수 있음
                    move.to != move.from && mine[move.to] > 0
                }
            }
            is Move.Split -> move.from in 0 until SET_COUNT && canSplit(board, side, move.from)
        }
    }

    fun legalMoves(board: Board, side: Side): List<Move> {
        if (board.winner() != null) return emptyList()
        val moves = mutableListOf<Move>()
        for (from in 0 until SET_COUNT) {
            for (to in 0 until SET_COUNT) {
                moves += Move.Add(from, true, to)
                moves += Move.Add(from, false, to)
            }
            moves += Move.Split(from)
        }
        return moves.filter { isLegal(board, side, it) }
    }

    /** 수를 적용합니다. 규칙에 어긋난 수면 IllegalArgumentException. */
    fun apply(board: Board, side: Side, move: Move): MoveResult {
        require(isLegal(board, side, move)) { "규칙에 맞지 않는 수: $move" }
        val who = side.label
        return when (move) {
            is Move.Add -> {
                val power = board.sets(side)[move.from]
                val targetSide = if (move.toOpponent) side.opponent else side
                val target = board.sets(targetSide).toMutableList()
                val sum = target[move.to] + power
                val boom = sum >= EXPLODE_AT
                target[move.to] = if (boom) 0 else sum
                val targetName = (if (move.toOpponent) "상대 " else "내 ") + setName(move.to)
                val verb = if (move.toOpponent) "공격" else "강화"
                val desc = "$who: ${setName(move.from)}(${power})로 $targetName $verb" +
                    if (boom) " → 💥 펑!" else " → ${sum}마리"
                MoveResult(
                    board = board.with(targetSide, target),
                    exploded = if (boom) targetSide to move.to else null,
                    description = desc,
                )
            }
            is Move.Split -> {
                val half = board.sets(side)[move.from] / 2
                val left = board.splitsLeft(side) - 1
                MoveResult(
                    board = board.with(side, listOf(half, half)).withSplitsLeft(side, left),
                    exploded = null,
                    description = "$who: ${setName(move.from)} 분할 → ${half}마리, ${half}마리 ✂️ (분할 -1, 남은 ${left}회)",
                )
            }
        }
    }
}
