package com.teera.core

import kotlinx.serialization.Serializable

/** Which end of the line a tile is being laid on. */
@Serializable
enum class End { LEFT, RIGHT }

/**
 * A tile as it sits on the table: [left] and [right] are the faces actually showing,
 * so a 6|3 laid with the 3 pointing left is PlacedTile(Domino(3,6), left = 3, right = 6).
 */
@Serializable
data class PlacedTile(val domino: Domino, val left: Int, val right: Int) {
    val isDouble: Boolean get() = domino.isDouble
}

/**
 * The line of play. Kept as a flat list with two open ends; the client draws it as a
 * snake that folds at the screen edges, but that is presentation, not state.
 */
@Serializable
data class Board(val tiles: List<PlacedTile> = emptyList()) {

    val isEmpty: Boolean get() = tiles.isEmpty()
    val size: Int get() = tiles.size

    val leftEnd: Int? get() = tiles.firstOrNull()?.left
    val rightEnd: Int? get() = tiles.lastOrNull()?.right

    fun endValue(end: End): Int? = if (end == End.LEFT) leftEnd else rightEnd

    /** Whether [domino] may be laid on [end] under [rules]. */
    fun accepts(domino: Domino, end: End, rules: RuleSet): Boolean {
        val face = endValue(end) ?: return true
        if (rules.doubleBlankIsWild && domino == Domino(0, 0)) return true
        return domino.matches(face)
    }

    /**
     * Lay [domino] on [end], orienting it so its matching face touches the line.
     * Callers are expected to have checked [accepts] first.
     */
    fun place(domino: Domino, end: End, rules: RuleSet): Board {
        val face = endValue(end)
            ?: return Board(listOf(PlacedTile(domino, domino.low, domino.high)))

        val wild = rules.doubleBlankIsWild && domino == Domino(0, 0) && !domino.matches(face)
        val outer = if (wild) face else domino.otherFace(face)
            ?: error("$domino does not match end $face")

        return when (end) {
            // Touching face goes on the inside, the other face becomes the new open end.
            End.LEFT -> Board(listOf(PlacedTile(domino, outer, face)) + tiles)
            End.RIGHT -> Board(tiles + PlacedTile(domino, face, outer))
        }
    }

    /**
     * Sum of the exposed ends, used by All Fives style scoring. A lone opening tile
     * shows both of its faces, and a double parked at an end shows both halves.
     */
    fun openEndTotal(rules: RuleSet): Int {
        val first = tiles.firstOrNull() ?: return 0
        val last = tiles.last()
        if (tiles.size == 1) return first.left + first.right

        var total = first.left + last.right
        if (rules.doublesCountBothHalves) {
            if (first.isDouble) total += first.left
            if (last.isDouble) total += last.right
        }
        return total
    }

    override fun toString(): String =
        tiles.joinToString(" ") { "[${it.left}:${it.right}]" }
}
