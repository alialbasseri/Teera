package com.teera.core

import kotlinx.serialization.Serializable

/** One match in a tournament's bracket: two teams by their place in [Tournament.teams]. */
@Serializable
data class TMatch(
    /** Null until the match before it is decided. */
    val a: Int? = null,
    val b: Int? = null,
    val winner: Int? = null,
    /** The room it is being played in, once somebody starts it. */
    val roomCode: String? = null,
)

/**
 * A knockout of pairs: 4, 8 or 16 teams, drawn in the order they were entered, the
 * winner of each match going on to the next round until one is left.
 */
@Serializable
data class Tournament(
    val id: String,
    val name: String,
    val creatorId: String,
    val teams: List<String>,
    val rounds: List<List<TMatch>>,
    val createdAtEpochMs: Long = 0,
) {
    val champion: Int? get() = rounds.lastOrNull()?.singleOrNull()?.winner

    /** [team] won match [index] of [round]: they go through to their place in the next. */
    fun withWinner(round: Int, index: Int, team: Int): Tournament? {
        val match = rounds.getOrNull(round)?.getOrNull(index) ?: return null
        if (team != match.a && team != match.b) return null
        // A result the next round has already played on cannot change.
        val next = rounds.getOrNull(round + 1)?.getOrNull(index / 2)
        if (next?.winner != null) return null
        val updated = rounds.mapIndexed { r, matches ->
            matches.mapIndexed { i, m ->
                when {
                    r == round && i == index -> m.copy(winner = team)
                    r == round + 1 && i == index / 2 -> if (index % 2 == 0) m.copy(a = team) else m.copy(b = team)
                    else -> m
                }
            }
        }
        return copy(rounds = updated)
    }

    fun withRoom(round: Int, index: Int, code: String): Tournament? {
        rounds.getOrNull(round)?.getOrNull(index) ?: return null
        return copy(rounds = rounds.mapIndexed { r, ms -> ms.mapIndexed { i, m -> if (r == round && i == index) m.copy(roomCode = code) else m } })
    }

    /** What a round is called at the table. */
    fun roundName(round: Int): String = when (rounds.size - round) {
        1 -> "النهائي"
        2 -> "نص النهائي"
        3 -> "ربع النهائي"
        else -> "الدور ${round + 1}"
    }

    companion object {
        val SIZES = listOf(4, 8, 16)

        fun create(id: String, name: String, creatorId: String, teams: List<String>, now: Long): Tournament? {
            if (teams.size !in SIZES || teams.any { it.isBlank() }) return null
            val first = teams.indices.chunked(2).map { (a, b) -> TMatch(a, b) }
            val rounds = generateSequence(first) { previous -> List(previous.size / 2) { TMatch() }.takeIf { it.isNotEmpty() } }.toList()
            return Tournament(id, name.trim().take(40).ifEmpty { "بطولة" }, creatorId, teams.map { it.trim().take(30) }, rounds, now)
        }
    }
}

/** A title the books give one player: the most teeras, and the like. */
@Serializable
data class TitleHolder(val title: String, val playerId: String, val name: String, val avatar: String, val value: String)
