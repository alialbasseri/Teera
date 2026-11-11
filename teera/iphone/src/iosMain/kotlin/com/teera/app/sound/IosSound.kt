package com.teera.app.sound

import com.teera.app.IosPrefs
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import kotlin.random.Random

/** Something the table wants heard. */
enum class Cue {
    CLACK, SLIDE, KNOCK, YOUR_TURN, TEERA, X, BLOCKED, MATCH_WON, MATCH_LOST,
    EMOTE_GULP, EMOTE_DRUMROLL, EMOTE_SAD, EMOTE_SLAP, EMOTE_NO_WAY, EMOTE_CLINK, EMOTE_WHOOSH,
}

/** Which of the two pieces of music suits the table right now. */
enum class Mood { CALM, TENSE }

/**
 * Plays the game on an iPhone: the phone's own sounds (as .m4a, in Resources/Sounds of the
 * Xcode project), and spoken lines in the iPhone's Arabic voice. The app's audio session
 * mixes with the player's own music (TeeraApp.swift).
 */
class SoundEngine(private val prefs: IosPrefs) {

    private val effects: Map<Cue, List<String>> = mapOf(
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
    )

    private val levels = mapOf(
        Cue.CLACK to 0.75f, Cue.SLIDE to 0.6f, Cue.KNOCK to 0.85f, Cue.YOUR_TURN to 0.45f,
    )

    // Players still sounding: AVAudioPlayer stops when nothing holds it.
    private val playing = ArrayDeque<AVAudioPlayer>()

    private fun player(name: String): AVAudioPlayer? {
        val path = NSBundle.mainBundle.pathForResource(name, ofType = "m4a", inDirectory = "Resources/Sounds") ?: return null
        return AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
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

    fun play(cue: Cue) {
        if (!effectsOn) return
        val names = effects[cue] ?: return
        val sound = player(names[Random.nextInt(names.size)]) ?: return
        sound.volume = levels[cue] ?: 1f
        sound.play()
        playing.addLast(sound)
        while (playing.size > 12) playing.removeFirst()
    }

    private val voice = AVSpeechSynthesizer()

    /** Says a line in the iPhone's Arabic voice. */
    @Suppress("UNUSED_PARAMETER")
    fun say(key: String, text: String) {
        if (!effectsOn) return
        val line = AVSpeechUtterance.speechUtteranceWithString(text)
        line.voice = AVSpeechSynthesisVoice.voiceWithLanguage("ar-SA")
        voice.speakUtterance(line)
    }

    private var music: AVAudioPlayer? = null
    private var mood = Mood.CALM

    fun setMood(next: Mood) {
        if (next == mood && music != null) return
        mood = next
        if (musicOn && music != null) {
            stopMusic()
            startMusic()
        }
    }

    fun startMusic() {
        if (!musicOn || music != null) return
        music = player(if (mood == Mood.TENSE) "music_tense" else "music_calm")?.apply {
            numberOfLoops = -1
            volume = 0.32f
            play()
        }
    }

    fun stopMusic() {
        music?.stop()
        music = null
    }
}
