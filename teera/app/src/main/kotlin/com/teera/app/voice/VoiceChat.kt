package com.teera.app.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Talking across the table: this phone's microphone out, everybody else's voice in.
 *
 * Like a group call on speaker: the mic is open while it is switched on, but only what
 * sounds like talking is sent ([TalkGate]), and the phone's own echo cancelling keeps
 * friends from hearing themselves back. Everything else here is plumbing between
 * [VoiceWire] and Android's audio classes.
 */
class VoiceChat(context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Where this phone's voice goes: the game connection. */
    @Volatile
    var send: (ByteArray) -> Unit = {}

    private val _micOn = MutableStateFlow(false)
    val micOn: StateFlow<Boolean> = _micOn.asStateFlow()

    /** True while this phone is sending, for its own button to glow. */
    private val _talking = MutableStateFlow(false)
    val talking: StateFlow<Boolean> = _talking.asStateFlow()

    /** Seats heard in the last moment, to light their rings. */
    private val _speaking = MutableStateFlow<Set<Int>>(emptySet())
    val speaking: StateFlow<Set<Int>> = _speaking.asStateFlow()

    /** Off mutes everybody else on this phone; their voices are still sent to the rest. */
    private val _hearing = MutableStateFlow(true)
    val hearing: StateFlow<Boolean> = _hearing.asStateFlow()

    private var capture: Thread? = null
    @Volatile private var capturing = false
    private val voices = ConcurrentHashMap<Int, Voice>()
    private var modeBefore: Int? = null

    // -- this phone talking -------------------------------------------------------

    /** Needs the microphone permission already granted; the screen asks for it. */
    fun setMic(on: Boolean) {
        if (on == _micOn.value) return
        if (on) startCapture() else stopCapture()
        // Voices are played differently in and out of call mode (see [Voice]); start them
        // afresh so they follow. The next packet from each friend brings theirs back.
        dropVoices()
    }

    private fun dropVoices() {
        voices.values.forEach { it.stop() }
        voices.clear()
        _speaking.value = emptySet()
    }

    @SuppressLint("MissingPermission") // asked for by the screen before this is called
    private fun startCapture() {
        val minimum = AudioRecord.getMinBufferSize(
            VoiceWire.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                VoiceWire.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, VoiceWire.FRAME_SAMPLES * 2 * 4),
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            Log.w(TAG, "no microphone", e)
            null
        } ?: return

        enterCallMode()
        val effects = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(recorder.audioSessionId) else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(recorder.audioSessionId) else null,
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(recorder.audioSessionId) else null,
        ).onEach { runCatching { it.enabled = true } }

        capturing = true
        _micOn.value = true
        capture = thread(name = "teera-mic", isDaemon = true) {
            val gate = TalkGate()
            val frame = ShortArray(VoiceWire.FRAME_SAMPLES)
            var previous: ShortArray? = null
            var wasOpen = false
            var seq = 0
            try {
                recorder.startRecording()
                while (capturing) {
                    var filled = 0
                    while (filled < frame.size && capturing) {
                        val read = recorder.read(frame, filled, frame.size - filled)
                        if (read <= 0) break
                        filled += read
                    }
                    if (filled < frame.size) continue
                    val open = gate.open(frame)
                    if (open) {
                        // The frame before the gate opened usually holds the start of the word.
                        if (!wasOpen) previous?.let { send(VoiceWire.pack(seq++ and 0xFFFF, it)) }
                        send(VoiceWire.pack(seq++ and 0xFFFF, frame))
                    }
                    if (open != wasOpen) _talking.value = open
                    wasOpen = open
                    previous = frame.copyOf()
                }
            } catch (e: Exception) {
                Log.w(TAG, "microphone stopped", e)
            } finally {
                runCatching { recorder.stop() }
                effects.forEach { runCatching { it.release() } }
                recorder.release()
                _talking.value = false
            }
        }
    }

    private fun stopCapture() {
        capturing = false
        capture?.join(500)
        capture = null
        _micOn.value = false
        _talking.value = false
        leaveCallMode()
    }

    /**
     * Echo cancelling only works properly in the phone's call mode, and call mode plays
     * through the earpiece unless told otherwise. Unless there are headphones in, put it
     * on the loudspeaker, like a group call lying on the table.
     */
    private fun enterCallMode() {
        if (modeBefore != null) return
        modeBefore = audio.mode
        runCatching {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!headphonesIn()) loudspeaker(true)
        }
    }

    private fun leaveCallMode() {
        val before = modeBefore ?: return
        modeBefore = null
        runCatching {
            loudspeaker(false)
            audio.mode = before
        }
    }

    @Suppress("DEPRECATION")
    private fun loudspeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audio.setCommunicationDevice(it) }
            } else {
                audio.clearCommunicationDevice()
            }
        } else {
            audio.isSpeakerphoneOn = on
        }
    }

    private fun headphonesIn(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type in setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }

    // -- everybody else -----------------------------------------------------------

    fun setHearing(on: Boolean) {
        _hearing.value = on
        if (!on) dropVoices()
    }

    /** One relayed packet from the connection. */
    /** Seats this player has blocked: their voice is not played. */
    @Volatile
    var mutedSeats: Set<Int> = emptySet()

    fun receive(relayed: ByteArray) {
        if (!_hearing.value) return
        val heard = VoiceWire.unpack(relayed) ?: return
        if (heard.seat in mutedSeats) return
        val voice = voices.getOrPut(heard.seat) { Voice(heard.seat) }
        voice.inbox.offer(heard.seq, heard.samples, SystemClock.elapsedRealtime())
        voice.wake()
    }

    /** Everything off: leaving the table, or the app going into the background. */
    fun stopAll() {
        setMic(false)
        dropVoices()
    }

    /**
     * One friend's voice: their packets, a thread that plays them as they come, and a
     * speaker stream of its own. It waits for two packets before starting, which absorbs
     * the usual unevenness of a mobile connection without adding noticeable delay.
     *
     * With this phone's mic on, the phone is in call mode and voices play as a call does,
     * which is what lets the echo canceller hear them to take them back out. With it off,
     * a call stream would go to the earpiece, so they play as ordinary sound instead.
     */
    private inner class Voice(private val seat: Int) {
        val inbox = Inbox()
        private val lock = java.lang.Object()
        @Volatile private var running = true
        private val track: AudioTrack? = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (_micOn.value) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA,
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(VoiceWire.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(
                    maxOf(
                        AudioTrack.getMinBufferSize(
                            VoiceWire.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        ),
                        VoiceWire.FRAME_SAMPLES * 2 * 3,
                    ),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.onFailure { Log.w(TAG, "no speaker stream", it) }.getOrNull()

        private val player = thread(name = "teera-voice-$seat", isDaemon = true) { play() }

        fun wake() = synchronized(lock) { lock.notifyAll() }

        private fun play() {
            val out = track ?: return
            try {
                out.play()
                while (running) {
                    // Idle until somebody speaks, then gather a little before starting.
                    synchronized(lock) { if (inbox.size() == 0) lock.wait(250) }
                    if (inbox.size() == 0) {
                        lit(false)
                        continue
                    }
                    val startedWaiting = SystemClock.elapsedRealtime()
                    while (running && inbox.size() < 2 && SystemClock.elapsedRealtime() - startedWaiting < 90) {
                        synchronized(lock) { lock.wait(20) }
                    }
                    lit(true)
                    // write() blocks until the speaker has room, which keeps this in time.
                    while (running) {
                        val samples = inbox.take() ?: break
                        out.write(samples, 0, samples.size)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "voice for seat $seat stopped", e)
            } finally {
                lit(false)
                runCatching { out.stop() }
                out.release()
            }
        }

        private fun lit(on: Boolean) = _speaking.update { if (on) it + seat else it - seat }

        fun stop() {
            running = false
            wake()
        }
    }

    private companion object {
        const val TAG = "VoiceChat"
    }
}
