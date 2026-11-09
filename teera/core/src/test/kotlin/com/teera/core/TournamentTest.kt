package com.teera.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TournamentTest {
    private val teams = listOf("علي وعبود", "حسن وسيف", "عمر ونور", "سارة ومريم")

    @Test
    fun `four teams make two rounds, and winners go through to the final`() {
        var t = assertNotNull(Tournament.create("T1", "بطولة الجمعة", "ali", teams, 0))
        assertEquals(listOf(2, 1), t.rounds.map { it.size })
        assertEquals("نص النهائي", t.roundName(0))
        assertEquals("النهائي", t.roundName(1))

        t = assertNotNull(t.withWinner(0, 0, 1))
        t = assertNotNull(t.withWinner(0, 1, 2))
        assertEquals(TMatch(1, 2), t.rounds[1][0])
        assertNull(t.withWinner(1, 0, 0), "a team that is not in the match cannot win it")
        t = assertNotNull(t.withWinner(1, 0, 2))
        assertEquals(2, t.champion)
        assertNull(t.withWinner(0, 0, 0), "the final has been played on it")
    }

    @Test
    fun `eight and sixteen teams, and nothing else`() {
        assertEquals(3, Tournament.create("T", "x", "a", List(8) { "t$it" }, 0)!!.rounds.size)
        assertEquals(4, Tournament.create("T", "x", "a", List(16) { "t$it" }, 0)!!.rounds.size)
        assertNull(Tournament.create("T", "x", "a", List(6) { "t$it" }, 0))
    }
}
