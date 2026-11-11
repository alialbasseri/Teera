package com.teera.app.voice

import com.teera.app.Lock
import com.teera.app.PlatformApp
import com.teera.app.locked
import kotlin.concurrent.Volatile
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.setActive

/**
 * Talking across the table from an iPhone: the same packets as the phone ([VoiceWire]),
 * so iPhones, phones and computers hear each other. Only what sounds like talking is sent
 * ([TalkGate]); while the mic is on the iPhone is in voice-chat mode, whose own echo
 * cancelling keeps friends from hearing themselves back, on the loudspeaker like a group
 * call lying on the table.
 */
@Suppress("UNUSED_PARAMETER")
class VoiceChat(app: PlatformApp) {

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

    @Volatile
    var mutedSeats: Set<Int> = emptySet()

    private val lock = Lock()
    private var engine: AVAudioEngine? = null
    private val voices = HashMap<Int, Voice>()

    /** Whether the player has let the app use the microphone (asked once, by the screen). */
    val allowed: Boolean get() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    /** Asks for the microphone; [answer] is told on whatever thread iOS answers on. */
    fun askForMic(answer: (Boolean) -> Unit) = AVAudioSession.sharedInstance().requestRecordPermission { granted -> answer(granted) }

    // -- this iPhone talking ------------------------------------------------------

    fun setMic(on: Boolean) = lock.locked {
        if (on == _micOn.value) return@locked
        // Voices start afresh in the new mode; the next packet from each brings theirs back.
        stopEngine()
        if (on && allowed) {
            callMode(true)
            if (startEngine(withMic = true)) _micOn.value = true else callMode(false)
        } else {
            _micOn.value = false
            _talking.value = false
            callMode(false)
        }
    }

    private fun callMode(on: Boolean) {
        val session = AVAudioSession.sharedInstance()
        if (on) {
            session.setCategory(
                AVAudioSessionCategoryPlayAndRecord!!,
                mode = AVAudioSessionModeVoiceChat!!,
                options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth or
                    AVAudioSessionCategoryOptionMixWithOthers,
                error = null,
            )
        } else {
            session.setCategory(AVAudioSessionCategoryAmbient!!, mode = AVAudioSessionModeDefault!!, options = AVAudioSessionCategoryOptionMixWithOthers, error = null)
        }
        session.setActive(true, error = null)
    }

    /** One engine plays everybody's voice and, with [withMic], listens to this iPhone's. */
    private fun startEngine(withMic: Boolean): Boolean {
        val fresh = AVAudioEngine()
        if (withMic) {
            val input = fresh.inputNode
            input.setVoiceProcessingEnabled(true, error = null)
            val format = input.outputFormatForBus(0u)
            val mic = Mic(format.sampleRate, format.commonFormat == AVAudioPCMFormatInt16)
            input.installTapOnBus(0u, bufferSize = 1024u, format = format) { buffer, _ ->
                if (buffer != null) mic.heard(buffer)
            }
        }
        // The mixer has to exist before the engine starts.
        fresh.mainMixerNode
        fresh.prepare()
        if (!fresh.startAndReturnError(null)) return false
        engine = fresh
        return true
    }

    private fun stopEngine() {
        voices.values.forEach { it.stop() }
        voices.clear()
        _speaking.value = emptySet()
        engine?.let { running ->
            if (_micOn.value) running.inputNode.removeTapOnBus(0u)
            running.stop()
        }
        engine = null
    }

    /** Turns the iPhone's microphone into the wire's frames: 16 kHz, 640 samples each. */
    private inner class Mic(private val rate: Double, private val shorts: Boolean) {
        private val gate = TalkGate()
        private val step = rate / VoiceWire.SAMPLE_RATE
        private var position = 0.0
        private val frame = ShortArray(VoiceWire.FRAME_SAMPLES)
        private var filled = 0
        private var previous: ShortArray? = null
        private var wasOpen = false
        private var seq = 0

        fun heard(buffer: AVAudioPCMBuffer) {
            val count = buffer.frameLength.toInt()
            val floats = if (shorts) null else buffer.floatChannelData?.get(0)
            val ints = if (shorts) buffer.int16ChannelData?.get(0) else null
            if (floats == null && ints == null) return
            // Down to 16 kHz by stepping through the input, averaging what each step covers.
            while (position < count) {
                val start = position.toInt()
                val end = minOf(count, (position + step).toInt().coerceAtLeast(start + 1))
                var sum = 0f
                for (i in start until end) sum += floats?.get(i) ?: (ints!![i] / 32768f)
                val value = (sum / (end - start)).coerceIn(-1f, 1f)
                frame[filled++] = (value * 32767).toInt().toShort()
                if (filled == frame.size) {
                    deliver(frame.copyOf())
                    filled = 0
                }
                position += step
            }
            position -= count
        }

        private fun deliver(samples: ShortArray) {
            val open = gate.open(samples)
            if (open) {
                // The frame before the gate opened usually holds the start of the word.
                if (!wasOpen) previous?.let { send(VoiceWire.pack(seq++ and 0xFFFF, it)) }
                send(VoiceWire.pack(seq++ and 0xFFFF, samples))
            }
            if (open != wasOpen) _talking.value = open
            wasOpen = open
            previous = samples
        }
    }

    // -- everybody else -----------------------------------------------------------

    fun setHearing(on: Boolean) = lock.locked {
        _hearing.value = on
        if (!on) {
            voices.values.forEach { it.stop() }
            voices.clear()
            _speaking.value = emptySet()
        }
    }

    fun receive(relayed: ByteArray) = lock.locked {
        if (!_hearing.value) return@locked
        val heard = VoiceWire.unpack(relayed) ?: return@locked
        if (heard.seat in mutedSeats) return@locked
        if (engine == null && !startEngine(withMic = false)) return@locked
        val running = engine ?: return@locked
        val voice = voices.getOrPut(heard.seat) { Voice(heard.seat, running) }
        voice.inbox.offer(heard.seq, heard.samples, com.teera.app.uptimeMillis())
        voice.feed()
    }

    fun stopAll() {
        setMic(false)
        lock.locked { stopEngine() }
    }

    /**
     * One friend's voice: a player node of its own on the engine. Packets wait in the
     * [Inbox] (in order, late ones dropped) and are handed to the speaker a few at a time,
     * which absorbs the usual unevenness of a mobile connection without adding much delay.
     */
    private inner class Voice(private val seat: Int, engine: AVAudioEngine) {
        val inbox = Inbox()
        private val format = AVAudioFormat(AVAudioPCMFormatFloat32, VoiceWire.SAMPLE_RATE.toDouble(), 1u, false)
        private val player = AVAudioPlayerNode()
        private var queued = 0
        private var started = false

        init {
            engine.attachNode(player)
            engine.connect(player, to = engine.mainMixerNode, format = format)
        }

        fun feed() {
            // Gather two before starting; after that keep three frames ahead at most.
            if (!started && inbox.size() < 2) return
            started = true
            while (queued < 3) {
                val samples = inbox.take() ?: break
                val buffer = AVAudioPCMBuffer(format, samples.size.toUInt()) ?: break
                buffer.frameLength = samples.size.toUInt()
                val channel = buffer.floatChannelData?.get(0) ?: break
                for (i in samples.indices) channel[i] = samples[i] / 32768f
                queued++
                player.scheduleBuffer(buffer) {
                    lock.locked {
                        queued--
                        if (queued == 0 && inbox.size() == 0) {
                            started = false
                            lit(false)
                        } else {
                            feed()
                        }
                    }
                }
            }
            if (queued > 0) {
                if (!player.playing) player.play()
                lit(true)
            }
        }

        private fun lit(on: Boolean) = _speaking.update { if (on) it + seat else it - seat }

        fun stop() {
            player.stop()
            lit(false)
        }
    }
}
