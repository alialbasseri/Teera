package com.teera.server

import com.teera.room.AdminStats
import com.teera.room.DayStat
import com.teera.room.LiveNow
import com.teera.room.adminPage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Writes the developer's page with made-up numbers, to look at. */
class AdminPagePreview {
    @Test
    fun `the page draws`() {
        val days = (0 until 14).map { i ->
            DayStat("2026-11-%02d".format(i + 1), opened = 3 + (i * 7) % 11, played = 2 + (i * 5) % 8, matches = i % 5, games = (i % 5) * 9, minutes = (i % 5) * 38)
        }
        val page = adminPage(
            LiveNow(online = 6, rooms = 2, playing = 1, watchers = 2, peakToday = 9),
            AdminStats(
                totalPlayers = 27, newToday = 3, openedToday = 12, playedToday = 8, matchesToday = 4, finishedToday = 3,
                gamesToday = 41, minutesToday = 186, totalMatches = 63, totalGames = 702, averageMatchMinutes = 44,
                days = days, versions = linkedMapOf(25 to 14, 24 to 6, 0 to 2),
                topPlayers = listOf("عبود نفط" to 19, "علي" to 17, "حسن" to 11, "سيف" to 8),
                reachable = 18, remindersToday = 23, tournaments = 2,
            ),
        )
        File("build/admin-preview.html").writeText(page)
        assertTrue("طيرة · الأرقام" in page)
    }
}
