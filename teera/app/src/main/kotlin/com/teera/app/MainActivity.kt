package com.teera.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teera.app.net.Link
import com.teera.app.ui.theme.Felt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teera.app.sound.SoundEngine
import com.teera.app.ui.LobbyScreen
import com.teera.app.ui.MenuScreen
import com.teera.app.ui.SoundSettings
import com.teera.app.ui.Talk
import com.teera.app.ui.TableScreen
import com.teera.app.ui.theme.TeeraTheme

class MainActivity : ComponentActivity() {

    private lateinit var sound: SoundEngine
    private val model: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sound = SoundEngine(applicationContext)
        setContent {
            TeeraTheme(dark = true) {
                TeeraApp(sound = sound)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sound.onResume()
    }

    override fun onPause() {
        // The music stops with the app: a game left in a pocket should not keep playing.
        sound.onPause()
        super.onPause()
    }

    override fun onStop() {
        // Out of sight, the mic closes: nobody at the table should overhear the room.
        model.pauseVoice()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) sound.release()
        super.onDestroy()
    }
}

@Composable
fun TeeraApp(sound: SoundEngine?, viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Table talk: the mic needs asking for the first time, and only then.
    val micOn by viewModel.voice.micOn.collectAsStateWithLifecycle()
    val talking by viewModel.voice.talking.collectAsStateWithLifecycle()
    val hearing by viewModel.voice.hearing.collectAsStateWithLifecycle()
    val speaking by viewModel.voice.speaking.collectAsStateWithLifecycle()
    val askForMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setMic(true) else viewModel.micRefused()
    }
    val talk = Talk(
        micOn = micOn,
        talking = talking,
        hearing = hearing,
        speaking = speaking,
        onMic = {
            when {
                micOn -> viewModel.setMic(false)
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> viewModel.setMic(true)
                else -> askForMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onHearing = { viewModel.setHearing(!hearing) },
    ).takeUnless { state.offline }

    // Toggles live in the engine; this mirrors them so the screen redraws when they change.
    var effectsOn by remember { mutableStateOf(sound?.effectsOn ?: false) }
    var musicOn by remember { mutableStateOf(sound?.musicOn ?: false) }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbars.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    // Everything the table should sound like, played as it arrives.
    LaunchedEffect(sound) {
        val engine = sound ?: return@LaunchedEffect
        viewModel.reactions.collect { reaction ->
            reaction.cues.forEach(engine::play)
            reaction.lines.forEach { engine.say(it.key, it.text) }
            reaction.mood?.let(engine::setMood)
        }
    }

    // Music belongs to the table, not the menu.
    LaunchedEffect(state.screen, sound) {
        if (state.screen == Screen.Table) sound?.startMusic() else sound?.stopMusic()
    }

    // Tables on the Wi-Fi are only looked for while somebody could pick one.
    LaunchedEffect(state.screen) { viewModel.lookForWifiTables(state.screen == Screen.Menu) }

    BackHandler(enabled = state.screen != Screen.Menu) { viewModel.back() }

    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                Screen.Menu -> MenuScreen(
                    playerName = state.playerName,
                    onNameChange = viewModel::setName,
                    onHost = viewModel::hostOnline,
                    onJoin = viewModel::join,
                    onSolo = { viewModel.playOffline() },
                    connecting = state.connecting,
                    onHostWifi = viewModel::hostOnWifi,
                    wifiTables = state.wifiTables,
                    onJoinWifi = viewModel::joinWifiTable,
                    onJoinAddress = viewModel::joinAddress,
                    hasInternetServer = state.hasInternetServer,
                    onSetServer = viewModel::setServer,
                    waking = state.link == Link.WAKING,
                    lastRoom = state.lastRoom,
                )

                Screen.Lobby -> state.room?.let { room ->
                    LobbyScreen(
                        room = room,
                        myId = state.myId,
                        isHost = state.isHost,
                        offline = state.offline,
                        wifiAddress = state.wifiAddress,
                        onTakeSeat = viewModel::takeSeat,
                        onToggleBot = { seat, enabled -> viewModel.setBot(seat, enabled) },
                        onSetRules = viewModel::setDraftRules,
                        onStart = viewModel::startMatch,
                        onLeave = viewModel::leave,
                        onShare = { code -> shareRoomCode(context, code) },
                        talk = talk,
                    )
                }

                Screen.Table -> state.view?.let { view ->
                    TableScreen(
                        view = view,
                        onMove = viewModel::play,
                        onNextGame = viewModel::nextGame,
                        onLeave = viewModel::leave,
                        emotes = state.emotes,
                        onEmote = viewModel::sendEmote,
                        sound = SoundSettings(
                            effectsOn = effectsOn,
                            musicOn = musicOn,
                            onEffects = { on -> sound?.effectsOn = on; effectsOn = on },
                            onMusic = { on -> sound?.musicOn = on; musicOn = on },
                        ),
                        talk = talk,
                    )
                }
            }

            // The server is holding the seat; say so, so nobody thinks the game froze.
            if (state.link == Link.LOST && state.screen != Screen.Menu) {
                Text(
                    "انقطع الاتصال… يرجع لحاله، ومكانك محفوظ",
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color(0xE6812A1E))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                )
            }
        }
    }
}

/** Hands the room code to WhatsApp, Telegram or whatever the phone offers. */
private fun shareRoomCode(context: Context, code: String) {
    val text = "تعال نلعب طيرة 🎲\nكود الغرفة: $code\nافتح التطبيق ← عبر الإنترنت ← اكتب الكود"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "دزّ الكود"))
}
