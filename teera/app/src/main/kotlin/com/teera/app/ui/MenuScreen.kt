package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino

@Composable
fun MenuScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: (String) -> Unit,
    onSolo: () -> Unit,
    connecting: Boolean,
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Felt.TableLight, Felt.Deep)))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DominoTile(Domino(6, 6), Modifier.size(44.dp, 84.dp))
            DominoTile(Domino(0, 1), Modifier.size(44.dp, 84.dp))
            DominoTile(null, Modifier.size(44.dp, 84.dp), faceUp = false)
        }

        Spacer(Modifier.height(18.dp))
        Text("طيرة", style = MaterialTheme.typography.displaySmall, color = Felt.Brass)
        Text(
            "دومينا بأربعة، فريقين، وبقوانينكم",
            style = MaterialTheme.typography.bodyMedium,
            color = Felt.IvoryShade,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = playerName,
            onValueChange = { onNameChange(it.take(16)) },
            label = { Text("اسمك") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onHost,
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
        ) {
            Text(
                if (connecting) "يحاول يتصل…" else "افتح غرفة",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(4) },
                label = { Text("كود الغرفة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { onJoin(code) },
                enabled = code.length == 4 && !connecting,
                modifier = Modifier.height(56.dp),
            ) { Text("ادخل") }
        }

        Spacer(Modifier.height(22.dp))

        OutlinedButton(onClick = onSolo, modifier = Modifier.fillMaxWidth()) {
            Text("العب ضد البوتات")
        }

        Spacer(Modifier.weight(1f))
        // Printed so a build can be told apart from an older one on the same phone.
        Text(
            "نسخة ${com.teera.app.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = Felt.Muted,
        )
    }
}
