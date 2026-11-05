package com.teera.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
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
import com.teera.app.ui.TableScreen
import com.teera.app.ui.theme.TeeraTheme

class MainActivity : ComponentActivity() {

    private lateinit var sound: SoundEngine

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

    override fun onDestroy() {
        if (isFinishing) sound.release()
        super.onDestroy()
    }
}

@Composable
fun TeeraApp(sound: SoundEngine?, viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

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
                    )
                }
            }
        }
    }
}
