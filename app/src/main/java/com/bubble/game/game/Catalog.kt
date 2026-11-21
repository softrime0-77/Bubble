package com.bubble.game.game

import com.bubble.game.i18n.Lang

/**
 * 계급. 흙(~1000) · 구리(1001~) · 은(1500~) · 금(2000~) · 루비(5000~) · 사파이어(7000~) · 다이아(10000~).
 * 값은 functions/src/catalog.ts의 RANKS와 같아야 합니다.
 * [sparkle]이 클수록 배지의 반짝임 효과가 화려해집니다.
 * [bonusPercent]: 이겨서 얻는 점수에 추가로 붙는 비율.
 */
enum class Rank(
    private val ko: String,
    private val en: String,
    val emoji: String,
    val minScore: Int,
    val sparkle: Int,
    val bonusPercent: Int,
) {
    DIRT("흙", "Dirt", "🟫", Int.MIN_VALUE, 0, 0),
    COPPER("구리", "Copper", "🟠", 1001, 1, 5),
    SILVER("은", "Silver", "⚪", 1401, 2, 10),
    GOLD("금", "Gold", "🟡", 2001, 3, 15),
    RUBY("루비", "Ruby", "🔴", 4001, 4, 20),
    SAPPHIRE("사파이어", "Sapphire", "🔷", 7001, 5, 25),
    DIAMOND("다이아", "Diamond", "💎", 10001, 6, 30),
    // 최고 계급: 분홍빛으로 흐르는 배지 + 하트 반짝이 + 궤도를 도는 별빛 + 빛 고리 (가장 화려함)
    PINK_DIAMOND("핑크다이아", "Pink Diamond", "🌸", 50001, 7, 35);

    /** 순서: zh, hi, es, fr, ar, de, pt, ru, ja */
    private val others: List<String>
        get() = when (this) {
            DIRT -> listOf("泥土", "मिट्टी", "Tierra", "Terre", "تراب", "Erde", "Terra", "Земля", "土")
            COPPER -> listOf("铜", "कॉपर", "Cobre", "Cuivre", "نحاس", "Kupfer", "Cobre", "Медь", "銅")
            SILVER -> listOf("银", "सिल्वर", "Plata", "Argent", "فضة", "Silber", "Prata", "Серебро", "銀")
            GOLD -> listOf("金", "गोल्ड", "Oro", "Or", "ذهب", "Gold", "Ouro", "Золото", "金")
            RUBY -> listOf("红宝石", "रूबी", "Rubí", "Rubis", "ياقوت أحمر", "Rubin", "Rubi", "Рубин", "ルビー")
            SAPPHIRE -> listOf("蓝宝石", "नीलम", "Zafiro", "Saphir", "ياقوت أزرق", "Saphir", "Safira", "Сапфир", "サファイア")
            DIAMOND -> listOf("钻石", "डायमंड", "Diamante", "Diamant", "ماس", "Diamant", "Diamante", "Алмаз", "ダイヤ")
            PINK_DIAMOND -> listOf("粉钻", "पिंक डायमंड", "Diamante rosa", "Diamant rose", "ماس وردي", "Pinker Diamant", "Diamante rosa", "Розовый алмаз", "ピンクダイヤ")
        }

    val label: String get() = Lang.pick(listOf(ko, en) + others)

    /** 이겨서 얻는 점수 [base]에 붙는 보너스 (반올림). functions/src/catalog.ts의 rankBonus와 같게. */
    fun bonusFor(base: Int): Int = if (base > 0) Math.round(base * bonusPercent / 100.0).toInt() else 0

    companion object {
        fun of(score: Int): Rank = entries.last { score >= it.minScore }
    }
}

/**
 * 상점에서 파는 동물 캐릭터 (소모품). 장착한 채 랜덤 매칭이 잡히면 1개를 쓰고,
 * 그 판에 내 캐릭터가 바뀌고 상대의 턴 제한 시간이 줄어듭니다.
 * 값은 functions/src/catalog.ts와 같아야 합니다.
 */
enum class Animal(private val ko: String, private val en: String, val price: Int, val opponentTurnSec: Int) {
    SHEEP("양", "Sheep", 30, 15),
    DOG("강아지", "Puppy", 50, 10),
    GIRAFFE("기린", "Giraffe", 100, 5),
    /** 무료 캐릭터: 모습만 햄스터로 바뀌고 효과 없음, 소모되지 않음 */
    HAMSTER("햄스터", "Hamster", 0, Shop.DEFAULT_TURN_SEC),
    ;

    /** 무료 캐릭터 (사지 않고 바로 장착, 효과 없음) */
    val free: Boolean get() = price == 0

    /** 순서: zh, hi, es, fr, ar, de, pt, ru, ja */
    private val others: List<String>
        get() = when (this) {
            SHEEP -> listOf("绵羊", "भेड़", "Oveja", "Mouton", "خروف", "Schaf", "Ovelha", "Овечка", "ヒツジ")
            DOG -> listOf("小狗", "पिल्ला", "Perrito", "Chiot", "جرو", "Welpe", "Cachorrinho", "Щенок", "子イヌ")
            GIRAFFE -> listOf("长颈鹿", "जिराफ़", "Jirafa", "Girafe", "زرافة", "Giraffe", "Girafa", "Жираф", "キリン")
            HAMSTER -> listOf("仓鼠", "हैम्स्टर", "Hámster", "Hamster", "هامستر", "Hamster", "Hamster", "Хомяк", "ハムスター")
        }

    val label: String get() = Lang.pick(listOf(ko, en) + others)

    companion object {
        fun of(id: String?): Animal? = entries.firstOrNull { it.name == id }
    }
}

object Shop {
    const val POTION_PRICE = 50

    /** ×3 점수 부스터 (소모품). functions/src/catalog.ts의 BOOST_PRICE와 같게 */
    const val BOOST_PRICE = 50
    const val BOOST_MULTIPLIER = 3

    /** 한 판에서 쓸 수 있는 부활물약 최대 개수 */
    const val MAX_REVIVES_PER_GAME = 3

    const val DEFAULT_TURN_SEC = 20
}
