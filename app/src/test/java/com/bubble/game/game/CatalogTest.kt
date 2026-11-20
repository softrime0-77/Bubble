package com.bubble.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTest {
    @Test
    fun ranksFollowScoreBands() {
        assertEquals(Rank.DIRT, Rank.of(0))
        assertEquals(Rank.DIRT, Rank.of(1000))
        assertEquals(Rank.COPPER, Rank.of(1001))
        assertEquals(Rank.COPPER, Rank.of(1100))
        assertEquals(Rank.SILVER, Rank.of(1101))
        assertEquals(Rank.GOLD, Rank.of(1201))
        assertEquals(Rank.GOLD, Rank.of(1300))
        assertEquals(Rank.DIAMOND, Rank.of(1301))
        assertEquals(Rank.DIAMOND, Rank.of(5000))
    }

    @Test
    fun animalsMatchServerCatalog() {
        assertEquals(listOf(500, 750, 1000), Animal.entries.map { it.price })
        assertEquals(listOf(20, 15, 10), Animal.entries.map { it.opponentTurnSec })
        assertEquals(Animal.DOG, Animal.of("DOG"))
        assertEquals(null, Animal.of(null))
    }
}
