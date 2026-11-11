package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.teera.app.ui.theme.Felt

/**
 * Tell the developer about a problem, or suggest something: straight to the admin page,
 * with a picture if it helps: the screen as it was when this opened, or one from the phone.
 */
@Composable
fun FeedbackDialog(onSend: (kind: String, text: String, image: com.teera.app.Picture?) -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf("problem") }
    var text by remember { mutableStateOf("") }
    // The screen behind, taken now, before this window covers it.
    val screen = com.teera.app.rememberScreenshot()
    var image by remember { mutableStateOf<com.teera.app.Picture?>(null) }
    val pick = com.teera.app.rememberPicturePicker { picked -> image = picked }
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("بلّغ أو اقترح", color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("problem" to "مشكلة", "idea" to "اقتراح").forEach { (key, label) ->
                    val on = kind == key
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Felt.Brass else Color(0xFF120B05).copy(alpha = 0.55f))
                            .border(1.dp, Felt.Brass.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .clickable { kind = key }
                            .padding(vertical = 8.dp)
                            .testTag("feedback-$key"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (on) Felt.Ink else Felt.Parchment, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(1_500) },
                placeholder = {
                    Text(
                        if (kind == "problem") "شنو صار؟ وين؟ شلون نعيدها؟" else "شنو تتمنى نضيف أو نغيّر؟",
                        color = Felt.Muted,
                    )
                },
                minLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Felt.Parchment,
                    unfocusedTextColor = Felt.Parchment,
                    focusedBorderColor = Felt.Brass,
                    unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
                    cursorColor = Felt.Brass,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("feedback-text"),
            )
            // A picture: the screen behind, or one from the phone.
            val shown = image
            if (shown != null) {
                Box(contentAlignment = Alignment.TopStart) {
                    androidx.compose.foundation.Image(
                        com.teera.app.Pictures.show(shown),
                        contentDescription = "الصورة",
                        modifier = Modifier.heightIn(max = 140.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                    )
                    Text(
                        "✕",
                        color = Felt.Parchment,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC120B05))
                            .clickable { image = null }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .testTag("feedback-image-remove"),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (screen != null) {
                        SmallChoice("📸 صورة الشاشة", Modifier.weight(1f).testTag("feedback-screen")) { image = screen }
                    }
                    SmallChoice("🖼️ من الصور", Modifier.weight(1f).testTag("feedback-gallery")) { pick() }
                }
            }
            Text(
                "يوصل لصاحب اللعبة ويا اسمك ونسختك.",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            GoldButton("دز", {
                if (text.trim().length >= 2) {
                    onSend(kind, text, image)
                    onClose()
                }
            })
            Text(
                "سد",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp),
            )
        }
    }
}

/** Words from the developer to everyone with the app open. */
@Composable
fun AnnouncementDialog(text: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("📣 من صاحب اللعبة", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
            Text(text, color = Felt.Parchment, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            GoldButton("تمام", onClose)
        }
    }
}

/** Somebody from the list asks to come in: anyone at the table may let them. */
@Composable
fun JoinAskDialog(ask: com.teera.core.ServerMessage.JoinAsked, onYes: () -> Unit, onNo: () -> Unit) {
    Dialog(onDismissRequest = onNo) {
        Plaque {
            Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).border(2.dp, Felt.Brass, androidx.compose.foundation.shape.CircleShape)) {
                Avatar(ask.avatar.takeIf { it != "photo" }.orEmpty(), ask.name, 64.dp)
            }
            Text("${ask.name} يريد يدخل لطاولتكم", color = Felt.Parchment, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("يدخل يتفرّج، وإذا أكو كرسي فارغ يگدر يگعد.", color = Felt.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            GoldButton("خليه يدخل", onYes)
            Text(
                "لا",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNo).padding(8.dp).testTag("join-no"),
            )
        }
    }
}

/** The host names the room: what it is called in the list of tables. */
@Composable
fun RenameDialog(current: String, onRename: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("اسم الطاولة", color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(com.teera.core.Room.MAX_NAME) },
                singleLine = true,
                placeholder = { Text("مثلاً: گعدة الجمعة", color = Felt.Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Felt.Parchment,
                    unfocusedTextColor = Felt.Parchment,
                    focusedBorderColor = Felt.Brass,
                    unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
                    cursorColor = Felt.Brass,
                ),
                modifier = Modifier.fillMaxWidth().testTag("rename-text"),
            )
            GoldButton("احفظ", { onRename(text); onClose() })
            Text(
                "سد",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp),
            )
        }
    }
}

@Composable
private fun SmallChoice(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF120B05).copy(alpha = 0.55f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Felt.Parchment, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
