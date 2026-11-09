package com.teera.app.sound

import com.teera.core.Emote
import com.teera.core.GameEnd
import com.teera.core.GameEvent
import com.teera.core.PlayerView

/** A line said aloud: [key] names a recording that may replace the phone's voice. */
data class Line(val key: String, val text: String)

/** Everything the table should sound like after one change. */
data class Reaction(
    val cues: List<Cue> = emptyList(),
    val lines: List<Line> = emptyList(),
    val mood: Mood? = null,
)

/**
 * Works out what a change at the table sounds like.
 *
 * Kept as a plain function of two views so it can be tested without a phone, and so the
 * same events always make the same sounds online and off.
 */
fun react(previous: PlayerView?, next: PlayerView): Reaction {
    val cues = mutableListOf<Cue>()
    val lines = mutableListOf<Line>()

    for (event in next.events) {
        when (event) {
            is GameEvent.Opened, is GameEvent.Played, is GameEvent.PlayedFor -> {
                cues += Cue.CLACK
                // The two tiles a teera is flown with: somebody just gave one up.
                val laid = when (event) {
                    is GameEvent.Opened -> event.domino
                    is GameEvent.Played -> event.domino
                    is GameEvent.PlayedFor -> event.domino
                    else -> null
                }
                if (laid in TEERA_TILES) lines += Line("barto", "برطو")
            }
            is GameEvent.Gave -> cues += Cue.SLIDE
            is GameEvent.Dealt, is GameEvent.Redealt -> cues += Cue.SLIDE
            // The stuck player knocks the table and asks the one before them which it is.
            is GameEvent.Stuck -> {
                cues += Cue.KNOCK
                lines += Line("tilab_tinti", "تلعب تنطي")
            }
            is GameEvent.TeeraCalled -> lines += Line("teera", "طيرة")
            is GameEvent.GameEnded -> when (event.result.end) {
                GameEnd.X -> cues += Cue.X
                GameEnd.TEERA -> cues += Cue.TEERA
                GameEnd.BLOCKED -> {
                    cues += Cue.BLOCKED
                    lines += Line("sadd", "قفلت")
                }
            }
            is GameEvent.MatchEnded ->
                cues += if (event.losingTeam == next.yourTeam) Cue.MATCH_LOST else Cue.MATCH_WON
            is GameEvent.AlertUpheld -> cues += Cue.KNOCK
        }
    }

    // Both of them on the table now: nobody can fly this game any more.
    val bothDown = next.board.tiles().containsAll(TEERA_TILES)
    val wereDown = previous?.takeIf { it.gameNumber == next.gameNumber }?.board?.tiles()?.containsAll(TEERA_TILES) == true
    if (bothDown && !wereDown) lines += Line("hay_alzilim", "هاااي الزلم")

    // A chime when the table comes round to you, not every time you are shown it.
    if (next.isYourMove && previous?.isYourMove != true) cues += Cue.YOUR_TURN

    return Reaction(cues = cues, lines = lines, mood = moodOf(next))
}

/**
 * The music tightens as the match does: when either side is closing on the target, or
 * somebody is down to their last couple of bones.
 */
fun moodOf(view: PlayerView): Mood {
    val nearTarget = view.scores.any { it >= view.rules.target - TENSE_MARGIN }
    val nearlyOut = view.handCounts.any { it in 1..2 }
    return if (nearTarget || nearlyOut) Mood.TENSE else Mood.CALM
}

private const val TENSE_MARGIN = 50

/** Zero-zero and zero-one: the only tiles a hand can be left holding to call a teera. */
private val TEERA_TILES = listOf(com.teera.core.Domino.of(0, 0), com.teera.core.Domino.of(0, 1))

/** What an emote sounds like, and what is said with it. */
fun react(emote: Emote): Reaction = when (emote) {
    Emote.GULP -> Reaction(listOf(Cue.EMOTE_GULP), listOf(Line("ablaa", "ابلع")))
    Emote.DRUMROLL -> Reaction(listOf(Cue.EMOTE_DRUMROLL))
    Emote.SAD -> Reaction(listOf(Cue.EMOTE_SAD), listOf(Line("raahat", "راحت عليك")))
    Emote.SLAP -> Reaction(listOf(Cue.EMOTE_SLAP), listOf(Line("khudh", "خذ")))
    Emote.NO_WAY -> Reaction(listOf(Cue.EMOTE_NO_WAY), listOf(Line("ma_teegh", "بالله ما طيغ!")))
    Emote.CHAT -> Reaction(listOf(Cue.EMOTE_CLINK), listOf(Line("nsoolif", "يلا شباب خلي نسولف")))
    Emote.SHEEL -> Reaction(listOf(Cue.EMOTE_WHOOSH), listOf(Line("sheel", "شيل")))
    Emote.SPLOOSH -> Reaction(listOf(Cue.EMOTE_SLAP), listOf(Line("sploosh", "سبلوش")))
    // Retired: silent even if an older phone sends it.
    Emote.WALLAH -> Reaction()
}
