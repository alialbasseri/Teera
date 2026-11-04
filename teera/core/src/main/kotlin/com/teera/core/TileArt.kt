package com.teera.core

/**
 * How a bone looks, as numbers.
 *
 * Kept here with the rest of the model rather than in the Compose code so the same
 * geometry can be drawn on a phone, rendered to a picture for review, and checked by a
 * test. Nothing here knows what a Canvas is.
 */
object TileArt {

    /** A spot's place on a face, as a fraction of that face. */
    data class Pip(val x: Float, val y: Float)

    /**
     * The colour each number is drilled in, as 0xRRGGBB, following the coloured sets
     * people actually play with. The count and arrangement of the spots still carries
     * the meaning, so the colour is a help rather than the signal.
     */
    fun colourOf(value: Int): Int = when (value) {
        1 -> 0x35A8DE   // light blue
        2 -> 0x1F7A3A   // dark green
        3 -> 0xC9437F   // pink
        4 -> 0x8C8176   // taupe
        5 -> 0x2B4C9B   // navy
        6 -> 0xD9A21B   // yellow
        else -> 0x2F2A24
    }

    private const val NEAR = 0.26f
    private const val MID = 0.5f
    private const val FAR = 0.74f

    /** Where the spots sit for a face, in the arrangement a real set uses. */
    fun pipsOf(value: Int): List<Pip> = when (value) {
        0 -> emptyList()
        1 -> listOf(Pip(MID, MID))
        2 -> listOf(Pip(NEAR, NEAR), Pip(FAR, FAR))
        3 -> listOf(Pip(NEAR, NEAR), Pip(MID, MID), Pip(FAR, FAR))
        4 -> listOf(Pip(NEAR, NEAR), Pip(FAR, NEAR), Pip(NEAR, FAR), Pip(FAR, FAR))
        5 -> listOf(Pip(NEAR, NEAR), Pip(FAR, NEAR), Pip(MID, MID), Pip(NEAR, FAR), Pip(FAR, FAR))
        6 -> listOf(
            Pip(NEAR, 0.2f), Pip(NEAR, MID), Pip(NEAR, 0.8f),
            Pip(FAR, 0.2f), Pip(FAR, MID), Pip(FAR, 0.8f),
        )
        else -> emptyList()
    }
}
