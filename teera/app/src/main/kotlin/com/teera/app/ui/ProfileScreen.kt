package com.teera.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt

/**
 * Who you are at the table: your name, and the picture your friends see on your seat -
 * one of the drawn ones, or a photo of your own.
 */
@Composable
fun ProfileScreen(
    name: String,
    avatar: String,
    photo: ImageBitmap?,
    onName: (String) -> Unit,
    onPreset: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onClose: () -> Unit,
    myCode: String = "",
    record: com.teera.core.PlayerRecord? = null,
    recordLoading: Boolean = false,
    recordKept: Boolean = true,
    onOpenMatch: (String, Int) -> Unit = { _, _ -> },
    onRanking: () -> Unit = {},
) {
    WalnutRoom {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("بروفايلك", color = Felt.Brass, fontSize = 44.sp, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.size(128.dp).clip(CircleShape).border(2.dp, Felt.Brass, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Avatar(avatar, name, 128.dp, photo) }
                if (myCode.isNotEmpty()) {
                    Text("رمزك: $myCode", color = Felt.Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { onName(it.take(16)) },
                    label = { Text("اسمك") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Felt.Brass,
                        unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
                        focusedLabelColor = Felt.Brass,
                        unfocusedLabelColor = Felt.Muted,
                        focusedTextColor = Felt.Parchment,
                        unfocusedTextColor = Felt.Parchment,
                        cursorColor = Felt.Brass,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                RecordCard(record, recordLoading, recordKept, onOpenMatch = onOpenMatch, onRanking = onRanking)
                Spacer(Modifier.height(18.dp))
                Text("اختار صورتك", color = Felt.Brass, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PRESET_AVATARS.indices.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        row.forEach { index ->
                            val chosen = avatar == "p:$index"
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(66.dp)
                                        .clip(CircleShape)
                                        .border(if (chosen) 3.dp else 1.dp, if (chosen) Felt.GoldLit else Felt.Brass.copy(alpha = 0.5f), CircleShape)
                                        .clickable { onPreset(index) }
                                        .testTag("avatar-$index"),
                                ) { PresetAvatar(index, Modifier.size(66.dp)) }
                                Text(
                                    PRESET_AVATARS[index],
                                    color = if (chosen) Felt.GoldLit else Felt.Muted,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                BrassButton(
                    if (avatar == "photo") "غيّر صورتك من الاستوديو" else "حط صورتك من الاستوديو",
                    onPickPhoto,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "صورتك تطلع لجماعتك على كرسيك بالطاولة",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))
                GoldButton("تمام", onClose)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
