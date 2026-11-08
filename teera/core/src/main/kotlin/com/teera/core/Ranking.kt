package com.teera.core

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Works the ranking out from finished matches, oldest first.
 *
 * Each team's strength is its players' average rating (a bot counts as [START] and is
 * never ranked). The winners gain what the losers lose, more when they were the weaker
 * side. A match only counts with a person on each side: beating three bots alone
 * proves nothing.
 */
object Ranking {
    const val START = 1000
    private const val K = 32.0

    /** A finished match as the ranking needs it: who sat where (null for a bot), and who lost. */
    data class Played(val players: List<String?>, val losingTeam: Int)

    data class Standing(val rating: Double = START.toDouble(), val matches: Int = 0, val wins: Int = 0)

    data class PairStanding(val matches: Int = 0, val wins: Int = 0)

    fun counts(match: Played): Boolean =
        (0 until 4).any { teamOf(it) == 0 && match.players.getOrNull(it) != null } &&
            (0 until 4).any { teamOf(it) == 1 && match.players.getOrNull(it) != null }

    fun standings(matches: List<Played>): Map<String, Standing> {
        val table = HashMap<String, Standing>()
        fun ratingOf(id: String?) = id?.let { table[it]?.rating } ?: START.toDouble()
        for (match in matches.filter(::counts)) {
            val strength = (0..1).map { team ->
                (0 until 4).filter { teamOf(it) == team }.map { ratingOf(match.players[it]) }.average()
            }
            for (seat in 0 until 4) {
                val id = match.players[seat] ?: continue
                val team = teamOf(seat)
                val expected = 1.0 / (1.0 + 10.0.pow((strength[1 - team] - strength[team]) / 400.0))
                val won = match.losingTeam != team
                val before = table[id] ?: Standing()
                table[id] = before.copy(
                    rating = before.rating + K * ((if (won) 1.0 else 0.0) - expected),
                    matches = before.matches + 1,
                    wins = before.wins + if (won) 1 else 0,
                )
            }
        }
        return table
    }

    /** Two people who played on the same side, keyed by their ids in order. */
    fun pairs(matches: List<Played>): Map<Pair<String, String>, PairStanding> {
        val table = HashMap<Pair<String, String>, PairStanding>()
        for (match in matches.filter(::counts)) {
            for (team in 0..1) {
                val ids = (0 until 4).filter { teamOf(it) == team }.mapNotNull { match.players[it] }
                if (ids.size != 2 || ids[0] == ids[1]) continue
                val key = ids.sorted().let { it[0] to it[1] }
                val before = table[key] ?: PairStanding()
                table[key] = PairStanding(before.matches + 1, before.wins + if (match.losingTeam != team) 1 else 0)
            }
        }
        return table
    }

    fun shown(rating: Double): Int = rating.roundToInt()
}
