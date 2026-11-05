package com.teera.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino
import kotlinx.coroutines.delay

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

    WalnutRoom {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Emblem()
            Spacer(Modifier.height(6.dp))
            Text(
                "طيرة",
                color = Felt.Brass,
                fontSize = 64.sp,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                "دومينا عراقية · أربعة · فريقين",
                color = Felt.Muted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            RosetteRule(Modifier.padding(horizontal = 30.dp))
            Spacer(Modifier.height(22.dp))

            OutlinedTextField(
                value = playerName,
                onValueChange = { onNameChange(it.take(16)) },
                label = { Text("اسمك") },
                singleLine = true,
                colors = brassField(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            GoldButton("العب ضد البوتات", onSolo)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(4) },
                    label = { Text("كود الغرفة") },
                    singleLine = true,
                    colors = brassField(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Go,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                BrassButton("ادخل", { onJoin(code) }, enabled = code.length == 4 && !connecting)
            }
            Spacer(Modifier.height(12.dp))
            BrassButton(
                if (connecting) "يحاول يتصل…" else "افتح غرفة لأصحابك",
                onHost,
                modifier = Modifier.fillMaxWidth(),
                enabled = !connecting,
            )

            Spacer(Modifier.weight(1f))
            Text(
                "نسخة ${com.teera.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = Felt.Muted,
            )
        }
    }
}

@Composable
private fun brassField() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Felt.Brass,
    unfocusedBorderColor = Felt.Brass.copy(alpha = 0.5f),
    focusedLabelColor = Felt.Brass,
    unfocusedLabelColor = Felt.Muted,
    focusedTextColor = Felt.Parchment,
    unfocusedTextColor = Felt.Parchment,
    cursorColor = Felt.Brass,
)

/**
 * The game's mark: the six-six that opens a match and the zero-one that flies it, fanned
 * under an Ishtar rosette. It settles into place when the app opens.
 */
@Composable
private fun Emblem() {
    val settle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        settle.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val fan = settle.value

    Box(Modifier.size(200.dp, 150.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.size(64.dp).align(Alignment.TopCenter)) {
            drawRosette(Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.46f, alpha = fan)
        }
        DominoTile(
            null,
            Modifier.size(46.dp, 92.dp).offset(x = (-44 * fan).dp, y = (6 * fan).dp).rotate(-16f * fan),
            faceUp = false,
        )
        DominoTile(
            Domino(6, 6),
            Modifier.size(46.dp, 92.dp).offset(x = (44 * fan).dp, y = (6 * fan).dp).rotate(16f * fan),
        )
        DominoTile(Domino(0, 1), Modifier.size(48.dp, 96.dp).scale(0.9f + 0.1f * fan))
    }
}
