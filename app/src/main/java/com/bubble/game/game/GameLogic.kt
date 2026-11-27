package com.bubble.game.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bubble.game.i18n.tr

/**
 * 화면에 보이는 진영. 세계관상 "나"는 늘 햄스터, 상대는 늘 고양이로 보여줍니다.
 * 규칙(고양이 진영이 먼저 둠)과 서버 데이터는 그대로이고, 그림·이름만 바꿔 보여줘요.
 * 대전 화면에 들어가면 [mySide]가 정해지고, 나오면 null(그대로 보여줌).
 */
object SideLook {
    var mySide: Side? by androidx.compose.runtime.mutableStateOf(null)

    /** [side]가 화면에서 어떤 동물로 보이는지 */
    fun of(side: Side): Side = mySide?.let { if (side == it) Side.HAMSTER else Side.CAT } ?: side
}

/** 진영: 고양이 또는 햄스터. 고양이 진영이 항상 먼저 둡니다 (화면에서는 [SideLook]으로 "나 = 햄스터"). */
enum class Side {
    CAT, HAMSTER;

    val opponent: Side get() = if (this == CAT) HAMSTER else CAT
    /** 화면용 이름 ([SideLook] 적용) */
    val label: String get() = if (SideLook.of(this) == CAT) tr("고양이", "Cat", zh = "猫咪", hi = "बिल्ली", es = "Gato", fr = "Chat", ar = "قطة", de = "Katze", pt = "Gato", ru = "Котик", ja = "ネコ") else tr("햄스터", "Hamster", zh = "仓鼠", hi = "हैम्स्टर", es = "Hámster", fr = "Hamster", ar = "هامستر", de = "Hamster", pt = "Hamster", ru = "Хомяк", ja = "ハムスター")
    val labelKo: String get() = if (this == CAT) "고양이" else "햄스터"
    val labelEn: String get() = if (this == CAT) "Cat" else "Hamster"
}

/**
 * 한 수.
 * - [Add]: 내 세트 [from]의 마리 수만큼, 상대 세트 또는 내 다른 세트 [to]에 더함
 * - [Split]: 짝수 마리인 내 세트 [from]을 반으로 나눠, 비어 있는(0) 다른 세트를 살림
 */
sealed class Move {
    data class Add(val from: Int, val toOpponent: Boolean, val to: Int) : Move()
    data class Split(val from: Int) : Move()

    /** 부활물약: 내 죽은(0마리) 세트 [index]를 1마리로 살림. 차례를 씀. */
    data class Revive(val index: Int) : Move()

    fun encode(): String = when (this) {
        is Add -> "A:$from:${if (toOpponent) 1 else 0}:$to"
        is Split -> "S:$from"
        is Revive -> "R:$index"
    }

    companion object {
        fun decode(s: String): Move? {
            val p = s.split(":")
            return when (p.firstOrNull()) {
                "A" -> if (p.size == 4) Add(p[1].toInt(), p[2] == "1", p[3].toInt()) else null
                "S" -> if (p.size == 2) Split(p[1].toInt()) else null
                "R" -> if (p.size == 2) Revive(p[1].toInt()) else null
                else -> null
            }
        }
    }
}

/** 수를 둔 결과. 터진 세트를 화면 애니메이션에 쓰기 위해 함께 돌려줍니다. */
data class MoveResult(
    val board: Board,
    val exploded: Pair<Side, Int>?,
    /** 한국어 설명 */
    val description: String,
    /** 영어 설명 */
    val descriptionEn: String = description,
    /** 화면 언어로 설명을 만들 정보 */
    val info: MoveInfo? = null,
) {
    /** 현재 화면 언어의 설명 */
    val localized: String get() = info?.let { MoveText.describe(it) } ?: tr(description, descriptionEn)
}

/** 판 상태. 각 진영은 세트 2개(인덱스 0, 1)와 남은 분할 횟수를 가집니다. */
data class Board(
    val cat: List<Int> = listOf(1, 1),
    val ham: List<Int> = listOf(1, 1),
    val catSplits: Int = GameLogic.MAX_SPLITS,
    val hamSplits: Int = GameLogic.MAX_SPLITS,
    /** 이번 판에서 쓸 수 있는 부활물약 수 (가진 물약이 있으면 1, 없으면 0) */
    val catRevives: Int = 0,
    val hamRevives: Int = 0,
) {
    fun revivesLeft(side: Side): Int = if (side == Side.CAT) catRevives else hamRevives

    fun withRevivesLeft(side: Side, n: Int): Board =
        if (side == Side.CAT) copy(catRevives = n) else copy(hamRevives = n)

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

    fun setName(i: Int) = tr("${i + 1}번 세트", "Set ${i + 1}", zh = "第 ${i + 1} 组", hi = "सेट ${i + 1}", es = "Grupo ${i + 1}", fr = "Groupe ${i + 1}", ar = "المجموعة ${i + 1}", de = "Gruppe ${i + 1}", pt = "Grupo ${i + 1}", ru = "Группа ${i + 1}", ja = "${i + 1}番セット")
    private fun setKo(i: Int) = "${i + 1}번 세트"
    private fun setEn(i: Int) = "Set ${i + 1}"

    /** 분할 모양 조건: 세트가 짝수(2 이상)이고 다른 세트가 0일 때. (횟수는 따지지 않음) */
    fun splitShapeOk(board: Board, side: Side, from: Int): Boolean {
        val mine = board.sets(side)
        val n = mine[from]
        return n > 0 && n % 2 == 0 && mine[1 - from] == 0
    }

    /** 부활 가능 여부: 물약이 남았고, 그 세트가 죽었고, 다른 세트는 살아있을 때. */
    fun canRevive(board: Board, side: Side, index: Int): Boolean {
        val mine = board.sets(side)
        return board.revivesLeft(side) > 0 && mine[index] == 0 && mine[1 - index] > 0
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
            is Move.Revive -> move.index in 0 until SET_COUNT && canRevive(board, side, move.index)
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
            moves += Move.Revive(from)
        }
        return moves.filter { isLegal(board, side, it) }
    }

    /** 수를 적용합니다. 규칙에 어긋난 수면 IllegalArgumentException. */
    fun apply(board: Board, side: Side, move: Move): MoveResult {
        require(isLegal(board, side, move)) { "규칙에 맞지 않는 수: $move" }
        val who = side.labelKo
        val whoEn = side.labelEn
        return when (move) {
            is Move.Add -> {
                val power = board.sets(side)[move.from]
                val targetSide = if (move.toOpponent) side.opponent else side
                val target = board.sets(targetSide).toMutableList()
                val sum = target[move.to] + power
                val boom = sum >= EXPLODE_AT
                target[move.to] = if (boom) 0 else sum
                val targetKo = (if (move.toOpponent) "상대 " else "내 ") + setKo(move.to)
                val verbKo = if (move.toOpponent) "공격" else "강화"
                val desc = "$who: ${setKo(move.from)}(${power})로 $targetKo $verbKo" +
                    if (boom) " → 💥 펑!" else " → ${sum}마리"
                val targetEn = (if (move.toOpponent) "their " else "own ") + setEn(move.to)
                val verbEn = if (move.toOpponent) "attacks" else "boosts"
                val descEn = "$whoEn: ${setEn(move.from)} (${power}) $verbEn $targetEn" +
                    if (boom) " → 💥 POP!" else " → ${sum}"
                MoveResult(
                    board = board.with(targetSide, target),
                    exploded = if (boom) targetSide to move.to else null,
                    description = desc,
                    descriptionEn = descEn,
                    info = MoveInfo(side, move, power, sum, boom, board.splitsLeft(side)),
                )
            }
            is Move.Revive -> {
                val sets = board.sets(side).toMutableList()
                sets[move.index] = 1
                MoveResult(
                    board = board.with(side, sets).withRevivesLeft(side, board.revivesLeft(side) - 1),
                    exploded = null,
                    description = "$who: 💖 부활물약으로 ${setKo(move.index)} 부활!",
                    descriptionEn = "$whoEn: 💖 revived ${setEn(move.index)} with a potion!",
                    info = MoveInfo(side, move, 0, 0, false, board.splitsLeft(side)),
                )
            }
            is Move.Split -> {
                val half = board.sets(side)[move.from] / 2
                val left = board.splitsLeft(side) - 1
                MoveResult(
                    board = board.with(side, listOf(half, half)).withSplitsLeft(side, left),
                    exploded = null,
                    description = "$who: ${setKo(move.from)} 분할 → ${half}마리, ${half}마리 ✂️ (분할 -1, 남은 ${left}회)",
                    descriptionEn = "$whoEn: split ${setEn(move.from)} → $half, $half ✂️ (split -1, $left left)",
                    info = MoveInfo(side, move, half * 2, half, false, left),
                )
            }
        }
    }
}
