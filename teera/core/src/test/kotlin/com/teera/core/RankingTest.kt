package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankingTest {
    private fun match(vararg players: String?, losing: Int) = Ranking.Played(players.toList(), losing)

    @Test
    fun `winners gain what losers lose, and only people are ranked`() {
        val table = Ranking.standings(listOf(match("ali", "sara", "omar", null, losing = 1)))
        assertEquals(setOf("ali", "sara", "omar"), table.keys, "the bot is not ranked")
        val ali = table.getValue("ali").rating
        val sara = table.getValue("sara").rating
        assertTrue(ali > Ranking.START && sara < Ranking.START)
        assertEquals(ali - Ranking.START, Ranking.START - sara, 0.001)
        assertEquals(1, table.getValue("omar").wins)
    }

    @Test
    fun `a match against bots alone does not count`() {
        assertTrue(Ranking.standings(listOf(match("ali", null, null, null, losing = 1))).isEmpty())
    }

    @Test
    fun `beating a stronger side is worth more than beating a weaker one`() {
        // Sara and Noor win twice first, so they come in stronger.
        val warmUp = listOf(match("x", "sara", "y", "noor", losing = 0), match("x", "sara", "y", "noor", losing = 0))
        val upset = Ranking.standings(warmUp + match("ali", "sara", "omar", "noor", losing = 1)).getValue("ali").rating
        val easy = Ranking.standings(listOf(match("ali", "sara", "omar", "noor", losing = 1))).getValue("ali").rating
        assertTrue(upset > easy, "$upset should beat $easy")
    }

    @Test
    fun `partners are counted together, whichever chair each had`() {
        val pairs = Ranking.pairs(
            listOf(match("ali", "sara", "omar", "noor", losing = 1), match("omar", "sara", "ali", "noor", losing = 0)),
        )
        val ours = pairs.getValue("ali" to "omar")
        assertEquals(2, ours.matches)
        assertEquals(1, ours.wins)
    }
}
