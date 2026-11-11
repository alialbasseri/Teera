package com.teera.app.voice

import com.teera.app.PlatformApp
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Talking across the table from a computer: the same packets as the phone ([VoiceWire]),
 * through the computer's microphone and speakers (javax.sound). Only what sounds like
 * talking is sent ([TalkGate]). A computer has no call mode to cancel its own echo, so
 * headphones are best; the gate keeps quiet rooms quiet either way.
 */
@Suppress("UNUSED_PARAMETER")
class VoiceChat(app: PlatformApp) {

    /** Where this computer's voice goes: the game connection. */
    @Volatile
    var send: (ByteArray) -> Unit = {}

    private val _micOn = MutableStateFlow(false)
    val micOn: StateFlow<Boolean> = _micOn.asStateFlow()

    private val _talking = MutableStateFlow(false)
    val talking: StateFlow<Boolean> = _talking.asStateFlow()

    private val _speaking = MutableStateFlow<Set<Int>>(emptySet())
    val speaking: StateFlow<Set<Int>> = _speaking.asStateFlow()

    private val _hearing = MutableStateFlow(true)
    val hearing: StateFlow<Boolean> = _hearing.asStateFlow()

    /** Seats this player has blocked: their voice is not played. */
    @Volatile
    var mutedSeats: Set<Int> = emptySet()

    private val format = AudioFormat(VoiceWire.SAMPLE_RATE.toFloat(), 16, 1, true, false)
    private var capture: Thread? = null
    @Volatile private var capturing = false
    private val voices = ConcurrentHashMap<Int, Voice>()

    // -- this computer talking ----------------------------------------------------

    fun setMic(on: Boolean) {
        if (on == _micOn.value) return
        if (on) startCapture() else stopCapture()
    }

    private fun startCapture() {
        val mic = runCatching {
            (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine).apply {
                open(format, VoiceWire.FRAME_SAMPLES * 2 * 4)
            }
        }.getOrNull() ?: return

        capturing = true
        _micOn.value = true
        capture = thread(name = "teera-mic", isDaemon = true) {
            val gate = TalkGate()
            val bytes = ByteArray(VoiceWire.FRAME_SAMPLES * 2)
            var previous: ShortArray? = null
            var wasOpen = false
            var seq = 0
            try {
                mic.start()
                while (capturing) {
                    var filled = 0
                    while (filled < bytes.size && capturing) {
                        val read = mic.read(bytes, filled, bytes.size - filled)
                        if (read <= 0) break
                        filled += read
                    }
                    if (filled < bytes.size) continue
                    val frame = ShortArray(VoiceWire.FRAME_SAMPLES) { i ->
                        ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
                    }
                    val open = gate.open(frame)
                    if (open) {
                        // The frame before the gate opened usually holds the start of the word.
                        if (!wasOpen) previous?.let { send(VoiceWire.pack(seq++ and 0xFFFF, it)) }
                        send(VoiceWire.pack(seq++ and 0xFFFF, frame))
                    }
                    if (open != wasOpen) _talking.value = open
                    wasOpen = open
                    previous = frame
                }
            } catch (e: Exception) {
                // The microphone went away (unplugged, or taken by another app).
            } finally {
                runCatching { mic.stop() }
                mic.close()
                _talking.value = false
                _micOn.value = false
            }
        }
    }

    private fun stopCapture() {
        capturing = false
        capture?.join(500)
        capture = null
        _micOn.value = false
        _talking.value = false
    }

    // -- everybody else -----------------------------------------------------------

    fun setHearing(on: Boolean) {
        _hearing.value = on
        if (!on) dropVoices()
    }

    fun receive(relayed: ByteArray) {
        if (!_hearing.value) return
        val heard = VoiceWire.unpack(relayed) ?: return
        if (heard.seat in mutedSeats) return
        val voice = voices.getOrPut(heard.seat) { Voice(heard.seat) }
        voice.inbox.offer(heard.seq, heard.samples, System.currentTimeMillis())
        voice.wake()
    }

    fun stopAll() {
        setMic(false)
        dropVoices()
    }

    private fun dropVoices() {
        voices.values.forEach { it.stop() }
        voices.clear()
        _speaking.value = emptySet()
    }

    /** One friend's voice: their packets, and a thread that plays them as they come. */
    private inner class Voice(private val seat: Int) {
        val inbox = Inbox()
        private val lock = Object()
        @Volatile private var running = true

        private val speaker: SourceDataLine? = runCatching {
            (AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine).apply {
                open(format, VoiceWire.FRAME_SAMPLES * 2 * 3)
            }
        }.getOrNull()

        private val player = thread(name = "teera-voice-$seat", isDaemon = true) { play() }

        fun wake() = synchronized(lock) { lock.notifyAll() }

        private fun play() {
            val out = speaker ?: return
            val bytes = ByteArray(VoiceWire.FRAME_SAMPLES * 2)
            try {
                out.start()
                while (running) {
                    // Idle until somebody speaks, then gather a little before starting.
                    synchronized(lock) { if (inbox.size() == 0) lock.wait(250) }
                    if (inbox.size() == 0) {
                        lit(false)
                        continue
                    }
                    val startedWaiting = System.currentTimeMillis()
                    while (running && inbox.size() < 2 && System.currentTimeMillis() - startedWaiting < 90) {
                        synchronized(lock) { lock.wait(20) }
                    }
                    lit(true)
                    // write() blocks until the speaker has room, which keeps this in time.
                    while (running) {
                        val samples = inbox.take() ?: break
                        for (i in samples.indices) {
                            bytes[2 * i] = samples[i].toInt().toByte()
                            bytes[2 * i + 1] = (samples[i].toInt() shr 8).toByte()
                        }
                        out.write(bytes, 0, samples.size * 2)
                    }
                }
            } catch (e: Exception) {
                // The speakers went away; this voice simply stops.
            } finally {
                lit(false)
                runCatching { out.stop() }
                out.close()
            }
        }

        private fun lit(on: Boolean) = _speaking.update { if (on) it + seat else it - seat }

        fun stop() {
            running = false
            wake()
        }
    }
}
