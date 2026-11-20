package com.bubble.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTest {
    @Test
    fun ranksFollowScoreBands() {
        val cases = mapOf(
            0 to Rank.DIRT, 1000 to Rank.DIRT, 1001 to Rank.COPPER, 1499 to Rank.COPPER,
            1500 to Rank.SILVER, 1999 to Rank.SILVER, 2000 to Rank.GOLD, 4999 to Rank.GOLD,
            5000 to Rank.RUBY, 6999 to Rank.RUBY, 7000 to Rank.SAPPHIRE, 9999 to Rank.SAPPHIRE,
            10000 to Rank.DIAMOND, 50000 to Rank.DIAMOND,
        )
        cases.forEach { (score, rank) -> assertEquals("score $score", rank, Rank.of(score)) }
    }

    @Test
    fun rankBonusMatchesServer() {
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 30), Rank.entries.map { it.bonusPercent })
        assertEquals(0, Rank.DIRT.bonusFor(30))
        assertEquals(2, Rank.COPPER.bonusFor(30))
        assertEquals(3, Rank.SILVER.bonusFor(30))
        assertEquals(5, Rank.GOLD.bonusFor(30))
        assertEquals(6, Rank.RUBY.bonusFor(30))
        assertEquals(8, Rank.SAPPHIRE.bonusFor(30))
        assertEquals(9, Rank.DIAMOND.bonusFor(30))
        assertEquals(0, Rank.DIAMOND.bonusFor(-15))
    }

    @Test
    fun animalsMatchServerCatalog() {
        assertEquals(listOf(500, 750, 1000), Animal.entries.map { it.price })
        assertEquals(listOf(20, 15, 10), Animal.entries.map { it.opponentTurnSec })
        assertEquals(Animal.DOG, Animal.of("DOG"))
        assertEquals(null, Animal.of(null))
    }
}
