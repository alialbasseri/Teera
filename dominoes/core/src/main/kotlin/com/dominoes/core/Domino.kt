package com.dominoes.core

import kotlinx.serialization.Serializable

/**
 * A single bone. Stored canonically with [low] <= [high] so that 6|3 and 3|6 are the
 * same tile; which way round it is actually laid on the table is a property of the
 * board, not of the tile.
 */
@Serializable
data class Domino(val low: Int, val high: Int) : Comparable<Domino> {

    init {
        require(low in 0..MAX_FACE && high in 0..MAX_FACE) { "face out of range: $low|$high" }
        require(low <= high) { "use Domino.of() to build a canonical tile" }
    }

    val isDouble: Boolean get() = low == high

    /** Total spots, used for hand counting and "heaviest tile" comparisons. */
    val pips: Int get() = low + high

    /** The face on the other side of [face], or null if this tile does not show [face]. */
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
        const val MAX_FACE = 12

        fun of(a: Int, b: Int): Domino = if (a <= b) Domino(a, b) else Domino(b, a)

        fun parse(text: String): Domino {
            val parts = text.split("|", "-", ":")
            require(parts.size == 2) { "cannot parse domino: $text" }
            return of(parts[0].trim().toInt(), parts[1].trim().toInt())
        }
    }
}

/** Which pack the round is dealt from. */
@Serializable
enum class DominoSet(val maxFace: Int) {
    DOUBLE_SIX(6),
    DOUBLE_NINE(9),
    DOUBLE_TWELVE(12);

    /** Every distinct tile in the pack, in ascending order. */
    fun tiles(): List<Domino> = buildList {
        for (high in 0..maxFace) for (low in 0..high) add(Domino(low, high))
    }

    val size: Int get() = (maxFace + 1) * (maxFace + 2) / 2
}

/** Total spots in a collection of tiles. */
fun Iterable<Domino>.pipTotal(): Int = sumOf { it.pips }
