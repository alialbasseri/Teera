package com.teera.core

import kotlinx.serialization.Serializable

/**
 * A single bone. Stored canonically with [low] <= [high] so that 6|3 and 3|6 are the
 * same tile; which way round it lies on the table belongs to the board, not the tile.
 */
@Serializable
data class Domino(val low: Int, val high: Int) : Comparable<Domino> {

    init {
        require(low in 0..Pack.MAX_FACE && high in 0..Pack.MAX_FACE) { "face out of range: $low|$high" }
        require(low <= high) { "use Domino.of() to build a canonical tile" }
    }

    val isDouble: Boolean get() = low == high

    /** Spots on the tile. This is what the losing team pays at the end of a game. */
    val pips: Int get() = low + high

    /** The face opposite [face], or null when this tile does not show [face]. */
    fun otherFace(face: Int): Int? = when (face) {
        low -> high
        high -> low
        else -> null
    }

    fun matches(face: Int): Boolean = face == low || face == high

    override fun compareTo(other: Domino): Int =
        compareValuesBy(this, other, { it.high }, { it.low })

    override fun toString(): String = "$low|$high"

    companion object {
        fun of(a: Int, b: Int): Domino = if (a <= b) Domino(a, b) else Domino(b, a)

        fun parse(text: String): Domino {
            val parts = text.split("|", "-", ":")
            require(parts.size == 2) { "cannot parse domino: $text" }
            return of(parts[0].trim().toInt(), parts[1].trim().toInt())
        }
    }
}

/**
 * Teera is played with one pack and one pack only: double six, dealt out entirely.
 * There is no boneyard, which is why [Engine] has no draw move.
 */
object Pack {
    const val MAX_FACE = 6
    const val SIZE = 28
    const val HAND_SIZE = 7
    const val PLAYERS = 4

    /** The opening bone of the very first game of a match. */
    val OPENING_DOUBLE = Domino(6, 6)

    /** The only two tiles light enough to declare a teera with. */
    val TEERA_TILES = listOf(Domino(0, 0), Domino(0, 1))

    val tiles: List<Domino> = buildList {
        for (high in 0..MAX_FACE) for (low in 0..high) add(Domino(low, high))
    }

    val doubles: List<Domino> = tiles.filter { it.isDouble }
}

/** Total spots in a collection of tiles. */
fun Iterable<Domino>.pipTotal(): Int = sumOf { it.pips }

/** Seats 0 and 2 are one team, 1 and 3 the other. Partners sit across the table. */
fun teamOf(seat: Int): Int = seat % 2

fun seatsOfTeam(team: Int): List<Int> = (0 until Pack.PLAYERS).filter { teamOf(it) == team }

fun otherTeam(team: Int): Int = 1 - team
