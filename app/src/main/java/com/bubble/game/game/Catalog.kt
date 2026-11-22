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
enum class Animal(
    private val ko: String,
    private val en: String,
    val price: Int,
    val opponentTurnSec: Int,
    /** 유료 스킨: 한 번 사면 계속 쓰고, 모습만 바뀜 (효과 없음) */
    val cosmetic: Boolean = false,
) {
    SHEEP("양", "Sheep", 20, 15),
    DOG("강아지", "Puppy", 40, 12),
    GIRAFFE("기린", "Giraffe", 70, 8),
    /** 무료 캐릭터: 모습만 햄스터로 바뀌고 효과 없음, 소모되지 않음 */
    HAMSTER("햄스터", "Hamster", 0, Shop.DEFAULT_TURN_SEC),
    // 유료 스킨 (functions/src/catalog.ts COSMETIC_SKINS와 같은 가격)
    PINK_CAT("핑크 고양이", "Pink Cat", 300, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    BLACK_CAT("까망 고양이", "Black Cat", 300, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    SNOW_HAMSTER("눈송이 햄스터", "Snow Hamster", 300, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    GOLD_HAMSTER("황금 햄스터", "Golden Hamster", 500, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 💰 복냥이: 한 번 사면 계속. 공격할 때마다 50% 확률로 상점 포인트 +1 (하루 최대 30) */
    LUCKY_CAT("복냥이", "Lucky Cat", 1000, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 🐙 문어: 분할할 때 먹물이 퍽 */
    OCTOPUS("문어", "Octopus", 400, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 🐧 펭귄: 공격할 때 얼음 썰매 */
    PENGUIN("펭귄", "Penguin", 400, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 🐼 출석 판다: 장착하고 출석하면 💰 +5 */
    PANDA("출석 판다", "Check-in Panda", 500, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 🐉 아기 용: 상대 방울을 터뜨리면 💰 +2 (하루 최대 10) */
    DRAGON("아기 용", "Baby Dragon", 700, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 🐰 연승 토끼: 연승할수록 리본이 늘어남 (최대 5) */
    RABBIT("연승 토끼", "Streak Bunny", 600, Shop.DEFAULT_TURN_SEC, cosmetic = true),
    /** 🦄 유니콘: 팔지 않음. 다이아 계급 이상이면 장착 */
    UNICORN("유니콘", "Unicorn", 0, Shop.DEFAULT_TURN_SEC),
    ;

    /** 효과가 있는 영구 캐릭터 (복냥이) */
    val lucky: Boolean get() = this == LUCKY_CAT

    /** 그릴 때 고양이 모양인지 (상점 미리보기용) */
    val looksLikeCat: Boolean get() = this == PINK_CAT || this == BLACK_CAT || this == LUCKY_CAT

    /** 소모품 동물 (랜덤 매칭 1판에 1개, 상대 턴을 줄임) */
    val consumable: Boolean get() = !free && !cosmetic && !rankOnly

    /** 계급 캐릭터 (팔지 않음): 🦄 유니콘은 다이아(10001점) 이상이면 무료로 장착 */
    val rankOnly: Boolean get() = this == UNICORN
    val rankMin: Int get() = if (this == UNICORN) 10_001 else 0

    /** 무료 캐릭터 (사지 않고 바로 장착, 효과 없음) */
    val free: Boolean get() = price == 0 && !rankOnly

    /** 순서: zh, hi, es, fr, ar, de, pt, ru, ja */
    private val others: List<String>
        get() = when (this) {
            SHEEP -> listOf("绵羊", "भेड़", "Oveja", "Mouton", "خروف", "Schaf", "Ovelha", "Овечка", "ヒツジ")
            DOG -> listOf("小狗", "पिल्ला", "Perrito", "Chiot", "جرو", "Welpe", "Cachorrinho", "Щенок", "子イヌ")
            GIRAFFE -> listOf("长颈鹿", "जिराफ़", "Jirafa", "Girafe", "زرافة", "Giraffe", "Girafa", "Жираф", "キリン")
            HAMSTER -> listOf("仓鼠", "हैम्स्टर", "Hámster", "Hamster", "هامستر", "Hamster", "Hamster", "Хомяк", "ハムスター")
            PINK_CAT -> listOf("粉红猫咪", "गुलाबी बिल्ली", "Gato rosa", "Chat rose", "قطة وردية", "Pinke Katze", "Gato rosa", "Розовый котик", "ピンクネコ")
            BLACK_CAT -> listOf("小黑猫", "काली बिल्ली", "Gato negro", "Chat noir", "قطة سوداء", "Schwarze Katze", "Gato preto", "Чёрный котик", "くろネコ")
            SNOW_HAMSTER -> listOf("雪花仓鼠", "स्नो हैम्स्टर", "Hámster nieve", "Hamster neige", "هامستر الثلج", "Schneehamster", "Hamster neve", "Снежный хомяк", "ゆきハムスター")
            GOLD_HAMSTER -> listOf("黄金仓鼠", "गोल्डन हैम्स्टर", "Hámster dorado", "Hamster doré", "هامستر ذهبي", "Goldhamster", "Hamster dourado", "Золотой хомяк", "きんハムスター")
            LUCKY_CAT -> listOf("招财猫", "लकी बिल्ली", "Gato de la suerte", "Chat porte-bonheur", "قطة الحظ", "Glückskatze", "Gato da sorte", "Котик удачи", "まねきネコ")
            OCTOPUS -> listOf("章鱼", "ऑक्टोपस", "Pulpo", "Pieuvre", "أخطبوط", "Oktopus", "Polvo", "Осьминог", "タコ")
            PENGUIN -> listOf("企鹅", "पेंगुइन", "Pingüino", "Pingouin", "بطريق", "Pinguin", "Pinguim", "Пингвин", "ペンギン")
            PANDA -> listOf("签到熊猫", "हाज़िरी पांडा", "Panda asistente", "Panda de présence", "باندا الحضور", "Check-in-Panda", "Panda da presença", "Панда-отметка", "しゅっせきパンダ")
            DRAGON -> listOf("小龙", "बेबी ड्रैगन", "Dragoncito", "Bébé dragon", "تنين صغير", "Babydrache", "Dragãozinho", "Дракончик", "ちびドラゴン")
            RABBIT -> listOf("连胜兔", "जीत खरगोश", "Conejo racha", "Lapin série", "أرنب الانتصارات", "Siegeshase", "Coelho da sequência", "Зайка-серия", "れんしょうウサギ")
            UNICORN -> listOf("独角兽", "यूनिकॉर्न", "Unicornio", "Licorne", "وحيد القرن", "Einhorn", "Unicórnio", "Единорог", "ユニコーン")
        }

    val label: String get() = Lang.pick(listOf(ko, en) + others)

    companion object {
        fun of(id: String?): Animal? = entries.firstOrNull { it.name == id }
    }
}

object Shop {
    const val POTION_PRICE = 50

    /** ✏️ 닉네임 변경권 (첫 변경은 무료). functions/src/catalog.ts RENAME_PRICE와 같게 */
    const val RENAME_PRICE = 100

    /** ×3 점수 부스터 (소모품). functions/src/catalog.ts의 BOOST_PRICE와 같게 */
    const val BOOST_PRICE = 50
    const val BOOST_MULTIPLIER = 3

    /** 💰 복냥이: 공격할 때 💰 +1 확률(%)과 하루 최대 (functions/src/catalog.ts LUCKY_*와 같게) */
    const val LUCKY_CHANCE_PCT = 50
    const val LUCKY_DAILY_MAX = 30

    /** 🐼 출석 판다 보너스, 🐉 아기 용 (터뜨릴 때 💰, 하루 최대), 🐰 리본 최대 (functions/src/catalog.ts와 같게) */
    /**
     * 묶음 할인 (개수 → 가격). functions/src/catalog.ts BUNDLES와 같게.
     * 부활물약 1·3·5·10개, 동물 1·3개.
     */
    fun bundles(item: String): List<Pair<Int, Int>> = when (item) {
        "POTION" -> listOf(1 to 50, 3 to 135, 5 to 200, 10 to 350)
        "SHEEP" -> listOf(1 to 20, 3 to 50)
        "DOG" -> listOf(1 to 40, 3 to 100)
        "GIRAFFE" -> listOf(1 to 70, 3 to 180)
        else -> emptyList()
    }

    const val PANDA_CHECKIN_BONUS = 5
    const val DRAGON_POP_COINS = 2
    const val DRAGON_DAILY_MAX = 10
    const val RABBIT_MAX_RIBBONS = 5
    /** 🐰 연승 보상: 3연승 💰5 · 5연승 💰15 · 6연승부터 💰2, 하루 최대 30 (functions/src/catalog.ts rabbitCoins와 같게) */
    const val RABBIT_3_COINS = 5
    const val RABBIT_5_COINS = 15
    const val RABBIT_MORE_COINS = 2
    const val RABBIT_DAILY_MAX = 30
    /** 🦄 유니콘 무지개 축복: 이길 때마다 💰3, 하루 최대 30 (functions/src/catalog.ts와 같게) */
    const val UNICORN_WIN_COINS = 3
    const val UNICORN_DAILY_MAX = 30

    /** 한 판에서 쓸 수 있는 부활물약 최대 개수 */
    const val MAX_REVIVES_PER_GAME = 3

    const val DEFAULT_TURN_SEC = 20
}
