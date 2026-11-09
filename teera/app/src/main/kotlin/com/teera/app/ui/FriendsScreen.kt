package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teera.app.Friend
import com.teera.core.ServerMessage
import androidx.compose.foundation.clickable
import com.teera.app.ui.theme.Felt

/**
 * "جماعتي": the people this player plays with. Everybody met at a table is added on
 * their own; anyone else by the six-letter code on their profile. A green dot means
 * their app is open, which is when an invitation can reach them.
 */
@Composable
fun FriendsScreen(
    myCode: String,
    friends: List<Friend>,
    online: Set<String>,
    onAdd: (String) -> Unit,
    onForget: (Friend) -> Unit,
    onShareCode: () -> Unit,
    onClose: () -> Unit,
    /** People this player blocked, and taking a block off. */
    blocked: Map<String, String> = emptyMap(),
    onUnblock: (String) -> Unit = {},
) {
    var code by remember { mutableStateOf("") }
    WalnutRoom {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
            }
            Text(
                "جماعتي",
                color = Felt.Brass,
                fontSize = 44.sp,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // This player's own code, to hand to a friend.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x99120B05))
                    .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("رمزك", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
                    Text(myCode, color = Felt.GoldLit, style = MaterialTheme.typography.headlineSmall)
                }
                BrassButton("دزّه", onShareCode)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = { Text("ضيف واحد برمزه") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Felt.Brass,
                        unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
                        focusedLabelColor = Felt.Brass,
                        unfocusedLabelColor = Felt.Muted,
                        focusedTextColor = Felt.Parchment,
                        unfocusedTextColor = Felt.Parchment,
                        cursorColor = Felt.Brass,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                BrassButton("ضيف", { onAdd(code); code = "" }, enabled = code.length == 6)
            }
            Text(
                "واللي تلعب وياهم بالإنترنت ينضافون لحالهم",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            if (friends.isEmpty()) {
                Text(
                    "لسه ماكو أحد. العب ويا جماعتك أو ضيفهم برمزهم.",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                if (blocked.isNotEmpty()) {
                    item(key = "blocked-title") {
                        Text("المحظورين", color = Felt.Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(blocked.entries.toList(), key = { "blocked-" + it.key }) { (id, name) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = Felt.Muted, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                "شيل الحظر",
                                color = Felt.Brass,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onUnblock(id) }.padding(8.dp)
                                    .testTag("unblock-$id"),
                            )
                        }
                    }
                }
                items(friends.sortedByDescending { it.id in online }, key = { it.id }) { friend ->
                    FriendRow(friend, friend.id in online) {
                        IconButton(onClick = { onForget(friend) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "شيله", tint = Felt.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** One person: picture with a green dot when online, name, and whatever action fits. */
@Composable
fun FriendRow(friend: Friend, online: Boolean, action: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x66120B05))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, Felt.Brass, CircleShape)) {
                Avatar(friend.avatar.takeIf { it != "photo" }.orEmpty(), friend.name, 44.dp)
            }
            if (online) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Felt.Playable)
                        .border(2.dp, Felt.Rail, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.name, color = Felt.Parchment, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                if (online) "فاتح التطبيق" else "مو موجود هسه",
                color = if (online) Felt.Playable else Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        action()
    }
}

/**
 * Sharing a room: WhatsApp first, and below it the friends who have the app open right
 * now, each a tap away from an invitation that pops up on their phone.
 */
@Composable
fun InviteDialog(
    friends: List<Friend>,
    online: Set<String>,
    seated: Set<String>,
    onInvite: (Friend) -> Unit,
    onShareLink: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("دزّ اللنك", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
            GoldButton("دزّه بالواتساب", { onClose(); onShareLink() })
            val here = friends.filter { it.id in online && it.id !in seated }
            if (here.isNotEmpty()) {
                Text("أو اعزم اللي فاتحين اللعبة هسه:", color = Felt.Muted, style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    here.take(8).forEach { friend ->
                        FriendRow(friend, online = true) { BrassButton("اعزم", { onInvite(friend) }) }
                    }
                }
            }
            Text(
                "سكّر",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp),
            )
        }
    }
}

/** An invitation arriving: who, and a button straight to their table. */
@Composable
fun InvitedDialog(invite: ServerMessage.Invited, onAccept: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        Plaque {
            Box(Modifier.size(72.dp).clip(CircleShape).border(2.dp, Felt.Brass, CircleShape)) {
                Avatar(invite.fromAvatar.takeIf { it != "photo" }.orEmpty(), invite.fromName, 72.dp)
            }
            Text("${invite.fromName} يعزمك على طاولته", color = Felt.Parchment, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            GoldButton("ادخل", onAccept)
            Text(
                "بعدين",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLater).padding(8.dp),
            )
        }
    }
}

/**
 * A newer app is out. The button downloads it straight away; the phone then offers to
 * install it over this one, keeping everything (same key, same name).
 */
@Composable
fun UpdateDialog(update: ServerMessage.UpdateAvailable, onDownload: () -> Unit, onLater: () -> Unit) {
    Dialog(
        onDismissRequest = { if (!update.required) onLater() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = !update.required,
            dismissOnClickOutside = !update.required,
        ),
    ) {
        Plaque {
            Text("نسخة جديدة!", color = Felt.Brass, style = MaterialTheme.typography.displaySmall)
            Text(
                "طيرة ${update.versionName} نزلت" +
                    if (update.required) ". لازم تحدّث حتى تكمل تلعب ويا جماعتك." else ". حدّث حتى تلعب ويا جماعتك بدون مشاكل.",
                color = Felt.Parchment,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                "دوس «نزّلها هسه»، ومن يخلص التنزيل افتح الملف ودوس تثبيت. بياناتك تبقى.",
                color = Felt.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            GoldButton("نزّلها هسه", onDownload)
            if (!update.required) {
                Text(
                    "بعدين",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLater).padding(8.dp)
                        .testTag("update-later"),
                )
            }
        }
    }
}
