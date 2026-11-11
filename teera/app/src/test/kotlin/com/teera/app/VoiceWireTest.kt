package com.teera.app

import com.teera.app.voice.Adpcm
import com.teera.app.voice.Inbox
import com.teera.app.voice.TalkGate
import com.teera.app.voice.VoiceWire
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class VoiceWireTest {

    /** Something voice-like: a few harmonics of a speaking pitch, rising and falling. */
    private fun speech(samples: Int, seed: Int = 1, loudness: Double = 9_000.0): ShortArray {
        val random = Random(seed)
        return ShortArray(samples) { i ->
            val t = i / VoiceWire.SAMPLE_RATE.toDouble()
            val envelope = 0.6 + 0.4 * sin(2 * PI * 3 * t)
            val v = (1..5).sumOf { h -> sin(2 * PI * 140 * h * t + h) / h } * envelope * loudness + random.nextInt(-200, 200)
            v.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun snr(original: ShortArray, decoded: ShortArray): Double {
        var signal = 0.0
        var noise = 0.0
        for (i in original.indices) {
            signal += original[i].toDouble() * original[i]
            val e = original[i] - decoded[i].toDouble()
            noise += e * e
        }
        return 10 * log10(signal / noise)
    }

    @Test
    fun `speech survives the codec clearly`() {
        val frame = speech(VoiceWire.FRAME_SAMPLES)
        val block = Adpcm.encode(frame)
        assertEquals(Adpcm.HEADER + VoiceWire.FRAME_SAMPLES / 2, block.size, "four bits a sample")
        val back = Adpcm.decode(block, count = frame.size)
        assertEquals(frame.size, back.size)
        assertTrue(snr(frame, back) > 18.0, "clear enough to understand: ${snr(frame, back)} dB")
    }

    @Test
    fun `a packet goes round the referee and comes back as the same voice from the right seat`() {
        val frame = speech(VoiceWire.FRAME_SAMPLES, seed = 4)
        val sent = VoiceWire.pack(seq = 65_534, frame = frame)
        val relayed = byteArrayOf(2) + sent // what the referee does: put the seat in front
        val heard = assertNotNull(VoiceWire.unpack(relayed))
        assertEquals(2, heard.seat)
        assertEquals(65_534, heard.seq)
        assertEquals(VoiceWire.FRAME_SAMPLES, heard.samples.size)
        assertTrue(snr(frame, heard.samples) > 18.0)
    }

    @Test
    fun `nonsense on the wire is ignored rather than crashing the table`() {
        assertNull(VoiceWire.unpack(byteArrayOf()))
        assertNull(VoiceWire.unpack(byteArrayOf(1, 1, 0)))
        assertNull(VoiceWire.unpack(byteArrayOf(9) + VoiceWire.pack(1, speech(640))), "no such seat")
        val fromTheFuture = VoiceWire.pack(1, speech(640)).also { it[0] = 7 }
        assertNull(VoiceWire.unpack(byteArrayOf(1) + fromTheFuture), "a codec this app does not know")
    }

    @Test
    fun `a quiet room sends nothing, talking opens the gate, and it holds through a pause`() {
        val gate = TalkGate(hangFrames = 5)
        val hum = ShortArray(640) { (Random.nextInt(-150, 150)).toShort() }
        repeat(30) { assertFalse(gate.open(hum), "background hum is not talking") }
        assertTrue(gate.open(speech(640)))
        repeat(5) { assertTrue(gate.open(hum), "a breath between words keeps it open") }
        assertFalse(gate.open(hum), "and it closes after")
    }

    @Test
    fun `packets are played in order, late ones dropped, and the queue never runs far behind`() {
        val inbox = Inbox(keep = 3)
        val s = ShortArray(1)
        var now = 0L
        inbox.offer(11, s, now); inbox.offer(10, s, now); inbox.offer(12, s, now)
        assertEquals(3, inbox.size())
        inbox.take(); inbox.take(); inbox.take()
        inbox.offer(11, s, now) // arrived after its turn
        assertEquals(0, inbox.size(), "too late to be useful")
        for (seq in 13..20) inbox.offer(seq, s, now)
        assertEquals(3, inbox.size(), "only the newest few are kept")
        // Across the counter wrapping round, order still holds.
        val wrap = Inbox()
        wrap.offer(65_535, shortArrayOf(1), now); wrap.offer(0, shortArrayOf(2), now); wrap.offer(65_534, shortArrayOf(0), now)
        assertEquals(listOf<Short>(0, 1, 2), List(3) { wrap.take()!![0] })
        // A sender that restarts its count after a pause is heard again.
        now += 5_000
        wrap.offer(3, shortArrayOf(9), now)
        assertEquals(9.toShort(), wrap.take()!![0])
    }
}
