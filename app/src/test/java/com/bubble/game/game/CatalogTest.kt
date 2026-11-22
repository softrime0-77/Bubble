package com.bubble.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTest {
    @Test
    fun ranksFollowScoreBands() {
        val cases = mapOf(
            0 to Rank.DIRT, 1000 to Rank.DIRT, 1001 to Rank.COPPER, 1400 to Rank.COPPER,
            1401 to Rank.SILVER, 2000 to Rank.SILVER, 2001 to Rank.GOLD, 4000 to Rank.GOLD,
            4001 to Rank.RUBY, 7000 to Rank.RUBY, 7001 to Rank.SAPPHIRE, 10000 to Rank.SAPPHIRE,
            10001 to Rank.DIAMOND, 50000 to Rank.DIAMOND, 50001 to Rank.PINK_DIAMOND, 999999 to Rank.PINK_DIAMOND,
        )
        cases.forEach { (score, rank) -> assertEquals("score $score", rank, Rank.of(score)) }
    }

    @Test
    fun rankBonusMatchesServer() {
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 30, 35), Rank.entries.map { it.bonusPercent })
        assertEquals(0, Rank.DIRT.bonusFor(30))
        assertEquals(2, Rank.COPPER.bonusFor(30))
        assertEquals(3, Rank.SILVER.bonusFor(30))
        assertEquals(5, Rank.GOLD.bonusFor(30))
        assertEquals(6, Rank.RUBY.bonusFor(30))
        assertEquals(8, Rank.SAPPHIRE.bonusFor(30))
        assertEquals(9, Rank.DIAMOND.bonusFor(30))
        assertEquals(11, Rank.PINK_DIAMOND.bonusFor(30))
        assertEquals(0, Rank.DIAMOND.bonusFor(-15))
    }

    @Test
    fun animalsMatchServerCatalog() {
        val paid = Animal.entries.filter { it.consumable }
        assertEquals(listOf(30, 50, 100), paid.map { it.price })
        assertEquals(listOf(15, 10, 5), paid.map { it.opponentTurnSec })
        // 무료 햄스터: 효과 없음 (상대 턴 시간 그대로)
        assertEquals(listOf(Animal.HAMSTER), Animal.entries.filter { it.free })
        // 유료 스킨 가격 (서버 COSMETIC_SKINS와 같게)
        assertEquals(listOf(300, 300, 300, 500, 1000), Animal.entries.filter { it.cosmetic }.map { it.price })
        assertEquals(listOf(Animal.LUCKY_CAT), Animal.entries.filter { it.lucky })
        assertEquals(Shop.DEFAULT_TURN_SEC, Animal.HAMSTER.opponentTurnSec)
        assertEquals(Animal.DOG, Animal.of("DOG"))
        assertEquals(null, Animal.of(null))
    }
}
