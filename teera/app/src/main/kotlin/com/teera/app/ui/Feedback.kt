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

/** Tell the developer about a problem, or suggest something: straight to the admin page. */
@Composable
fun FeedbackDialog(onSend: (kind: String, text: String) -> Unit, onClose: () -> Unit) {
    var kind by remember { mutableStateOf("problem") }
    var text by remember { mutableStateOf("") }
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
            Text(
                "يوصل لصاحب اللعبة ويا اسمك ونسختك.",
                color = Felt.Muted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            GoldButton("دز", {
                if (text.trim().length >= 2) {
                    onSend(kind, text)
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
