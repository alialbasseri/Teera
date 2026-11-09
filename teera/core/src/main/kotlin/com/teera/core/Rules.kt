package com.teera.core

import kotlinx.serialization.Serializable

/**
 * The few things a table actually gets to choose.
 *
 * Teera is not a family of variants the way dominoes is, so this is small on purpose:
 * the rules themselves live in [Engine] and are described in RULES.md. What is settled
 * before the deal is the target, and how long people get to act.
 */
@Serializable
data class Rules(
    /** Reaching this **loses** the match. The table picks 201 or 251 before dealing. */
    val target: Int = 201,
    /** 0 leaves a seat as long as it likes. Only an online table sets a clock. */
    val turnSeconds: Int = 0,
    val alertVoteSeconds: Int = 30,
    /** Alerts needed before the penalty lands. They run for the whole match. */
    val alertsPerPenalty: Int = 3,
    val alertPenalty: Int = 50,
    /** Emptying your hand takes 25 off your team's score. */
    val xBonus: Int = -25,
    /**
     * 0 plays to [target]. Otherwise the match is this many games (a locked game is
     * played again, so it does not count), and the higher score after them loses; level
     * after them, another game settles it.
     */
    val games: Int = 0,
) {
    /** How the match is played to, as the table says it. */
    val byGames: Boolean get() = games > 0

    fun validate(): List<String> = buildList {
        if (target < 1) add("target must be a positive score")
        if (alertsPerPenalty < 1) add("alertsPerPenalty must be at least 1")
        if (games !in 0..50) add("games must be between 0 and 50")
    }

    companion object {
        /** What a table is offered before the deal; rule 5. */
        val TARGETS = listOf(201, 251)

        /** The other way to play a match: a set number of games. */
        const val GAMES = 10
        val DEFAULT = Rules()
    }
}
