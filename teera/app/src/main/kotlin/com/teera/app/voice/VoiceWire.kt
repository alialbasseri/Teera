package com.teera.app.voice

import com.teera.app.SharedSynchronized
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The parts of table talk that are plain arithmetic, kept apart from the microphone and
 * speaker so they can be tested anywhere.
 *
 * On the wire a phone sends `[codec][seq hi][seq lo][ADPCM block]` as a binary websocket
 * frame. The referee does not open it: it puts the sender's seat in front and passes it
 * to everyone else at the table, `[seat][codec][seq hi][seq lo][block]`.
 */
object VoiceWire {
    const val SAMPLE_RATE = 16_000

    /** 40 ms a packet: few enough packets to be cheap, short enough to feel live. */
    const val FRAME_SAMPLES = 640
    const val CODEC_ADPCM: Byte = 1

    fun pack(seq: Int, frame: ShortArray): ByteArray {
        val block = Adpcm.encode(frame)
        val out = ByteArray(3 + block.size)
        out[0] = CODEC_ADPCM
        out[1] = (seq shr 8).toByte()
        out[2] = seq.toByte()
        block.copyInto(out, 3)
        return out
    }

    /** One packet as relayed by the referee. */
    class Heard(val seat: Int, val seq: Int, val samples: ShortArray)

    fun unpack(relayed: ByteArray): Heard? {
        if (relayed.size < 4 + Adpcm.HEADER) return null
        if (relayed[1] != CODEC_ADPCM) return null // a codec from a newer app: skip it, do not crash
        val seat = relayed[0].toInt()
        if (seat !in 0..7) return null // four chairs, then the watchers
        val seq = ((relayed[2].toInt() and 0xFF) shl 8) or (relayed[3].toInt() and 0xFF)
        val blockLength = relayed.size - 4
        val count = minOf(FRAME_SAMPLES, 1 + (blockLength - Adpcm.HEADER) * 2)
        return Heard(seat, seq, Adpcm.decode(relayed, offset = 4, length = blockLength, count = count))
    }

    /** True when [a] comes after [b], allowing for the 16-bit counter wrapping round. */
    fun newer(a: Int, b: Int): Boolean = ((a - b) and 0xFFFF) in 1..0x7FFF
}

/**
 * Decides whether somebody is talking, so an open mic in a quiet room sends nothing.
 *
 * It follows the room's own background level and opens when the voice rises well above
 * it, then stays open a little after the last word so sentences are not chopped.
 */
class TalkGate(
    private val hangFrames: Int = 10,
    private val minimumLevel: Double = 260.0,
) {
    private var floor = 300.0
    private var hang = 0

    /** The level of the last frame, for a meter. */
    var level = 0.0
        private set

    fun open(frame: ShortArray): Boolean {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        level = sqrt(sum / frame.size)
        // The floor sinks quickly to quiet and rises only slowly, so speech does not drag it up.
        floor = if (level < floor) floor * 0.8 + level * 0.2 else floor * 0.995 + level * 0.005
        val loud = level > max(minimumLevel, floor * 3.0)
        if (loud) {
            hang = hangFrames
            return true
        }
        if (hang > 0) {
            hang--
            return true
        }
        return false
    }
}

/**
 * Packets from one player, waiting to be played: put back in order, late ones dropped,
 * and never more than a quarter of a second queued, so a burst after a stall does not
 * leave everybody listening to the past.
 */
class Inbox(private val keep: Int = 6, private val forgetAfterMillis: Long = 1_500) {
    private val waiting = ArrayList<Pair<Int, ShortArray>>()
    private var lastPlayed: Int? = null
    private var lastHeardAt = Long.MIN_VALUE / 2

    @SharedSynchronized
    fun offer(seq: Int, samples: ShortArray, now: Long) {
        // After a pause the sender may have started counting again (a restarted app);
        // the order only matters within one stretch of talking.
        if (now - lastHeardAt > forgetAfterMillis) lastPlayed = null
        lastHeardAt = now
        val last = lastPlayed
        if (last != null && !VoiceWire.newer(seq, last)) return
        if (waiting.any { it.first == seq }) return
        val at = waiting.indexOfFirst { VoiceWire.newer(it.first, seq) }.let { if (it < 0) waiting.size else it }
        waiting.add(at, seq to samples)
        while (waiting.size > keep) waiting.removeAt(0)
    }

    @SharedSynchronized
    fun size(): Int = waiting.size

    @SharedSynchronized
    fun take(): ShortArray? {
        if (waiting.isEmpty()) return null
        val (seq, samples) = waiting.removeAt(0)
        lastPlayed = seq
        return samples
    }
}
