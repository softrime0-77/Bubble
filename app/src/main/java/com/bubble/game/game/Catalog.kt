package com.bubble.game.game

/**
 * 계급. 1000점 이하는 흙, 이후 100점마다 한 단계씩 올라갑니다.
 * [sparkle]이 클수록 배지의 반짝임 효과가 화려해집니다.
 */
enum class Rank(val label: String, val emoji: String, val minScore: Int, val sparkle: Int) {
    DIRT("흙", "🟫", Int.MIN_VALUE, 0),
    COPPER("구리", "🥉", 1001, 1),
    SILVER("은", "🥈", 1101, 2),
    GOLD("금", "🥇", 1201, 3),
    DIAMOND("다이아", "💎", 1301, 4);

    companion object {
        fun of(score: Int): Rank = entries.last { score >= it.minScore }
    }
}

/**
 * 상점에서 파는 동물 캐릭터. 장착하면 상대의 턴 제한 시간이 줄어듭니다.
 * 값은 functions/src/catalog.ts와 같아야 합니다.
 */
enum class Animal(val label: String, val price: Int, val opponentTurnSec: Int) {
    SHEEP("양", 500, 20),
    DOG("강아지", 750, 15),
    GIRAFFE("기린", 1000, 10),
    ;

    companion object {
        fun of(id: String?): Animal? = entries.firstOrNull { it.name == id }
    }
}

object Shop {
    const val POTION_PRICE = 1000

    /** 한 판에서 쓸 수 있는 부활물약 최대 개수 */
    const val MAX_REVIVES_PER_GAME = 3

    const val DEFAULT_TURN_SEC = 30
}
