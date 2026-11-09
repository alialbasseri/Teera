package com.teera.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.teera.app.ui.theme.Felt
import com.teera.core.MatchSummary
import com.teera.core.PlayerRecord
import java.util.Calendar

/**
 * "سجلّي": what the server's books say about this player — matches won and lost, games,
 * and their latest matches with who they played with and against.
 */
@Composable
fun RecordCard(
    record: PlayerRecord?,
    loading: Boolean,
    kept: Boolean,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onOpenMatch: (String, Int) -> Unit = { _, _ -> },
    onRanking: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("record"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("سجلّي", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
        when {
            record != null -> Filled(record, now, onOpenMatch, onRanking)
            loading -> Note("دا نجيب سجلّك من السيرفر…")
            !kept -> Note("السيرفر بعد ما يحفظ الگيمات")
            else -> Note("سجلّك يبين هنا من تكون متصل بالنت")
        }
    }
}

@Composable
private fun Note(text: String) =
    Text(text, color = Felt.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Filled(record: PlayerRecord, now: Long, onOpenMatch: (String, Int) -> Unit, onRanking: () -> Unit) {
    // Their place in the ranking, which opens it.
    Text(
        if (record.rank != null) "المركز ${record.rank} · ${record.rating} نقطة" else "الترتيب",
        color = Felt.GoldLit,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onRanking)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("my-rank"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("${record.matches}", "مباريات")
        Stat("${record.wins}", "فزت", Felt.Playable)
        Stat("${record.losses}", "خسرت", Felt.Danger)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("${record.games}", "گيمات")
        Stat("${record.finishes}", "خلّصت")
        Stat("${record.teeras}", "طيّرت")
    }
    // Titles held, then badges earned.
    if (record.titles.isNotEmpty() || record.badges.isNotEmpty()) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().testTag("badges"),
        ) {
            record.titles.forEach { Badge("👑 $it", Felt.GoldLit) }
            record.badges.forEach { Badge(it, Felt.Parchment) }
        }
    }
    if (record.recent.isEmpty()) {
        Note("بعدك ما لعبت أونلاين. أول مباراة تلعبها تنحفظ هنا")
        return
    }
    HorizontalDivider(color = Felt.Brass.copy(alpha = 0.3f))
    Text("آخر المباريات", color = Felt.Brass, style = MaterialTheme.typography.titleSmall)
    record.recent.forEach { match -> MatchLine(match, now) { onOpenMatch(match.id, match.seat) } }
}

@Composable
private fun Stat(value: String, label: String, colour: Color = Felt.Parchment) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = colour, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MatchLine(match: MatchSummary, now: Long, onOpen: () -> Unit) {
    val (verdict, colour) = when (match.won) {
        true -> "فزتوا" to Felt.Playable
        false -> "خسرتوا" to Felt.Danger
        null -> "ما خلصت" to Felt.Muted
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = match.id.isNotBlank(), onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "ويا ${match.partner.ifBlank { "؟" }} ضد ${match.opponents.joinToString(" و")}",
                color = Felt.Parchment,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${whenPlayed(match.startedAtEpochMs, now)} · ${match.games} گيم",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(verdict, color = colour, style = MaterialTheme.typography.labelLarge)
            Text(
                "نحن ${number(match.ourScore)} · هم ${number(match.theirScore)}",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** A score kept left to right, so a minus stays in front of its number in Arabic text. */
private fun number(value: Int) = "\u2066$value\u2069"

/** "اليوم", "البارحة", or the date. */
internal fun whenPlayed(at: Long, now: Long): String {
    fun day(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let {
        it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR)
    }
    val calendar = Calendar.getInstance().apply { timeInMillis = at }
    return when (day(now) - day(at)) {
        0 -> "اليوم"
        1 -> "البارحة"
        else -> "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}"
    }
}

@Composable
private fun Badge(text: String, colour: Color) {
    Text(
        text,
        color = colour,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Felt.Brass.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
