package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt
import com.teera.core.LiveGame

/**
 * Games being played right now that one of their players let anyone watch: who is
 * against whom, the score, and a button to go and watch.
 */
@Composable
fun LiveGamesScreen(
    games: List<LiveGame>,
    onWatch: (String) -> Unit,
    onClose: () -> Unit,
    /** Asks to come into a room that is not open to all. */
    onAsk: (LiveGame) -> Unit = {},
    /** Rooms already asked into, by ref, waiting on an answer. */
    asked: Set<String> = emptySet(),
) {
    WalnutRoom {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
            }
            Text(
                "الطاولات",
                color = Felt.Brass,
                fontSize = 40.sp,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "ادخل العب وياهم أو تفرّج. المفتوحة تدخلها، والباقي تطلب وهمّه يقبلون",
                color = Felt.Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
            )
            if (games.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "ماكو طاولة هسه",
                        color = Felt.Parchment,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "افتح غرفة وخلي جماعتك يدخلون، وتطلع هنا للكل",
                        color = Felt.Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("live-games")) {
                items(games, key = { it.ref.ifEmpty { it.code } }) { game ->
                    LiveGameCard(game, onWatch, onAsk, waiting = game.ref.isNotEmpty() && game.ref in asked)
                }
            }
        }
    }
}

@Composable
private fun LiveGameCard(game: LiveGame, onWatch: (String) -> Unit, onAsk: (LiveGame) -> Unit, waiting: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x99120B05))
            .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        if (game.name.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.name,
                    color = Felt.Brass,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when {
                        !game.started && game.freeChairs > 0 -> "ناقصهم ${game.freeChairs}"
                        !game.started -> "بالغرفة"
                        else -> "يلعبون"
                    } + if (game.open) "" else " · 🔒",
                    color = if (!game.started && game.freeChairs > 0) Felt.Playable else Felt.Muted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Team(game, listOf(0, 2), game.scores.getOrElse(0) { 0 }, Modifier.weight(1f))
            Text("ضد", color = Felt.Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
            Team(game, listOf(1, 3), game.scores.getOrElse(1) { 0 }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (game.started) "گيم ${game.gameNumber} · " else "") +
                    (if (game.games > 0) "${game.games} گيمات" else "لـ ${game.target}") +
                    if (game.watchers > 0) " · ${game.watchers} يتفرجون" else "",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            val label = when {
                waiting -> "ننتظر جوابهم…"
                !game.open -> "اطلب تدخل"
                !game.started && game.freeChairs > 0 -> "ادخل العب"
                else -> "تفرّج"
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (waiting) Felt.Brass.copy(alpha = 0.4f) else Felt.Brass)
                    .clickable(enabled = !waiting) { if (game.open) onWatch(game.code) else onAsk(game) }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("watch-${game.code.ifEmpty { game.ref }}"),
            ) {
                Text(label, color = Felt.Ink, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Two partners and their score. */
@Composable
private fun Team(game: LiveGame, seats: List<Int>, score: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            seats.forEach { seat ->
                Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, Felt.Brass, CircleShape)) {
                    Avatar(game.avatars.getOrElse(seat) { "" }.takeIf { it != "photo" }.orEmpty(), game.names.getOrElse(seat) { "" }, 34.dp)
                }
            }
        }
        Text(
            seats.joinToString(" و") { game.names.getOrElse(it) { "" }.ifBlank { "لاعب" } },
            color = Felt.Parchment,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        // Kept left to right, so a minus stays in front of its number in Arabic text.
        Text("\u2066$score\u2069", color = Felt.GoldLit, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}
