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
) {
    fun validate(): List<String> = buildList {
        if (target < 1) add("target must be a positive score")
        if (alertsPerPenalty < 1) add("alertsPerPenalty must be at least 1")
    }

    companion object {
        /** What a table is offered before the deal; rule 5. */
        val TARGETS = listOf(201, 251)
        val DEFAULT = Rules()
    }
}
