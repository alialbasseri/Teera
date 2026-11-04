package com.teera.core

import kotlinx.serialization.Serializable

/**
 * The four directions the line of play runs in.
 *
 * Teera opens on a double laid in the middle of the table, and that double is played
 * on from all four sides. These are those sides; the names are only for the screen.
 */
@Serializable
enum class Arm { NORTH, EAST, SOUTH, WEST;

    companion object {
        val ALL = entries.toList()
    }
}

/**
 * A tile as it lies on an arm: [inner] faces back towards the middle of the table,
 * [outer] is the face now open to be played on.
 */
@Serializable
data class LaidTile(val domino: Domino, val inner: Int, val outer: Int) {
    val isDouble: Boolean get() = domino.isDouble
}

/**
 * The table.
 *
 * [centre] is the double the game opened on. Each arm grows outwards from it, and the
 * four arms must all be opened before play spreads — see rule 12 in RULES.md. A double
 * played later sits crossways for looks but is still one open end, keeping its own
 * number live (rule 14).
 */
@Serializable
data class Board(
    val centre: Domino? = null,
    /** Indexed by [Arm.ordinal]. */
    val arms: List<List<LaidTile>> = List(Arm.ALL.size) { emptyList() },
) {
    val isEmpty: Boolean get() = centre == null

    /** The number showing on all four sides of the opening double. */
    val centreFace: Int? get() = centre?.high

    fun tilesOn(arm: Arm): List<LaidTile> = arms[arm.ordinal]

    /** The number an arm currently accepts, or the centre's number if it is untouched. */
    fun endOf(arm: Arm): Int? = tilesOn(arm).lastOrNull()?.outer ?: centreFace

    fun isOpened(arm: Arm): Boolean = tilesOn(arm).isNotEmpty()

    val openedArms: Int get() = arms.count { it.isNotEmpty() }

    /** True while the opening double still has a bare side (rule 12). */
    val inOpeningPhase: Boolean get() = centre != null && openedArms < Arm.ALL.size

    /** Every tile on the table, the opening double included. */
    fun tiles(): List<Domino> =
        listOfNotNull(centre) + arms.flatten().map { it.domino }

    val size: Int get() = (if (centre == null) 0 else 1) + arms.sumOf { it.size }

    /**
     * Whether [domino] may go on [arm].
     *
     * While the four sides are being closed, each turn has to open a side nobody has
     * touched yet, so an arm already played on is not a legal target however well the
     * tile matches.
     */
    fun accepts(domino: Domino, arm: Arm): Boolean {
        val face = endOf(arm) ?: return false
        if (inOpeningPhase && isOpened(arm)) return false
        return domino.matches(face)
    }

    /** Whether this tile has anywhere to go at all. */
    fun acceptsAnywhere(domino: Domino): Boolean = Arm.ALL.any { accepts(domino, it) }

    /** Lays the opening double in the middle. */
    fun open(double: Domino): Board {
        require(double.isDouble) { "a game can only be opened on a double" }
        require(centre == null) { "the table is already open" }
        return Board(centre = double)
    }

    /** Lays [domino] on [arm], turning it so its matching face points inwards. */
    fun place(domino: Domino, arm: Arm): Board {
        val face = endOf(arm) ?: error("the table has not been opened")
        val outer = domino.otherFace(face) ?: error("$domino does not match the $face end")
        val updated = arms.toMutableList()
        updated[arm.ordinal] = tilesOn(arm) + LaidTile(domino, inner = face, outer = outer)
        return copy(arms = updated)
    }

    override fun toString(): String = buildString {
        append(centre?.toString() ?: "-")
        Arm.ALL.forEach { arm ->
            if (isOpened(arm)) {
                append(" ${arm.name.first()}:")
                append(tilesOn(arm).joinToString("") { "[${it.inner}:${it.outer}]" })
            }
        }
    }
}
