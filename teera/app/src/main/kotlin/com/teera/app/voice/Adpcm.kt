package com.teera.app.voice

/**
 * IMA ADPCM: 16-bit speech in 4 bits a sample, a quarter of the size.
 *
 * Chosen over Opus because it is a page of arithmetic that runs the same on every phone
 * and in tests, with no codec to find on the device. At 16 kHz it is 64 kbit/s, which
 * is fine for four friends talking and only flows while somebody actually speaks.
 *
 * Every block starts from its own state (the first sample and the step index), so a lost
 * packet costs only its own 40 ms and never garbles the ones after it.
 *
 * Block layout: predictor (int16, little endian), step index (byte), 0, then two samples
 * per byte, low nibble first. A block of n samples is 4 + n / 2 bytes.
 */
object Adpcm {

    private val STEPS = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767,
    )
    private val INDEX_SHIFT = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)

    /** Picks the step index a fresh block should start on, from the size of its first jump. */
    private fun startingIndex(samples: ShortArray, offset: Int, count: Int): Int {
        if (count < 2) return 0
        val jump = kotlin.math.abs(samples[offset + 1] - samples[offset])
        var index = 0
        while (index < STEPS.lastIndex && STEPS[index] < jump) index++
        return index
    }

    fun encode(samples: ShortArray, offset: Int = 0, count: Int = samples.size, index0: Int? = null): ByteArray {
        require(count >= 1)
        val out = ByteArray(HEADER + count / 2)
        var predictor = samples[offset].toInt()
        var index = index0 ?: startingIndex(samples, offset, count)
        out[0] = (predictor and 0xFF).toByte()
        out[1] = ((predictor shr 8) and 0xFF).toByte()
        out[2] = index.toByte()
        out[3] = 0

        var nibbleAt = 0
        for (i in 1 until count) {
            val step = STEPS[index]
            var diff = samples[offset + i] - predictor
            var code = 0
            if (diff < 0) {
                code = 8
                diff = -diff
            }
            var delta = step shr 3
            if (diff >= step) { code = code or 4; diff -= step; delta += step }
            if (diff >= step shr 1) { code = code or 2; diff -= step shr 1; delta += step shr 1 }
            if (diff >= step shr 2) { code = code or 1; delta += step shr 2 }
            predictor = (if (code and 8 != 0) predictor - delta else predictor + delta).coerceIn(-32768, 32767)
            index = (index + INDEX_SHIFT[code]).coerceIn(0, STEPS.lastIndex)

            val at = HEADER + nibbleAt / 2
            out[at] = if (nibbleAt % 2 == 0) code.toByte() else (out[at].toInt() or (code shl 4)).toByte()
            nibbleAt++
        }
        return out
    }

    /**
     * The samples a block holds. [count] is how many were encoded into it; left out, it
     * assumes the last byte was full, which gives one sample too many for an even count.
     */
    fun decode(
        block: ByteArray,
        offset: Int = 0,
        length: Int = block.size - offset,
        count: Int = 1 + (length - HEADER) * 2,
    ): ShortArray {
        require(length >= HEADER && count in 1..1 + (length - HEADER) * 2)
        var predictor = ((block[offset].toInt() and 0xFF) or (block[offset + 1].toInt() shl 8)).toShort().toInt()
        var index = (block[offset + 2].toInt() and 0xFF).coerceIn(0, STEPS.lastIndex)
        val nibbles = count - 1
        val out = ShortArray(count)
        out[0] = predictor.toShort()
        for (n in 0 until nibbles) {
            val byte = block[offset + HEADER + n / 2].toInt() and 0xFF
            val code = if (n % 2 == 0) byte and 0x0F else byte shr 4
            val step = STEPS[index]
            var delta = step shr 3
            if (code and 4 != 0) delta += step
            if (code and 2 != 0) delta += step shr 1
            if (code and 1 != 0) delta += step shr 2
            predictor = (if (code and 8 != 0) predictor - delta else predictor + delta).coerceIn(-32768, 32767)
            index = (index + INDEX_SHIFT[code]).coerceIn(0, STEPS.lastIndex)
            out[1 + n] = predictor.toShort()
        }
        return out
    }

    const val HEADER = 4
}
