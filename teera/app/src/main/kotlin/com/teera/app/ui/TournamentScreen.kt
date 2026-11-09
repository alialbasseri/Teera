package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.window.Dialog
import com.teera.app.ui.theme.Felt
import com.teera.core.TMatch
import com.teera.core.Tournament

/**
 * A tournament's bracket, round by round. A match with both teams known can be played
 * (it opens a room with the teams' names on the chairs); its winner goes through when
 * the match ends there, or when the organiser says who won.
 */
@Composable
fun TournamentScreen(
    tournament: Tournament?,
    myId: String,
    onPlay: (round: Int, index: Int) -> Unit,
    onWinner: (round: Int, index: Int, team: Int) -> Unit,
    onShare: (Tournament) -> Unit,
    onClose: () -> Unit,
) {
    var settling by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    WalnutRoom {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
                Spacer(Modifier.weight(1f))
                if (tournament != null) BrassButton("دزّ الكود", { onShare(tournament) })
            }
            if (tournament == null) {
                Text(
                    "دا نجيب البطولة…",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                )
                return@Column
            }
            Text(
                tournament.name,
                color = Felt.Brass,
                fontSize = 38.sp,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "الكود ${tournament.id} · ${tournament.teams.size} فرق",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            tournament.champion?.let { champ ->
                Text(
                    "🏆 البطل: ${tournament.teams[champ]}",
                    color = Felt.GoldLit,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            val organiser = tournament.creatorId == myId
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("bracket")) {
                tournament.rounds.forEachIndexed { round, matches ->
                    item(key = "round-$round") {
                        Text(
                            tournament.roundName(round),
                            color = Felt.Brass,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    itemsIndexed(matches, key = { index, _ -> "m-$round-$index" }) { index, match ->
                        MatchCard(
                            tournament, match,
                            onPlay = { onPlay(round, index) },
                            onSettle = if (organiser) ({ settling = round to index }) else null,
                            tag = "match-$round-$index",
                        )
                    }
                }
            }
        }
    }
    settling?.let { (round, index) ->
        val match = tournament?.rounds?.getOrNull(round)?.getOrNull(index)
        if (tournament != null && match?.a != null && match.b != null) {
            Dialog(onDismissRequest = { settling = null }) {
                Plaque {
                    Text("منو فاز؟", color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
                    listOf(match.a!!, match.b!!).forEach { team ->
                        GoldButton(tournament.teams[team], { onWinner(round, index, team); settling = null }, Modifier.testTag("winner-$team"))
                    }
                }
            }
        } else {
            settling = null
        }
    }
}

@Composable
private fun MatchCard(tournament: Tournament, match: TMatch, onPlay: () -> Unit, onSettle: (() -> Unit)?, tag: String) {
    fun team(i: Int?) = i?.let { tournament.teams.getOrNull(it) } ?: "؟"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x99120B05))
            .border(1.dp, Felt.Brass.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag(tag),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Side(team(match.a), won = match.winner != null && match.winner == match.a, Modifier.weight(1f))
            Text("ضد", color = Felt.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
            Side(team(match.b), won = match.winner != null && match.winner == match.b, Modifier.weight(1f))
        }
        val ready = match.a != null && match.b != null && match.winner == null
        if (ready) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoldButton(if (match.roomCode != null) "ادخل الغرفة ${match.roomCode}" else "العبوها", onPlay, Modifier.weight(1f).testTag("$tag-play"))
                if (onSettle != null) BrassButton("منو فاز؟", onSettle, modifier = Modifier.testTag("$tag-settle"))
            }
        }
    }
}

@Composable
private fun Side(name: String, won: Boolean, modifier: Modifier) {
    Text(
        (if (won) "🏆 " else "") + name,
        color = if (won) Felt.GoldLit else Felt.Parchment,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Making a tournament: its name, how many teams, and each team's name. */
@Composable
fun CreateTournamentDialog(onCreate: (String, List<String>) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableIntStateOf(4) }
    val teams = remember { mutableStateListOf(*Array(16) { "" }) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Felt.Brass,
        unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
        focusedLabelColor = Felt.Brass,
        unfocusedLabelColor = Felt.Muted,
        focusedTextColor = Felt.Parchment,
        unfocusedTextColor = Felt.Parchment,
        cursorColor = Felt.Brass,
    )
    Dialog(onDismissRequest = onClose) {
        Plaque {
          // The whole form scrolls, so the button stays reachable on a small phone.
          Column(
              Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("بطولة جديدة", color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, label = { Text("اسم البطولة") },
                singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth().testTag("tournament-name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tournament.SIZES.forEach { n ->
                    val chosen = n == size
                    Text(
                        "$n فرق",
                        color = if (chosen) Felt.Ink else Felt.Parchment,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) Felt.Brass else Color(0x99120B05))
                            .clickable { size = n }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("size-$n"),
                    )
                }
            }
            Text("اكتب كل فريق (مثلاً: علي وعبود). الترتيب يحدد منو يلعب ويا منو.", color = Felt.Muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            (0 until size).forEach { i ->
                OutlinedTextField(
                    value = teams[i], onValueChange = { teams[i] = it.take(30) },
                    label = { Text("الفريق ${i + 1}") }, singleLine = true, colors = colors,
                    modifier = Modifier.fillMaxWidth().testTag("team-$i"),
                )
            }
            val ready = name.isNotBlank() && (0 until size).all { teams[it].isNotBlank() }
            GoldButton("سوّيها", { onCreate(name, (0 until size).map { teams[it].trim() }) }, Modifier.testTag("tournament-create"), enabled = ready)
          }
        }
    }
}
