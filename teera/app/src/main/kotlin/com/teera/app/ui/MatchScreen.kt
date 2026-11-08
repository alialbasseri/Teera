package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt
import com.teera.core.GameEnd
import com.teera.core.MatchDetail
import com.teera.core.PlayedGame
import com.teera.core.teamOf

/**
 * A match from the books: who played, how it ended, and every game in it. Pressing a
 * game opens its steps, as the review after a game does.
 */
@Composable
fun MatchScreen(match: MatchDetail?, loading: Boolean, mySeat: Int, onClose: () -> Unit) {
    var opened by remember { mutableStateOf<PlayedGame?>(null) }
    val myTeam = teamOf(mySeat)
    WalnutRoom {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
            }
            if (match == null) {
                Text(
                    if (loading) "دا نجيب المباراة…" else "ما لگيت هالمباراة",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                )
                return@Column
            }
            fun team(t: Int) = (0 until 4).filter { teamOf(it) == t }.joinToString(" و") { match.names.getOrElse(it) { "" } }
            val verdict = when (match.losingTeam) {
                null -> "ما خلصت"
                myTeam -> "خسرتوا"
                else -> "فزتوا"
            }
            Text(
                verdict,
                color = when (match.losingTeam) { null -> Felt.Muted; myTeam -> Felt.Danger; else -> Felt.Playable },
                fontSize = 40.sp,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Side(team(myTeam), match.scores.getOrElse(myTeam) { 0 }, Modifier.weight(1f))
                Text("ضد", color = Felt.Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                Side(team(1 - myTeam), match.scores.getOrElse(1 - myTeam) { 0 }, Modifier.weight(1f))
            }
            Text(
                "${whenPlayed(match.startedAtEpochMs, System.currentTimeMillis())} · لـ ${match.target} · ${match.games.size} گيم · دوس على أي گيم تشوف خطواته",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("match-games")) {
                items(match.games, key = { it.gameNumber }) { game -> GameLine(game, match.names, myTeam) { opened = game } }
            }
        }
    }
    opened?.let { game ->
        StepsDialog(stepsOf(match!!.names, game.steps), myTeam, onClose = { opened = null }, title = "گيم ${game.gameNumber}")
    }
}

@Composable
private fun Side(names: String, score: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(names, color = Felt.Parchment, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text("⁦$score⁩", color = Felt.GoldLit, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
}

@Composable
private fun GameLine(game: PlayedGame, names: List<String>, myTeam: Int, onOpen: () -> Unit) {
    val who = game.winnerSeat?.let { names.getOrNull(it) }.orEmpty()
    val how = when (game.end) {
        GameEnd.X -> "$who خلّص (اكس)"
        GameEnd.TEERA -> "$who طيّر"
        GameEnd.BLOCKED -> "قفلت"
    }
    val ours = game.winnerSeat?.let { teamOf(it) == myTeam }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x99120B05))
            .border(1.dp, Felt.Brass.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("game-${game.gameNumber}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${game.gameNumber}", color = Felt.Brass, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
        Text(
            how,
            color = when (ours) { true -> Felt.Playable; false -> Felt.Danger; null -> Felt.Muted },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "⁦${game.scores.getOrElse(myTeam) { 0 }}⁩ / ⁦${game.scores.getOrElse(1 - myTeam) { 0 }}⁩",
            color = Felt.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
