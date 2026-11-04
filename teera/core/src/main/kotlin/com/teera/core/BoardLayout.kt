package com.teera.core

/**
 * Where every tile sits on the table.
 *
 * Kept out of the UI so the geometry can be unit tested rather than eyeballed on a
 * phone. Coordinates are in tile units: one unit is the short side of a bone, so a bone
 * lying down is 2 by 1. The opening double stands in the middle and the four arms grow
 * away from it, which is the shape Teera actually makes on a table.
 */
object BoardLayout {

    const val TILE_LENGTH = 2f
    const val TILE_WIDTH = 1f

    data class Placement(
        val arm: Arm?,
        val index: Int,
        val domino: Domino,
        /** The face nearer the middle of the table, and the one further out. */
        val inner: Int,
        val outer: Int,
        val centreX: Float,
        val centreY: Float,
        /** 0 means lying left to right; 90 means standing on end. */
        val rotationDegrees: Float,
        val isCentre: Boolean = false,
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

    private val EMPTY = Layout(emptyList(), 0f, 0f, 0f, 0f)

    fun compute(board: Board): Layout {
        val centre = board.centre ?: return EMPTY

        val placements = mutableListOf<Placement>()

        // The opening double stands upright in the middle, so the east and west arms run
        // off its long sides and the north and south arms off its ends.
        placements += Placement(
            arm = null,
            index = 0,
            domino = centre,
            inner = centre.low,
            outer = centre.high,
            centreX = 0f,
            centreY = 0f,
            rotationDegrees = 90f,
            isCentre = true,
        )

        for (arm in Arm.ALL) {
            var distance = startOffset(arm)
            board.tilesOn(arm).forEachIndexed { index, laid ->
                val span = if (laid.isDouble) TILE_WIDTH else TILE_LENGTH
                val from = distance + span / 2f
                placements += Placement(
                    arm = arm,
                    index = index,
                    domino = laid.domino,
                    inner = laid.inner,
                    outer = laid.outer,
                    centreX = if (arm.isHorizontal) from * arm.sign else 0f,
                    centreY = if (arm.isHorizontal) 0f else from * arm.sign,
                    rotationDegrees = rotationOf(arm, laid.isDouble),
                )
                distance += span
            }
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (placement in placements) {
            val upright = placement.rotationDegrees != 0f
            val halfWide = if (upright) TILE_WIDTH / 2f else TILE_LENGTH / 2f
            val halfTall = if (upright) TILE_LENGTH / 2f else TILE_WIDTH / 2f
            minX = minOf(minX, placement.centreX - halfWide)
            maxX = maxOf(maxX, placement.centreX + halfWide)
            minY = minOf(minY, placement.centreY - halfTall)
            maxY = maxOf(maxY, placement.centreY + halfTall)
        }
        return Layout(placements, minX, minY, maxX, maxY)
    }

    /** How far the first tile of an arm starts from the middle of the opening double. */
    private fun startOffset(arm: Arm): Float =
        if (arm.isHorizontal) TILE_WIDTH / 2f else TILE_LENGTH / 2f

    /**
     * Tiles lie along their arm, and doubles sit across it. East and west arms run
     * horizontally, north and south vertically, so the two swap over.
     */
    private fun rotationOf(arm: Arm, isDouble: Boolean): Float = when {
        arm.isHorizontal && isDouble -> 90f
        arm.isHorizontal -> 0f
        isDouble -> 0f
        else -> 90f
    }
}

private val Arm.isHorizontal: Boolean get() = this == Arm.EAST || this == Arm.WEST

/** Which way along its axis an arm grows. */
private val Arm.sign: Float
    get() = when (this) {
        Arm.EAST, Arm.SOUTH -> 1f
        Arm.WEST, Arm.NORTH -> -1f
    }
