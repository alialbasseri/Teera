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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt
import com.teera.core.Leaderboard

/**
 * The ranking: everybody who has played a match against people, strongest first, and
 * the partnerships that win most. This player's own line stands out.
 */
@Composable
fun RankingScreen(
    board: Leaderboard?,
    loading: Boolean,
    kept: Boolean,
    myId: String,
    onClose: () -> Unit,
) {
    var pairs by rememberSaveable { mutableStateOf(false) }
    WalnutRoom {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
            }
            Text(
                "الترتيب",
                color = Felt.Brass,
                fontSize = 44.sp,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
            ) {
                Tab("اللاعبين", !pairs, "tab-players") { pairs = false }
                Tab("الثنائيات", pairs, "tab-pairs") { pairs = true }
            }

            val empty = when {
                board == null && loading -> "دا نجيب الترتيب…"
                board == null && !kept -> "السيرفر بعد ما يحفظ الگيمات"
                board == null -> "الترتيب يبين من تكون متصل بالنت"
                !pairs && board.players.isEmpty() -> "بعد ماكو مباريات بالترتيب"
                pairs && board.pairs.isEmpty() -> "بعد ماكو ثنائي لعب ويا بعض"
                else -> null
            }
            if (empty != null) {
                Note(empty)
                Note("تنحسب المباراة إذا بكل فريق لاعب واحد على الأقل، مو بوتات بس")
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("ranking")) {
                if (!pairs) {
                    itemsIndexed(board!!.players, key = { _, it -> it.playerId }) { index, player ->
                        Line(
                            place = index + 1,
                            avatars = listOf(player.avatar),
                            names = listOf(player.name),
                            mine = player.playerId == myId,
                            big = "${player.rating}",
                            small = "${player.matches} مباراة · ${player.wins} فوز",
                        )
                    }
                } else {
                    itemsIndexed(board!!.pairs, key = { index, it -> "$index:" + it.names.joinToString() }) { index, pair ->
                        Line(
                            place = index + 1,
                            avatars = pair.avatars,
                            names = pair.names,
                            mine = false,
                            big = "${pair.wins * 100 / pair.matches.coerceAtLeast(1)}٪",
                            small = "${pair.matches} مباراة · ${pair.wins} فوز",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(text: String, chosen: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text,
        color = if (chosen) Felt.Ink else Felt.Parchment,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(if (chosen) Felt.Brass else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .testTag(tag),
    )
}

@Composable
private fun Note(text: String) = Text(
    text,
    color = Felt.Muted,
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
)

/** One place in the ranking: its number (gold, silver, bronze for the first three). */
@Composable
private fun Line(place: Int, avatars: List<String>, names: List<String>, mine: Boolean, big: String, small: String) {
    val medal = when (place) {
        1 -> Felt.GoldLit
        2 -> Color(0xFFC9CED6)
        3 -> Color(0xFFCD8B4E)
        else -> Felt.Muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (mine) Felt.Brass.copy(alpha = 0.22f) else Color(0x99120B05))
            .border(1.dp, if (mine) Felt.Brass else Felt.Brass.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$place", color = medal, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            avatars.forEachIndexed { index, avatar ->
                Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, Felt.Brass, CircleShape)) {
                    Avatar(avatar, names.getOrElse(index) { "" }, 36.dp)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                names.joinToString(" و"),
                color = Felt.Parchment,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(small, color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        }
        Text(big, color = if (mine) Felt.GoldLit else Felt.Brass, style = MaterialTheme.typography.titleLarge)
    }
}
