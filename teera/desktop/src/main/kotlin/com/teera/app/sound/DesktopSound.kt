package com.teera.app.sound

import com.teera.app.DesktopPrefs
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10
import kotlin.random.Random

/** Something the table wants heard. */
enum class Cue {
    CLACK, SLIDE, KNOCK, YOUR_TURN, TEERA, X, BLOCKED, MATCH_WON, MATCH_LOST,
    EMOTE_GULP, EMOTE_DRUMROLL, EMOTE_SAD, EMOTE_SLAP, EMOTE_NO_WAY, EMOTE_CLINK, EMOTE_WHOOSH,
}

/** Which of the two pieces of music suits the table right now. */
enum class Mood { CALM, TENSE }

/**
 * Plays the game on a computer: the phone's own sounds (as WAV, which Java plays without
 * help), through javax.sound. Spoken lines are left silent here: Windows seldom has an
 * Arabic voice, and the words show on the table anyway.
 */
class SoundEngine(private val prefs: DesktopPrefs) {

    private class Sample(val format: AudioFormat, val bytes: ByteArray)

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "teera-sound").apply { isDaemon = true } }

    private fun load(name: String): Sample? = runCatching {
        val url = javaClass.getResource("/sounds/$name.wav") ?: return null
        AudioSystem.getAudioInputStream(url.openStream().buffered()).use { stream ->
            Sample(stream.format, stream.readAllBytes())
        }
    }.getOrNull()

    private val effects: Map<Cue, List<Sample>> by lazy {
        mapOf(
            Cue.CLACK to listOf("sfx_clack_0", "sfx_clack_1", "sfx_clack_2"),
            Cue.SLIDE to listOf("sfx_slide"),
            Cue.KNOCK to listOf("sfx_knock"),
            Cue.YOUR_TURN to listOf("sfx_turn"),
            Cue.TEERA to listOf("sfx_teera"),
            Cue.X to listOf("sfx_x"),
            Cue.BLOCKED to listOf("sfx_blocked"),
            Cue.MATCH_WON to listOf("sfx_match_won"),
            Cue.MATCH_LOST to listOf("sfx_match_lost"),
            Cue.EMOTE_GULP to listOf("emote_gulp"),
            Cue.EMOTE_DRUMROLL to listOf("emote_drumroll"),
            Cue.EMOTE_SAD to listOf("emote_sad"),
            Cue.EMOTE_SLAP to listOf("emote_slap"),
            Cue.EMOTE_NO_WAY to listOf("emote_no_way"),
            Cue.EMOTE_CLINK to listOf("emote_clink"),
            Cue.EMOTE_WHOOSH to listOf("emote_whoosh"),
        ).mapValues { (_, names) -> names.mapNotNull(::load) }
    }

    /** How loud each kind of sound sits in the mix, so a clack never drowns a teera. */
    private val levels = mapOf(
        Cue.CLACK to 0.75f, Cue.SLIDE to 0.6f, Cue.KNOCK to 0.85f, Cue.YOUR_TURN to 0.45f,
    )

    init {
        // Read the sounds now, off the window's thread, so the first clack is not late.
        worker.execute { effects.size }
    }

    var effectsOn: Boolean = prefs.getBoolean("sound.effects", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("sound.effects", value).apply()
        }

    var musicOn: Boolean = prefs.getBoolean("sound.music", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("sound.music", value).apply()
            if (value) startMusic() else stopMusic()
        }

    private fun open(sample: Sample, level: Float): Clip? = runCatching {
        val clip = AudioSystem.getClip()
        clip.open(AudioInputStream(ByteArrayInputStream(sample.bytes), sample.format, sample.bytes.size.toLong() / sample.format.frameSize))
        if (level < 1f && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            gain.value = (20f * log10(level.coerceAtLeast(0.01f))).coerceIn(gain.minimum, gain.maximum)
        }
        clip
    }.getOrNull()

    fun play(cue: Cue) {
        if (!effectsOn) return
        worker.execute {
            val samples = effects[cue].orEmpty()
            if (samples.isEmpty()) return@execute
            val clip = open(samples[Random.nextInt(samples.size)], levels[cue] ?: 1f) ?: return@execute
            clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP) clip.close() }
            clip.start()
        }
    }

    /** The phone reads these aloud; a computer shows them only. */
    @Suppress("UNUSED_PARAMETER")
    fun say(key: String, text: String) = Unit

    // -- music --------------------------------------------------------------

    @Volatile private var music: Clip? = null
    @Volatile private var playing = false
    private var mood = Mood.CALM

    fun setMood(next: Mood) {
        if (next == mood && playing) return
        mood = next
        if (musicOn && playing) {
            stopMusic()
            startMusic()
        }
    }

    fun startMusic() {
        if (!musicOn || playing) return
        playing = true
        val track = if (mood == Mood.TENSE) "music_tense" else "music_calm"
        worker.execute {
            if (!playing || music != null) return@execute
            val sample = load(track) ?: return@execute
            music = open(sample, 0.32f)?.apply { loop(Clip.LOOP_CONTINUOUSLY) }
        }
    }

    fun stopMusic() {
        playing = false
        worker.execute {
            music?.run {
                runCatching { stop() }
                close()
            }
            music = null
        }
    }

    fun release() {
        stopMusic()
        worker.shutdown()
    }
}
