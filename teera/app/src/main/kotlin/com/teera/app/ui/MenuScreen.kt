package com.teera.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teera.app.net.WifiTable
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino
import kotlinx.coroutines.delay

/**
 * The front door. It fits on one screen with nothing to scroll: who you are, play the
 * bots, play your friends over the internet. Playing on the same Wi-Fi, which few need
 * now that the server is up, sits behind a small link at the bottom.
 */
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
    hasInternetServer: Boolean = true,
    /** Knocking on a sleeping server, which takes a while the first time. */
    waking: Boolean = false,
    /** The internet room this phone was last in, if any, to go straight back to. */
    lastRoom: String? = null,
    onRules: () -> Unit = {},
    onProfile: () -> Unit = {},
    onRejoin: (String) -> Unit = onJoin,
    onFriends: () -> Unit = {},
    /** How many of this player's people have the app open, for the link's count. */
    friendsOnline: Int = 0,
    avatar: @Composable (size: Dp) -> Unit = { size -> Initial(playerName, size) },
) {
    var code by remember { mutableStateOf("") }
    var wifiOpen by remember { mutableStateOf(false) }
    var changelogOpen by remember { mutableStateOf(false) }

    CafeTable {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Smaller phones get a smaller emblem rather than a scrolling menu.
            val roomy = maxHeight > 720.dp
            // Laid out to fill exactly one screen; only if a small phone with large text
            // cannot hold it all does it scroll, rather than cutting the bottom off.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Emblem(Modifier.scale(if (roomy) 1f else 0.78f))
                Text("طيرة", color = Felt.Brass, fontSize = if (roomy) 62.sp else 52.sp, style = MaterialTheme.typography.displayLarge)
                if (roomy) {
                    Text("دومينا عراقية · أربعة · فريقين", color = Felt.Muted, style = MaterialTheme.typography.bodyMedium)
                }
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {

                // Who you are: your picture opens your profile.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Felt.Brass, CircleShape)
                            .clickable(onClick = onProfile)
                            .testTag("profile"),
                        contentAlignment = Alignment.Center,
                    ) { avatar(56.dp) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { onNameChange(it.take(16)) },
                        label = { Text("اسمك") },
                        singleLine = true,
                        colors = brassField(),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))
                GoldButton("العب ضد البوتات", onSolo)
                Spacer(Modifier.height(14.dp))

                // Friends anywhere: the way the game is mostly played.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF120B05).copy(alpha = 0.6f))
                        .border(1.dp, Felt.Brass.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("العب ويا جماعتك", color = Felt.Brass, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (lastRoom != null) {
                        GoldButton("ارجع للغرفة $lastRoom", { onRejoin(lastRoom) })
                        Spacer(Modifier.height(8.dp))
                    }
                    BrassButton(
                        when {
                            connecting && waking -> "السيرفر يحمى…"
                            connecting -> "يحاول يتصل…"
                            else -> "افتح غرفة"
                        },
                        onHost,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connecting && hasInternetServer,
                    )
                    if (connecting && waking) {
                        Text(
                            "إذا محد لعب من فترة، السيرفر يگعد وياخذ تقريباً دقيقة حتى يحمى",
                            color = Felt.Muted,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
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
                }

              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The quieter doors.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SmallLink(if (friendsOnline > 0) "جماعتي ($friendsOnline)" else "جماعتي", onFriends, tag = "friends")
                    Text("·", color = Felt.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                    SmallLink("القوانين", onRules)
                    Text("·", color = Felt.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                    SmallLink(
                        if (wifiTables.isEmpty()) "واي فاي" else "واي فاي (${wifiTables.size})",
                        { wifiOpen = true },
                        tag = "wifi",
                    )
                }
                Credits()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "نسخة ${com.teera.app.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Felt.Muted,
                    )
                    Text("·", color = Felt.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                    SmallLink("شنو الجديد", { changelogOpen = true }, tag = "changelogLink")
                }
              }
            }
        }
    }

    if (changelogOpen) ChangelogDialog(onClose = { changelogOpen = false })

    if (wifiOpen) {
        WifiDialog(
            connecting = connecting,
            tables = wifiTables,
            onHost = { wifiOpen = false; onHostWifi() },
            onJoin = { wifiOpen = false; onJoinWifi(it) },
            onJoinAddress = { wifiOpen = false; onJoinAddress(it) },
            onClose = { wifiOpen = false },
        )
    }
}

@Composable
private fun SmallLink(text: String, onClick: () -> Unit, tag: String = text) {
    Text(
        text,
        color = Felt.Brass,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag(tag),
    )
}

/** Playing in one room with no internet: one phone hosts, the rest find it on the Wi-Fi. */
@Composable
private fun WifiDialog(
    connecting: Boolean,
    tables: List<WifiTable>,
    onHost: () -> Unit,
    onJoin: (WifiTable) -> Unit,
    onJoinAddress: (String) -> Unit,
    onClose: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("على نفس الواي فاي", color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
            Text(
                "للعب بنفس البيت بدون إنترنت: واحد يفتح طاولة، والباقين يدخلون عليها.",
                color = Felt.Parchment,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            BrassButton(if (connecting) "يحاول يتصل…" else "افتح طاولة", onHost, Modifier.fillMaxWidth(), enabled = !connecting)
            tables.forEach { table -> GoldButton("ادخل طاولة ${table.name}", { onJoin(table) }) }
            if (tables.isEmpty()) {
                Text("الطاولات اللي يفتحها جماعتك تطلع هنا", color = Felt.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.filter { c -> c.isDigit() || c == '.' || c == ':' }.take(21) },
                    label = { Text("أو بالعنوان، مثل 192.168.1.5") },
                    singleLine = true,
                    colors = brassField(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                BrassButton("ادخل", { onJoinAddress(address) }, enabled = address.count { it == '.' } == 3)
            }
            SmallLink("سكّر", onClose)
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
    focusedContainerColor = Color(0x99120B05),
    unfocusedContainerColor = Color(0x99120B05),
)

/**
 * The game's mark: the six-six that opens a match and the zero-one that flies it, fanned
 * under an Ishtar rosette. It settles into place when the app opens.
 */
@Composable
private fun Emblem(modifier: Modifier = Modifier) {
    val settle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        settle.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val fan = settle.value

    Box(modifier.size(200.dp, 140.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.size(60.dp).align(Alignment.TopCenter)) {
            drawRosette(Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.46f, alpha = fan)
        }
        DominoTile(
            null,
            Modifier.size(44.dp, 88.dp).offset(x = (-42 * fan).dp, y = (6 * fan).dp).rotate(-16f * fan),
            faceUp = false,
        )
        DominoTile(
            Domino(6, 6),
            Modifier.size(44.dp, 88.dp).offset(x = (42 * fan).dp, y = (6 * fan).dp).rotate(16f * fan),
        )
        DominoTile(Domino(0, 1), Modifier.size(46.dp, 92.dp).scale(0.9f + 0.1f * fan))
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
        // One line each, never wrapped: a narrow phone or large text broke "Tech Vision" in two.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CreditLine("Designed by", "Ali Albasseri") { runCatching { links.openUri(DESIGNER_URL) } }
            CreditLine("Powered by", "Tech Vision") { runCatching { links.openUri(TECH_VISION_URL) } }
        }
    }
}

@Composable
private fun CreditLine(lead: String, name: String, open: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$lead ", color = Felt.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        Text(
            name,
            maxLines = 1,
            softWrap = false,
            color = Felt.Brass,
            style = MaterialTheme.typography.labelLarge.copy(
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            ),
            modifier = Modifier.clickable(onClick = open).padding(vertical = 2.dp, horizontal = 2.dp),
        )
    }
}
