package com.teera.app.sound

import com.teera.app.WebPrefs
import kotlin.random.Random

/** Something the table wants heard. */
enum class Cue {
    CLACK, SLIDE, KNOCK, YOUR_TURN, TEERA, X, BLOCKED, MATCH_WON, MATCH_LOST,
    EMOTE_GULP, EMOTE_DRUMROLL, EMOTE_SAD, EMOTE_SLAP, EMOTE_NO_WAY, EMOTE_CLINK, EMOTE_WHOOSH,
}

/** Which of the two pieces of music suits the table right now. */
enum class Mood { CALM, TENSE }

private fun jsPreload(src: String): Unit = js("{ try { const a = new Audio(src); a.preload = 'auto'; a.load(); } catch (e) {} }")
private fun jsPlay(src: String, volume: Double): Unit =
    js("{ try { const a = new Audio(src); a.volume = volume; a.play().catch(() => {}); } catch (e) {} }")
private fun jsMusic(src: String, volume: Double): JsAny =
    js("(() => { const a = new Audio(src); a.loop = true; a.volume = volume; a.play().catch(() => {}); return a; })()")
private fun jsStop(audio: JsAny): Unit = js("{ try { audio.pause(); } catch (e) {} }")
private fun jsSpeak(text: String): Unit = js(
    "{ try { const u = new SpeechSynthesisUtterance(text); u.lang = 'ar-SA'; u.rate = 1.05;" +
        " const v = speechSynthesis.getVoices().find(v => v.lang && v.lang.startsWith('ar')); if (v) u.voice = v;" +
        " speechSynthesis.speak(u); } catch (e) {} }",
)

/**
 * Plays the game in a browser: the same sounds as the phone (AAC, which every browser
 * plays) and the spoken lines in the browser's Arabic voice, where it has one.
 */
class SoundEngine(private val prefs: WebPrefs) {

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
        Cue.CLACK to 0.75, Cue.SLIDE to 0.6, Cue.KNOCK to 0.85, Cue.YOUR_TURN to 0.45,
    )

    private fun src(name: String) = "sounds/$name.m4a"

    init {
        effects.values.flatten().forEach { jsPreload(src(it)) }
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
        jsPlay(src(names[Random.nextInt(names.size)]), levels[cue] ?: 1.0)
    }

    @Suppress("UNUSED_PARAMETER")
    fun say(key: String, text: String) {
        if (effectsOn) jsSpeak(text)
    }

    private var music: JsAny? = null
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
        music = jsMusic(src(if (mood == Mood.TENSE) "music_tense" else "music_calm"), 0.32)
    }

    fun stopMusic() {
        music?.let(::jsStop)
        music = null
    }
}
