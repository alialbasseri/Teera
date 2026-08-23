package com.dominoes.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dominoes.app.net.GameClient
import com.dominoes.app.net.LocalGameClient
import com.dominoes.app.net.WebSocketGameClient
import com.dominoes.core.ClientMessage
import com.dominoes.core.Difficulty
import com.dominoes.core.Move
import com.dominoes.core.PlayerView
import com.dominoes.core.RoomInfo
import com.dominoes.core.RuleSet
import com.dominoes.core.ServerMessage
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { Menu, Rules, Lobby, Table }

data class ChatLine(val name: String, val text: String)

data class UiState(
    val myId: String = "",
    val screen: Screen = Screen.Menu,
    val playerName: String = "Player",
    val serverUrl: String = DEFAULT_SERVER,
    val room: RoomInfo? = null,
    val view: PlayerView? = null,
    val chat: List<ChatLine> = emptyList(),
    /** Whatever the host is editing before the deal; the table's rules once it starts. */
    val draftRules: RuleSet = RuleSet.CLASSIC_DRAW,
    val notice: String? = null,
    val connecting: Boolean = false,
    val offline: Boolean = false,
) {
    val isHost: Boolean get() = room == null || room.hostId == myId
}

const val DEFAULT_SERVER = "wss://dominoes.example.com/ws"

/**
 * Holds the connection and the last view the referee sent.
 *
 * The phone never decides anything about the game: it renders [PlayerView] and asks for
 * moves. Whether the referee is across the internet or inside this process is the only
 * thing [hostOnline], [join] and [playOffline] actually change.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("dominoes", Context.MODE_PRIVATE)

    /** Stable across reinstalls of the process, so a reconnect finds its seat again. */
    private val playerId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_ID, it).apply()
    }

    private val _state = MutableStateFlow(
        UiState(
            myId = playerId,
            playerName = prefs.getString(KEY_NAME, null) ?: "Player",
            serverUrl = prefs.getString(KEY_SERVER, null) ?: DEFAULT_SERVER,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: GameClient? = null
    private var pump: Job? = null

    val hostId: String get() = playerId

    fun setName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
        _state.update { it.copy(playerName = name) }
    }

    fun setServer(url: String) {
        prefs.edit().putString(KEY_SERVER, url).apply()
        _state.update { it.copy(serverUrl = url) }
    }

    fun setDraftRules(rules: RuleSet) {
        _state.update { it.copy(draftRules = rules) }
        client?.send(ClientMessage.SetRules(rules))
    }

    fun openRules() = _state.update { it.copy(screen = Screen.Rules) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // -- starting a table ---------------------------------------------------

    fun hostOnline() {
        connectOnline()
        client?.send(ClientMessage.CreateRoom(_state.value.draftRules))
    }

    fun join(code: String) {
        connectOnline()
        client?.send(ClientMessage.JoinRoom(code.trim().uppercase()))
    }

    /** A table against bots, refereed in this process. */
    fun playOffline(bots: Int = 3, difficulty: Difficulty = Difficulty.NORMAL) {
        val rules = _state.value.draftRules
        attach(LocalGameClient(viewModelScope, playerId, _state.value.playerName, rules))
        _state.update { it.copy(offline = true, connecting = false) }
        repeat(bots.coerceAtMost(rules.playerCount - 1)) { index ->
            client?.send(ClientMessage.SetBot(index + 1, enabled = true, difficulty = difficulty))
        }
        client?.send(ClientMessage.StartMatch)
    }

    private fun connectOnline() {
        attach(
            WebSocketGameClient(
                url = _state.value.serverUrl,
                scope = viewModelScope,
                playerId = playerId,
                playerName = _state.value.playerName,
            ),
        )
        _state.update { it.copy(connecting = true, offline = false) }
    }

    private fun attach(next: GameClient) {
        client?.close()
        pump?.cancel()
        client = next
        _state.update { it.copy(room = null, view = null, chat = emptyList(), notice = null) }
        pump = viewModelScope.launch {
            next.incoming.collect(::onMessage)
        }
    }

    private fun onMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> _state.update { it.copy(connecting = false) }

            is ServerMessage.RoomUpdate -> _state.update {
                it.copy(
                    room = message.room,
                    draftRules = if (message.room.started) it.draftRules else message.room.rules,
                    screen = if (message.room.started) Screen.Table else Screen.Lobby,
                    connecting = false,
                )
            }

            is ServerMessage.GameUpdate -> _state.update {
                it.copy(view = message.view, screen = Screen.Table, connecting = false)
            }

            // A refused move means the phone and the referee disagreed; the view attached
            // to the refusal is the truth, so take it and say why.
            is ServerMessage.MoveRejected -> _state.update {
                it.copy(view = message.view, notice = message.reason.explain())
            }

            is ServerMessage.Failure -> _state.update { it.copy(notice = message.message, connecting = false) }

            is ServerMessage.ChatMessage -> _state.update {
                it.copy(chat = (it.chat + ChatLine(message.name, message.text)).takeLast(30))
            }

            ServerMessage.Pong -> Unit
        }
    }

    // -- lobby and play -----------------------------------------------------

    fun setBot(seat: Int, enabled: Boolean, difficulty: Difficulty = Difficulty.NORMAL) =
        client?.send(ClientMessage.SetBot(seat, enabled, difficulty))

    fun startMatch() = client?.send(ClientMessage.StartMatch)

    fun play(move: Move) = client?.send(ClientMessage.MakeMove(move))

    fun nextRound() = client?.send(ClientMessage.NextRound)

    fun chat(text: String) = client?.send(ClientMessage.Chat(text))

    fun leave() {
        client?.send(ClientMessage.LeaveRoom)
        client?.close()
        client = null
        pump?.cancel()
        _state.update {
            it.copy(screen = Screen.Menu, room = null, view = null, chat = emptyList(), offline = false)
        }
    }

    fun back() {
        when (_state.value.screen) {
            Screen.Rules -> _state.update {
                it.copy(screen = if (it.room != null) Screen.Lobby else Screen.Menu)
            }
            Screen.Lobby, Screen.Table -> leave()
            Screen.Menu -> Unit
        }
    }

    override fun onCleared() {
        client?.close()
        super.onCleared()
    }

    private companion object {
        const val KEY_ID = "playerId"
        const val KEY_NAME = "playerName"
        const val KEY_SERVER = "serverUrl"
    }
}

private fun com.dominoes.core.Rejection.explain(): String = when (this) {
    com.dominoes.core.Rejection.NOT_YOUR_TURN -> "Wait for your turn"
    com.dominoes.core.Rejection.ROUND_NOT_RUNNING -> "That round is over"
    com.dominoes.core.Rejection.TILE_NOT_IN_HAND -> "You are not holding that bone"
    com.dominoes.core.Rejection.TILE_DOES_NOT_MATCH -> "That bone does not match the end"
    com.dominoes.core.Rejection.MUST_PLAY_OPENING_TILE -> "The opening bone has to go down first"
    com.dominoes.core.Rejection.MUST_PLAY_IF_ABLE -> "You have a legal move, so you must play it"
    com.dominoes.core.Rejection.MUST_DRAW_FIRST -> "Draw from the boneyard before passing"
    com.dominoes.core.Rejection.BONEYARD_EMPTY -> "The boneyard is empty"
    com.dominoes.core.Rejection.DRAW_LIMIT_REACHED -> "No more draws this turn"
}
