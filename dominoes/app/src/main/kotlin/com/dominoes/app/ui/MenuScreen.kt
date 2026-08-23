package com.dominoes.app.ui

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
import androidx.compose.material3.TextButton
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
import com.dominoes.app.ui.theme.Felt
import com.dominoes.core.Domino

@Composable
fun MenuScreen(
    playerName: String,
    onNameChange: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: (String) -> Unit,
    onSolo: () -> Unit,
    onRules: () -> Unit,
    connecting: Boolean,
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Felt.TableLight, Felt.Deep)))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DominoTile(Domino(6, 6), Modifier.size(46.dp, 88.dp))
            DominoTile(Domino(3, 5), Modifier.size(46.dp, 88.dp))
            DominoTile(null, Modifier.size(46.dp, 88.dp), faceUp = false)
        }

        Spacer(Modifier.height(20.dp))
        Text("DOMINOES", style = MaterialTheme.typography.displaySmall, color = Felt.Brass)
        Text(
            "Play with your friends, by your own rules",
            style = MaterialTheme.typography.bodyMedium,
            color = Felt.IvoryShade,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = playerName,
            onValueChange = { onNameChange(it.take(16)) },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onHost,
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
        ) {
            Text(if (connecting) "Connecting…" else "Create a room", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(4) },
                label = { Text("Room code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { onJoin(code) },
                enabled = code.length == 4 && !connecting,
                modifier = Modifier.height(56.dp),
            ) {
                Text("Join")
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onSolo, modifier = Modifier.fillMaxWidth()) {
            Text("Play offline against bots")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onRules) {
            Text("House rules", color = Felt.Brass)
        }
    }
}
