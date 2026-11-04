package com.teera.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import com.teera.app.R
import java.util.Locale
import kotlin.random.Random

/** Something the table wants heard. */
enum class Cue {
    CLACK, SLIDE, KNOCK, YOUR_TURN, TEERA, X, BLOCKED, MATCH_WON, MATCH_LOST,
    EMOTE_GULP, EMOTE_DRUMROLL, EMOTE_SAD, EMOTE_SLAP,
}

/** Which of the two pieces of music suits the table right now. */
enum class Mood { CALM, TENSE }

/**
 * Plays the game.
 *
 * Every sound it uses was synthesised for this app (see tools/sound/synth.py), so none of
 * it carries a licence. Two choices matter beyond that:
 *
 * It never asks for audio focus. A player who has their own music going in another app
 * keeps it: our effects mix in on top, and our own music stays off while theirs plays.
 *
 * Spoken lines look first for a recording named voice_<line> in res/raw, and only fall
 * back to the phone's Arabic voice when there is none. Dropping a friend saying "ابلع"
 * into the project as voice_ablaa.ogg replaces the robot without touching any code.
 */
class SoundEngine(private val context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setAudioAttributes(attributes)
        .setMaxStreams(8)
        .build()

    private val effects: Map<Cue, List<Int>> = mapOf(
        Cue.CLACK to listOf(R.raw.sfx_clack_0, R.raw.sfx_clack_1, R.raw.sfx_clack_2),
        Cue.SLIDE to listOf(R.raw.sfx_slide),
        Cue.KNOCK to listOf(R.raw.sfx_knock),
        Cue.YOUR_TURN to listOf(R.raw.sfx_turn),
        Cue.TEERA to listOf(R.raw.sfx_teera),
        Cue.X to listOf(R.raw.sfx_x),
        Cue.BLOCKED to listOf(R.raw.sfx_blocked),
        Cue.MATCH_WON to listOf(R.raw.sfx_match_won),
        Cue.MATCH_LOST to listOf(R.raw.sfx_match_lost),
        Cue.EMOTE_GULP to listOf(R.raw.emote_gulp),
        Cue.EMOTE_DRUMROLL to listOf(R.raw.emote_drumroll),
        Cue.EMOTE_SAD to listOf(R.raw.emote_sad),
        Cue.EMOTE_SLAP to listOf(R.raw.emote_slap),
    ).mapValues { (_, resources) -> resources.map { pool.load(context, it, 1) } }

    /** How loud each kind of sound sits in the mix, so a clack never drowns a teera. */
    private val levels = mapOf(
        Cue.CLACK to 0.75f, Cue.SLIDE to 0.6f, Cue.KNOCK to 0.85f, Cue.YOUR_TURN to 0.45f,
    )

    private val prefs = context.getSharedPreferences("teera-sound", Context.MODE_PRIVATE)

    var effectsOn: Boolean = prefs.getBoolean("effects", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("effects", value).apply()
        }

    var musicOn: Boolean = prefs.getBoolean("music", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("music", value).apply()
            if (value) startMusic() else stopMusic()
        }

    // -- effects -----------------------------------------------------------

    fun play(cue: Cue) {
        if (!effectsOn) return
        val ids = effects[cue] ?: return
        val id = ids[Random.nextInt(ids.size)]
        val level = levels[cue] ?: 1f
        // A touch of pitch drift, so the hundredth clack of the night is not the same
        // clack as the first.
        val rate = if (cue == Cue.CLACK) 0.94f + Random.nextFloat() * 0.12f else 1f
        pool.play(id, level, level, 1, 0, rate)
    }

    // -- spoken lines -------------------------------------------------------

    private var voice: TextToSpeech? = null
    private var voiceReady = false

    private fun ensureVoice() {
        if (voice != null) return
        voice = TextToSpeech(context) { status ->
            val engine = voice ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val result = engine.setLanguage(Locale("ar"))
                voiceReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setSpeechRate(1.05f)
            }
        }
    }

    /**
     * Says a line. A recording called voice_[key] wins if one has been added to the
     * project; otherwise the phone reads [text] aloud, if it has an Arabic voice.
     */
    fun say(key: String, text: String) {
        if (!effectsOn) return
        val recorded = context.resources.getIdentifier("voice_$key", "raw", context.packageName)
        if (recorded != 0) {
            MediaPlayer.create(context, recorded)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
            return
        }
        ensureVoice()
        if (voiceReady) voice?.speak(text, TextToSpeech.QUEUE_ADD, null, key)
    }

    // -- music --------------------------------------------------------------

    private var music: MediaPlayer? = null
    private var mood = Mood.CALM
    private var paused = false

    fun setMood(next: Mood) {
        if (next == mood && music != null) return
        mood = next
        if (musicOn && music != null) {
            stopMusic()
            startMusic()
        }
    }

    fun startMusic() {
        if (!musicOn || paused || music != null) return
        // Somebody already has their own music on; ours would only fight it.
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.isMusicActive) return

        val track = if (mood == Mood.TENSE) R.raw.music_tense else R.raw.music_calm
        music = MediaPlayer.create(context, track)?.apply {
            isLooping = true
            setVolume(0.32f, 0.32f)
            start()
        }
    }

    fun stopMusic() {
        music?.run {
            runCatching { stop() }
            release()
        }
        music = null
    }

    fun onPause() {
        paused = true
        stopMusic()
    }

    fun onResume() {
        paused = false
        startMusic()
    }

    fun release() {
        stopMusic()
        pool.release()
        voice?.shutdown()
        voice = null
    }
}
