package com.teera.room

/** One day's numbers, by Baghdad's calendar. */
data class DayStat(
    val day: String,
    /** People who opened the app that day. */
    val opened: Int,
    /** People who sat in a match started that day. */
    val played: Int,
    val matches: Int,
    val games: Int,
    val minutes: Int,
)

/** What the developer wants to know: who comes, who plays, for how long, on which app. */
data class AdminStats(
    val totalPlayers: Int,
    val newToday: Int,
    val openedToday: Int,
    val playedToday: Int,
    val matchesToday: Int,
    val finishedToday: Int,
    val gamesToday: Int,
    val minutesToday: Int,
    val totalMatches: Int,
    val totalGames: Int,
    val averageMatchMinutes: Int,
    /** Oldest first. */
    val days: List<DayStat>,
    /** App build → people seen in the last 30 days on it. */
    val versions: Map<Int, Int>,
    /** Name → matches in the last 30 days. */
    val topPlayers: List<Pair<String, Int>>,
    /** People a reminder can reach (a phone address, reminders on). */
    val reachable: Int,
    val remindersToday: Int,
    val tournaments: Int,
)
