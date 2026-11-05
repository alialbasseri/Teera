package com.teera.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.net.WifiTable
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
    onHostWifi: () -> Unit = {},
    wifiTables: List<WifiTable> = emptyList(),
    onJoinWifi: (WifiTable) -> Unit = {},
    onJoinAddress: (String) -> Unit = {},
    hasInternetServer: Boolean = false,
    onSetServer: (String) -> Unit = {},
    /** Knocking on a sleeping server, which takes a while the first time. */
    waking: Boolean = false,
    /** The internet room this phone was last in, if any, to go straight back to. */
    lastRoom: String? = null,
    onRules: () -> Unit = {},
    scroll: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var typingAddress by remember { mutableStateOf(false) }

    WalnutRoom {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp, vertical = 20.dp),
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
            Spacer(Modifier.height(10.dp))
            BrassButton("قوانين اللعبة", onRules, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            RosetteRule(Modifier.padding(horizontal = 50.dp))
            Spacer(Modifier.height(10.dp))

            // Friends anywhere, the usual case: first, because they are rarely in the same house.
            Text("عبر الإنترنت", color = Felt.Brass, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (hasInternetServer) {
                if (lastRoom != null) {
                    GoldButton("ارجع للغرفة $lastRoom", { onJoin(lastRoom) })
                    Spacer(Modifier.height(10.dp))
                }
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
                Spacer(Modifier.height(10.dp))
                BrassButton(
                    when {
                        connecting && waking -> "السيرفر يصحى…"
                        connecting -> "يحاول يتصل…"
                        else -> "افتح غرفة عبر الإنترنت"
                    },
                    onHost,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                )
                if (connecting && waking) {
                    Text(
                        "إذا محد لعب من فترة، السيرفر ينام وياخذ تقريباً دقيقة حتى يصحى",
                        color = Felt.Muted,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                Text(
                    "يحتاج سيرفر على الإنترنت، ولسه ما منصوب",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                var server by remember { mutableStateOf("") }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it.trim() },
                        label = { Text("عنوان السيرفر") },
                        singleLine = true,
                        colors = brassField(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    BrassButton("حفظ", { onSetServer(server) }, enabled = server.contains('.'))
                }
            }

            Spacer(Modifier.height(14.dp))
            RosetteRule(Modifier.padding(horizontal = 50.dp))
            Spacer(Modifier.height(10.dp))

            // Friends in the same room: one phone hosts, the rest find it on the Wi-Fi.
            Text("مع أصحابك على نفس الواي فاي", color = Felt.Brass, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            BrassButton(
                if (connecting) "يحاول يتصل…" else "افتح طاولة",
                onHostWifi,
                modifier = Modifier.fillMaxWidth(),
                enabled = !connecting,
            )
            wifiTables.forEach { table ->
                Spacer(Modifier.height(8.dp))
                GoldButton("ادخل طاولة ${table.name}", { onJoinWifi(table) })
            }
            if (wifiTables.isEmpty()) {
                Text(
                    "الطاولات اللي يفتحها أصحابك تطلع هنا",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                if (typingAddress) "إخفاء" else "ما طلعت الطاولة؟ ادخل بالعنوان",
                color = Felt.Brass,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { typingAddress = !typingAddress }
                    .padding(6.dp),
            )
            if (typingAddress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it.filter { c -> c.isDigit() || c == '.' || c == ':' }.take(21) },
                        label = { Text("العنوان، مثل 192.168.1.5") },
                        singleLine = true,
                        colors = brassField(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    BrassButton("ادخل", { onJoinAddress(address) }, enabled = address.count { it == '.' } == 3)
                }
            }

            Spacer(Modifier.height(26.dp))
            RosetteRule(Modifier.padding(horizontal = 80.dp))
            Spacer(Modifier.height(10.dp))
            Credits()
            Text(
                "نسخة ${com.teera.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = Felt.Muted,
                modifier = Modifier.padding(top = 6.dp),
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

/** Who made the game, each name a link. */
const val DESIGNER_URL = "https://www.linkedin.com/in/ali-albasseri"
const val TECH_VISION_URL = "https://store.techvisi0n.com"

@Composable
private fun Credits() {
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    // English lines read left to right, even inside the Arabic menu.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CreditLine("Designed by", "Ali Albasseri") { runCatching { links.openUri(DESIGNER_URL) } }
            CreditLine("Powered by", "Tech Vision") { runCatching { links.openUri(TECH_VISION_URL) } }
        }
    }
}

@Composable
private fun CreditLine(lead: String, name: String, open: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text("$lead ", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
        Text(
            name,
            color = Felt.Brass,
            style = MaterialTheme.typography.labelLarge.copy(
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            ),
            modifier = Modifier.clickable(onClick = open).padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}
