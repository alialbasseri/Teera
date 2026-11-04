package com.teera.core

/**
 * Where each tile sits on the table.
 *
 * Kept out of the UI so it can be unit tested and so the phone, a tablet and a future
 * spectator view all fold the line of play the same way. Coordinates are in tile units:
 * one unit is the short side of a bone, so a bone is 2 x 1 lying down.
 */
object BoardLayout {

    /** Position of one tile, as the centre of the bone plus how far it is turned. */
    data class Placement(
        val index: Int,
        val tile: PlacedTile,
        val centreX: Float,
        val centreY: Float,
        /** 0 = lying along the row, 90 = crossways, as doubles are laid. */
        val rotationDegrees: Float,
    )

    data class Layout(
        val placements: List<Placement>,
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float,
    ) {
        val width: Float get() = maxX - minX
        val height: Float get() = maxY - minY
    }

    const val TILE_LENGTH = 2f
    const val TILE_WIDTH = 1f
    private const val ROW_GAP = 0.45f

    /**
     * Folds the line of play into rows that fit inside [maxRowUnits], the way a real
     * table runs out of space and turns the line back on itself. Rows alternate
     * direction, so the chain reads left to right, then right to left.
     */
    fun compute(tiles: List<PlacedTile>, maxRowUnits: Float = 14f): Layout {
        if (tiles.isEmpty()) return Layout(emptyList(), 0f, 0f, 0f, 0f)

        val rowLimit = maxRowUnits.coerceAtLeast(TILE_LENGTH * 2)
        val placements = ArrayList<Placement>(tiles.size)

        var direction = 1f
        var cursor = 0f          // leading edge of the row, in units
        var row = 0f
        var rowStart = 0f

        tiles.forEachIndexed { index, tile ->
            val span = if (tile.isDouble) TILE_WIDTH else TILE_LENGTH

            // Out of room in this row: drop down and run the other way.
            if (kotlin.math.abs(cursor + direction * span - rowStart) > rowLimit) {
                row += TILE_LENGTH + ROW_GAP
                direction = -direction
                rowStart = cursor
            }

            val centre = cursor + direction * span / 2f
            placements += Placement(
                index = index,
                tile = tile,
                centreX = centre,
                centreY = row,
                rotationDegrees = if (tile.isDouble) 90f else 0f,
            )
            cursor += direction * span
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (placement in placements) {
            val halfWide = if (placement.rotationDegrees == 0f) TILE_LENGTH / 2f else TILE_WIDTH / 2f
            val halfTall = if (placement.rotationDegrees == 0f) TILE_WIDTH / 2f else TILE_LENGTH / 2f
            minX = minOf(minX, placement.centreX - halfWide)
            maxX = maxOf(maxX, placement.centreX + halfWide)
            minY = minOf(minY, placement.centreY - halfTall)
            maxY = maxOf(maxY, placement.centreY + halfTall)
        }
        return Layout(placements, minX, minY, maxX, maxY)
    }
}
