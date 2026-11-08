package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino
import com.teera.core.GameEnd
import com.teera.core.GameEvent
import com.teera.core.PlayerView

/**
 * One line of the after-game review: who did what, and the bone it was done with.
 * [seat] colours the line by team; null for the table's own lines (the deal, the end).
 */
data class Step(val text: String, val domino: Domino? = null, val seat: Int? = null)

/**
 * The game told move by move, for after it is over. Only ever offered once the game has
 * ended: during play it would count the bones for you, and counting is the players' job.
 */
fun stepsOf(view: PlayerView, events: List<GameEvent>): List<Step> {
    fun name(seat: Int): String {
        val info = view.seatInfo(seat)
        return when {
            info == null || info.name.isBlank() -> "لاعب"
            info.isBot -> "بوت ${seat + 1}"
            else -> info.name.trim()
        }
    }
    return stepsOf({ name(it) }, { view.teamName(it) }, events)
}

/** The same, for a match from the books: names by seat. */
fun stepsOf(names: List<String>, events: List<GameEvent>): List<Step> {
    fun name(seat: Int) = names.getOrNull(seat)?.trim().orEmpty().ifEmpty { "لاعب" }
    return stepsOf({ name(it) }, { team -> (0 until 4).filter { com.teera.core.teamOf(it) == team }.joinToString("/") { name(it) } }, events)
}

private fun stepsOf(name: (Int) -> String, teamName: (Int) -> String, events: List<GameEvent>): List<Step> {
    return events.mapNotNull { event ->
        when (event) {
            is GameEvent.Dealt -> Step("توزّعت البولات — الفتحة على ${name(event.opener)}")
            is GameEvent.Redealt -> Step("انعادت القسمة")
            is GameEvent.Opened -> Step("${name(event.seat)} فتح", event.domino, event.seat)
            is GameEvent.Played -> Step("${name(event.seat)} لعب", event.domino, event.seat)
            is GameEvent.Stuck -> Step("${name(event.seat)} گلى", seat = event.seat)
            is GameEvent.Gave -> Step("${name(event.from)} انطى بول لـ ${name(event.to)}", seat = event.from)
            is GameEvent.PlayedFor ->
                Step("${name(event.rescuer)} لعب بدال ${name(event.stuck)}", event.domino, event.rescuer)
            is GameEvent.TeeraCalled -> Step("${name(event.seat)} طيّر بـ ${event.pips}", seat = event.seat)
            is GameEvent.AlertUpheld -> Step(
                "ناخذ خاوة على ${teamName(event.againstTeam)}" +
                    if (event.penalty > 0) " (+${event.penalty})" else "",
            )
            is GameEvent.GameEnded -> Step(
                when (event.result.end) {
                    GameEnd.X -> "${event.result.winnerSeat?.let { name(it) } ?: "لاعب"} خلّص وأخذ اكس"
                    GameEnd.TEERA -> "خلص الگيم بطيرة"
                    GameEnd.BLOCKED -> "قفلت"
                },
            )
            is GameEvent.MatchEnded -> Step("خلصت المباراة")
        }
    }
}

/** The review itself: numbered lines, a little bone on each move. */
@Composable
fun StepsDialog(view: PlayerView, events: List<GameEvent>, onClose: () -> Unit) =
    StepsDialog(stepsOf(view, events), view.yourTeam, onClose)

/** The steps of a game from the books, seen from [yourTeam]'s side. */
@Composable
fun StepsDialog(steps: List<Step>, yourTeam: Int, onClose: () -> Unit, title: String = "خطوات الگيم") {
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text(title, color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
            if (steps.isEmpty()) {
                Text(
                    "ما لحقنا نسجّل خطوات هذا الگيم",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("steps"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(steps) { index, step -> StepRow(index + 1, step, yourTeam) }
            }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("رجوع", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: Step, yourTeam: Int) {
    val ours = step.seat != null && com.teera.core.teamOf(step.seat) == yourTeam
    val tint = when {
        step.seat == null -> Felt.Muted
        ours -> Felt.Playable
        else -> Felt.Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(tint.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Felt.Parchment, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            step.text,
            color = if (step.seat == null) Felt.Muted else Felt.Parchment,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        step.domino?.let {
            Spacer(Modifier.width(6.dp))
            DominoTile(it, Modifier.size(36.dp, 18.dp).padding(0.dp), vertical = false)
        }
    }
}
