package com.teera.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teera.app.net.GameClient
import com.teera.app.net.LanBeacon
import com.teera.app.net.LanHost
import com.teera.app.net.Link
import com.teera.app.net.WifiTable
import com.teera.app.net.localAddress
import com.teera.app.net.openRoomAt
import com.teera.app.net.LocalGameClient
import com.teera.app.net.WebSocketGameClient
import com.teera.app.sound.Reaction
import com.teera.app.sound.react
import com.teera.app.voice.VoiceChat
import com.teera.core.ClientMessage
import com.teera.core.ErrorCode
import com.teera.core.Difficulty
import com.teera.core.Emote
import com.teera.core.Move
import com.teera.core.Pack
import com.teera.core.PlayerView
import com.teera.core.RoomInfo
import com.teera.core.Rules
import com.teera.core.ServerMessage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Menu, Lobby, Table, Rules, Profile, Friends }

/** Somebody this player knows: met at a table, or added by their code. */
data class Friend(val id: String, val name: String, val avatar: String)

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
    val draftRules: Rules = Rules.DEFAULT,
    val notice: String? = null,
    val connecting: Boolean = false,
    val offline: Boolean = false,
    /** Soundboard presses still on screen, newest last. */
    val emotes: List<ShownEmote> = emptyList(),
    /** Set while this phone hosts a table on the Wi-Fi: the address friends can type in. */
    val wifiAddress: String? = null,
    /** Tables other phones on this Wi-Fi are hosting. */
    val wifiTables: List<WifiTable> = emptyList(),
    /** How the line to the referee is doing. */
    val link: Link = Link.UP,
    /** The internet room this phone was last in, offered on the menu to go back to. */
    val lastRoom: String? = null,
    /** This player's picture: "p:3", "photo" or empty (see [com.teera.core.Seat.avatar]). */
    val avatar: String = "",
    /** Pictures players chose themselves, by player id; this player's own included. */
    val photos: Map<String, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
    /** This player's people, and which of them have the app open now. */
    val friends: List<Friend> = emptyList(),
    val online: Set<String> = emptySet(),
    /** An invitation waiting for an answer. */
    val invite: ServerMessage.Invited? = null,
) {
    /** What friends type to add this player. */
    val myCode: String get() = com.teera.core.shortCodeOf(myId)

    /** Whether an internet server has actually been set up yet. */
    val hasInternetServer: Boolean get() = serverUrl.startsWith("ws")
    val isHost: Boolean get() = room == null || room.hostId == myId
}

/** Turns a pasted host name or web address into the websocket address the server listens on. */
fun normaliseServer(input: String): String {
    var address = input.trim().trimEnd('/')
    if (address.isEmpty()) return ""
    address = when {
        address.startsWith("wss://") || address.startsWith("ws://") -> address
        address.startsWith("https://") -> "wss://" + address.removePrefix("https://")
        address.startsWith("http://") -> "ws://" + address.removePrefix("http://")
        else -> "wss://$address"
    }
    return if (address.endsWith("/ws")) address else "$address/ws"
}

/** A soundboard press, shown as a bubble over the seat that sent it. */
data class ShownEmote(val seat: Int, val emote: Emote, val id: Long)

/** The internet server baked into this build, or empty when none has been set up. */
val DEFAULT_SERVER: String = BuildConfig.SERVER_URL

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
            lastRoom = prefs.getString(KEY_LAST_ROOM, null),
            avatar = prefs.getString(KEY_AVATAR, null) ?: "p:${(UUID.randomUUID().hashCode() and 0x7) }",
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Read from the microphone's thread as well as the main one.
    @Volatile
    private var client: GameClient? = null
    private var pump: Job? = null
    private var linkWatch: Job? = null
    private var listening: Job? = null

    /** Table talk. Its packets go out over whatever connection the table is on. */
    val voice = VoiceChat(app).also { chat -> chat.send = { packet -> client?.sendVoice(packet) } }

    /** Whether the current table is on the internet server, the one worth coming back to. */
    private var onInternet = false

    /**
     * What the table should sound like, one change at a time. The screen plays these; the
     * view model only decides them, so the decision is the same online and off.
     */
    private val _reactions = MutableSharedFlow<Reaction>(extraBufferCapacity = 64)
    val reactions: SharedFlow<Reaction> = _reactions.asSharedFlow()

    private var lastView: PlayerView? = null
    private var emoteCounter = 0L

    val hostId: String get() = playerId

    fun setName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
        _state.update { it.copy(playerName = name) }
    }

    /**
     * Points this phone at an internet server. Takes whatever somebody is likely to paste
     * - "teera.onrender.com", "https://teera.onrender.com" - and makes a websocket address
     * of it.
     */
    fun setServer(url: String) {
        val address = normaliseServer(url)
        prefs.edit().putString(KEY_SERVER, address).apply()
        _state.update { it.copy(serverUrl = address) }
    }

    fun setDraftRules(rules: Rules) {
        _state.update { it.copy(draftRules = rules) }
        client?.send(ClientMessage.SetRules(rules))
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // -- starting a table ---------------------------------------------------

    fun hostOnline() {
        if (!_state.value.hasInternetServer) return noInternetServer()
        connectTo(_state.value.serverUrl, internet = true)
        client?.send(ClientMessage.CreateRoom(_state.value.draftRules))
    }

    /** [rejoin]: going back to a room, or by its link, which reopens it if the server lost it. */
    fun join(code: String, rejoin: Boolean = false) {
        if (!_state.value.hasInternetServer) return noInternetServer()
        connectTo(_state.value.serverUrl, internet = true)
        client?.send(ClientMessage.JoinRoom(code.trim().uppercase(), rejoin))
    }

    fun rejoin(code: String) = join(code, rejoin = true)

    /**
     * Somebody tapped a room's link. Straight in - unless this phone is already at that
     * table, in which case there is nothing to do.
     */
    fun openLink(code: String) {
        val wanted = code.trim().uppercase()
        if (wanted.length != 4) return
        if (_state.value.room?.code == wanted && _state.value.screen != Screen.Menu) return
        if (_state.value.screen == Screen.Lobby || _state.value.screen == Screen.Table) leave()
        rejoin(wanted)
    }

    /** The address a room's link points at: the internet server's, as a web page. */
    fun linkFor(code: String): String? = roomLink(_state.value.serverUrl, code)

    private fun rememberRoom(code: String?) {
        prefs.edit().apply { if (code == null) remove(KEY_LAST_ROOM) else putString(KEY_LAST_ROOM, code) }.apply()
        _state.update { it.copy(lastRoom = code) }
    }

    /**
     * Until somebody hosts the server on the internet there is nothing to connect to, and
     * trying only produces "unable to resolve host". Say what does work instead.
     */
    private fun noInternetServer() = _state.update {
        it.copy(notice = "اللعب عبر الإنترنت يحتاج سيرفر، ولسه ما منصوب. العبوا على نفس الواي فاي")
    }

    // -- a table on the Wi-Fi -------------------------------------------------

    private val lanHost = LanHost()
    private val beacon = LanBeacon(app)
    private var announcedCode: String? = null

    init {
        viewModelScope.launch {
            beacon.tables.collect { found -> _state.update { it.copy(wifiTables = found) } }
        }
    }

    /** Called while the menu is showing, so tables on the network appear as they open. */
    fun lookForWifiTables(looking: Boolean) = if (looking) beacon.startLooking() else beacon.stopLooking()

    /** This phone becomes the referee; friends on the same Wi-Fi find it and join. */
    fun hostOnWifi() {
        viewModelScope.launch {
            val port = runCatching { withContext(Dispatchers.IO) { lanHost.start() } }.getOrElse {
                _state.update { it.copy(notice = "ما قدرت أفتح الطاولة على هذا الجهاز") }
                return@launch
            }
            connectTo("ws://127.0.0.1:$port/ws")
            _state.update { it.copy(wifiAddress = localAddress()?.let { ip -> "$ip:$port" }) }
            client?.send(ClientMessage.CreateRoom(_state.value.draftRules))
        }
    }

    fun joinWifiTable(table: WifiTable) {
        viewModelScope.launch {
            val code = table.code ?: openRoomAt(table.host, table.port)
            if (code == null) {
                _state.update { it.copy(notice = "الطاولة بدت أو تسكّرت") }
                return@launch
            }
            connectTo("ws://${table.host}:${table.port}/ws")
            client?.send(ClientMessage.JoinRoom(code))
        }
    }

    /** For a network that hides tables from each other: type the host's address instead. */
    fun joinAddress(address: String) {
        val parts = address.trim().split(":")
        val host = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        val port = parts.getOrNull(1)?.toIntOrNull() ?: LanHost.DEFAULT_PORT
        joinWifiTable(WifiTable(name = host, host = host, port = port, code = null))
    }

    /** Puts the hosted table on the network once the room exists and has a code. */
    private fun announceIfHosting(room: RoomInfo) {
        if (!lanHost.isRunning || room.started || announcedCode == room.code) return
        announcedCode = room.code
        beacon.announce(_state.value.playerName, lanHost.port, room.code)
    }

    private fun stopHosting() {
        beacon.stopAnnouncing()
        announcedCode = null
        if (lanHost.isRunning) {
            viewModelScope.launch(Dispatchers.IO) {
                // Give the goodbye a moment to reach the other phones before the referee goes.
                delay(400)
                lanHost.stop()
            }
        }
        _state.update { it.copy(wifiAddress = null) }
    }

    /** A table against bots, refereed in this process. */
    fun playOffline(difficulty: Difficulty = Difficulty.NORMAL) {
        val rules = _state.value.draftRules
        attach(LocalGameClient(viewModelScope, playerId, _state.value.playerName, rules, avatar = _state.value.avatar))
        _state.update { it.copy(offline = true, connecting = false) }
        for (seat in 1 until Pack.PLAYERS) {
            client?.send(ClientMessage.SetBot(seat, enabled = true, difficulty = difficulty))
        }
        client?.send(ClientMessage.StartMatch)
    }

    private fun connectTo(url: String, internet: Boolean = false) {
        attach(
            WebSocketGameClient(
                url = url,
                scope = viewModelScope,
                playerId = playerId,
                playerName = _state.value.playerName,
                wakeFirst = internet,
                avatar = _state.value.avatar,
                photo = if (_state.value.avatar == "photo") myPhotoBase64 else null,
            ),
        )
        onInternet = internet
        _state.update { it.copy(connecting = true, offline = false) }
    }

    private fun attach(next: GameClient) {
        client?.close()
        pump?.cancel()
        linkWatch?.cancel()
        client = next
        onInternet = false
        _state.update { it.copy(room = null, view = null, chat = emptyList(), notice = null) }
        pump = viewModelScope.launch {
            next.incoming.collect(::onMessage)
        }
        listening?.cancel()
        listening = viewModelScope.launch(Dispatchers.Default) {
            next.voice.collect(voice::receive)
        }
        linkWatch = viewModelScope.launch {
            next.link.collect { link ->
                _state.update { it.copy(link = link) }
                if (link == Link.UNREACHABLE) unreachable()
            }
        }
    }

    /** The client has given up getting through: say so plainly and go back to the menu. */
    private fun unreachable() {
        val where = if (onInternet) "السيرفر" else "الطاولة"
        voice.stopAll()
        listening?.cancel()
        client?.close()
        client = null
        pump?.cancel()
        lastView = null
        stopHosting()
        _state.update {
            it.copy(
                screen = Screen.Menu, room = null, view = null, connecting = false, link = Link.UP,
                notice = "ما قدرت أوصل لـ$where. تأكد من النت وجرّب مرة ثانية",
            )
        }
    }

    private fun onMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> _state.update { it.copy(connecting = false) }

            is ServerMessage.RoomUpdate -> {
                announceIfHosting(message.room)
                if (onInternet) metAt(message.room.seats)
                if (onInternet && message.room.code != _state.value.lastRoom) rememberRoom(message.room.code)
                // Once the deal is done nobody else can sit down, so stop advertising it.
                if (message.room.started) beacon.stopAnnouncing()
                _state.update {
                    it.copy(
                        room = message.room,
                        draftRules = if (message.room.started) it.draftRules else message.room.rules,
                        screen = if (message.room.started) Screen.Table else Screen.Lobby,
                        connecting = false,
                    )
                }
            }

            is ServerMessage.GameUpdate -> {
                _reactions.tryEmit(react(lastView, message.view))
                lastView = message.view
                _state.update { it.copy(view = message.view, screen = Screen.Table, connecting = false) }
            }

            is ServerMessage.Avatar -> viewModelScope.launch(Dispatchers.Default) {
                val picture = decodePhoto(message.photo) ?: return@launch
                _state.update { it.copy(photos = it.photos + (message.playerId to picture)) }
            }

            is ServerMessage.EmoteShown -> {
                _reactions.tryEmit(react(message.emote))
                val shown = ShownEmote(message.seat, message.emote, ++emoteCounter)
                _state.update { it.copy(emotes = (it.emotes + shown).takeLast(4)) }
                viewModelScope.launch {
                    delay(EMOTE_SHOWN_MS)
                    _state.update { s -> s.copy(emotes = s.emotes.filterNot { it.id == shown.id }) }
                }
            }

            // A refused move means the phone and the referee disagreed; the view attached
            // to the refusal is the truth, so take it and say why.
            is ServerMessage.MoveRejected -> _state.update {
                it.copy(view = message.view, notice = message.reason.explain())
            }

            is ServerMessage.Failure -> {
                if (message.code == ErrorCode.ROOM_NOT_FOUND) rememberRoom(null)
                _state.update { it.copy(notice = message.explain(), connecting = false) }
            }

            is ServerMessage.ChatMessage -> _state.update {
                it.copy(chat = (it.chat + ChatLine(message.name, message.text)).takeLast(30))
            }

            ServerMessage.Pong -> Unit

            is ServerMessage.Online, is ServerMessage.PlayerFound,
            is ServerMessage.Invited, is ServerMessage.InviteSent -> onPresence(message)
        }
    }

    // -- lobby and play -----------------------------------------------------

    fun setBot(seat: Int, enabled: Boolean, difficulty: Difficulty = Difficulty.NORMAL) =
        client?.send(ClientMessage.SetBot(seat, enabled, difficulty))

    fun startMatch() = client?.send(ClientMessage.StartMatch)

    fun play(move: Move) = client?.send(ClientMessage.MakeMove(move))

    fun nextGame() = client?.send(ClientMessage.NextGame)

    fun takeSeat(seat: Int) = client?.send(ClientMessage.TakeSeat(seat))

    fun chat(text: String) = client?.send(ClientMessage.Chat(text))

    fun sendEmote(emote: Emote) = client?.send(ClientMessage.SendEmote(emote))

    // -- talking -----------------------------------------------------------------

    /** The screen has made sure of the microphone permission before switching it on. */
    fun setMic(on: Boolean) {
        if (on && _state.value.offline) return
        voice.setMic(on)
        if (on && !voice.micOn.value) {
            _state.update { it.copy(notice = "ما قدرت أشغّل المايك") }
        }
    }

    fun micRefused() = _state.update {
        it.copy(notice = "حتى تحچي ويا أصحابك لازم تسمح للتطبيق يستعمل المايك")
    }

    fun setHearing(on: Boolean) = voice.setHearing(on)

    /** The app has gone into the background: close the mic, nobody should be overheard. */
    fun pauseVoice() = voice.setMic(false)

    fun showRules() = _state.update { it.copy(screen = Screen.Rules) }

    // -- people and invitations --------------------------------------------------

    /**
     * A quiet connection to the internet server held while the app is open, on any screen:
     * it is how friends see this player online and how their invitations arrive. It also
     * wakes a sleeping server before anybody asks it for a room.
     */
    private var presence: GameClient? = null
    private var presencePump: Job? = null
    private var onlinePoll: Job? = null

    init {
        _state.update { it.copy(friends = loadFriends()) }
    }

    fun setForeground(foreground: Boolean) {
        if (foreground) startPresence() else stopPresence()
    }

    private fun startPresence() {
        if (presence != null || !_state.value.hasInternetServer) return
        if (System.getProperty("teera.noPresence") == "true") return
        val link = WebSocketGameClient(
            url = _state.value.serverUrl,
            scope = viewModelScope,
            playerId = playerId,
            playerName = _state.value.playerName,
            wakeFirst = true,
            avatar = _state.value.avatar.takeIf { it != "photo" }.orEmpty(),
        )
        presence = link
        presencePump = viewModelScope.launch { link.incoming.collect(::onPresence) }
        onlinePoll = viewModelScope.launch {
            while (true) {
                askWhoIsOnline()
                delay(ONLINE_POLL_MS)
            }
        }
    }

    private fun stopPresence() {
        onlinePoll?.cancel()
        presencePump?.cancel()
        presence?.close()
        presence = null
    }

    private fun askWhoIsOnline() {
        val ids = _state.value.friends.map { it.id }
        if (ids.isNotEmpty()) presence?.send(ClientMessage.WhoIsOnline(ids))
    }

    private fun onPresence(message: ServerMessage) {
        when (message) {
            is ServerMessage.Online -> _state.update { it.copy(online = message.playerIds.toSet()) }
            is ServerMessage.PlayerFound -> {
                val id = message.playerId
                if (id == null || id == playerId) {
                    _state.update { it.copy(notice = if (id == playerId) "هذا رمزك إنت" else "ماكو أحد فاتح التطبيق بهذا الرمز") }
                } else {
                    remember(Friend(id, message.name, message.avatar))
                    _state.update { it.copy(notice = "انضاف ${message.name} لجماعتك", online = it.online + id) }
                }
            }
            is ServerMessage.Invited -> _state.update {
                // The same invitation can arrive on both connections; show it once.
                if (it.invite == message || it.room?.code == message.code) it else it.copy(invite = message)
            }
            is ServerMessage.InviteSent -> if (!message.delivered) {
                val name = _state.value.friends.firstOrNull { it.id == message.playerId }?.name ?: "صاحبك"
                _state.update { it.copy(notice = "$name مو فاتح التطبيق هسه، دزله اللنك") }
            } else {
                _state.update { it.copy(notice = "انرسلت العزيمة") }
            }
            else -> Unit
        }
    }

    fun showFriends() {
        askWhoIsOnline()
        _state.update { it.copy(screen = Screen.Friends) }
    }

    fun closeFriends() = _state.update { if (it.screen == Screen.Friends) it.copy(screen = Screen.Menu) else it }

    fun addFriend(code: String) {
        val clean = code.trim().uppercase().filter { it.isLetterOrDigit() }
        if (clean.length != 6) return
        if (presence == null) startPresence()
        presence?.send(ClientMessage.FindPlayer(clean))
    }

    fun forget(friend: Friend) {
        val next = _state.value.friends.filterNot { it.id == friend.id }
        saveFriends(next)
        _state.update { it.copy(friends = next) }
    }

    fun invite(friend: Friend) {
        val code = _state.value.room?.code ?: return
        if (presence == null) startPresence()
        presence?.send(ClientMessage.Invite(friend.id, code))
    }

    fun acceptInvite() {
        val invite = _state.value.invite ?: return
        _state.update { it.copy(invite = null) }
        openLink(invite.code)
    }

    fun dismissInvite() = _state.update { it.copy(invite = null) }

    /** Everyone at an internet table becomes somebody this player knows. */
    private fun metAt(seats: List<com.teera.core.Seat>) {
        seats.filter { !it.isBot && it.playerId.isNotEmpty() && it.playerId != playerId }
            .forEach { remember(Friend(it.playerId, it.name, it.avatar)) }
    }

    private fun remember(friend: Friend) {
        val current = _state.value.friends
        val known = current.firstOrNull { it.id == friend.id }
        if (known == friend) return
        val next = listOf(friend) + current.filterNot { it.id == friend.id }
        saveFriends(next.take(MAX_FRIENDS))
        _state.update { it.copy(friends = next.take(MAX_FRIENDS)) }
    }

    private fun loadFriends(): List<Friend> =
        prefs.getString(KEY_FRIENDS, null).orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3) Friend(parts[0], parts[1], parts[2]) else null
        }

    private fun saveFriends(friends: List<Friend>) {
        val text = friends.joinToString("\n") { listOf(it.id, it.name.replace('\t', ' ').replace('\n', ' '), it.avatar).joinToString("\t") }
        prefs.edit().putString(KEY_FRIENDS, text).apply()
    }

    // -- profile ---------------------------------------------------------------

    private val photoFile = java.io.File(app.filesDir, "avatar.jpg")

    /** This player's photo as it goes over the wire, read once. */
    private var myPhotoBase64: String? = null

    init {
        // Everybody starts with one of the drawn pictures, kept from then on.
        if (prefs.getString(KEY_AVATAR, null) == null) prefs.edit().putString(KEY_AVATAR, _state.value.avatar).apply()
        // A photo chosen on an earlier run.
        if (photoFile.exists()) {
            viewModelScope.launch(Dispatchers.IO) {
                val bytes = runCatching { photoFile.readBytes() }.getOrNull() ?: return@launch
                myPhotoBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                decodePhoto(myPhotoBase64!!)?.let { picture ->
                    _state.update { it.copy(photos = it.photos + (playerId to picture)) }
                }
            }
        }
    }

    fun showProfile() = _state.update { it.copy(screen = Screen.Profile) }

    fun closeProfile() = _state.update { if (it.screen == Screen.Profile) it.copy(screen = Screen.Menu) else it }

    fun choosePreset(index: Int) {
        val ref = "p:$index"
        prefs.edit().putString(KEY_AVATAR, ref).apply()
        _state.update { it.copy(avatar = ref) }
    }

    /**
     * Takes a photo from the gallery: squared off from the middle, shrunk to 160 pixels
     * and saved as a small JPEG, which is all a seat at the table needs.
     */
    fun choosePhoto(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val bitmap = runCatching {
                app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap == null) {
                _state.update { it.copy(notice = "ما قدرت أفتح الصورة") }
                return@launch
            }
            val side = minOf(bitmap.width, bitmap.height)
            val square = android.graphics.Bitmap.createBitmap(
                bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side,
            )
            val small = android.graphics.Bitmap.createScaledBitmap(square, PHOTO_SIZE, PHOTO_SIZE, true)
            val bytes = java.io.ByteArrayOutputStream().use { out ->
                small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, out)
                out.toByteArray()
            }
            photoFile.writeBytes(bytes)
            myPhotoBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            prefs.edit().putString(KEY_AVATAR, "photo").apply()
            val picture = small.asImageBitmapCompat()
            _state.update { it.copy(avatar = "photo", photos = it.photos + (playerId to picture)) }
        }
    }

    fun closeRules() = _state.update { if (it.screen == Screen.Rules) it.copy(screen = Screen.Menu) else it }

    fun leave() {
        voice.stopAll()
        listening?.cancel()
        client?.send(ClientMessage.LeaveRoom)
        client?.close()
        client = null
        pump?.cancel()
        linkWatch?.cancel()
        if (onInternet) rememberRoom(null)
        onInternet = false
        lastView = null
        stopHosting()
        _state.update {
            it.copy(
                screen = Screen.Menu, room = null, view = null, chat = emptyList(),
                offline = false, connecting = false, link = Link.UP,
            )
        }
    }

    fun back() {
        when (_state.value.screen) {
            Screen.Lobby, Screen.Table -> leave()
            Screen.Rules -> closeRules()
            Screen.Profile -> closeProfile()
            Screen.Friends -> closeFriends()
            Screen.Menu -> Unit
        }
    }

    override fun onCleared() {
        stopPresence()
        voice.stopAll()
        client?.close()
        beacon.close()
        lanHost.stop()
        super.onCleared()
    }

    private companion object {
        const val EMOTE_SHOWN_MS = 2_600L
        const val KEY_ID = "playerId"
        const val KEY_NAME = "playerName"
        const val KEY_SERVER = "serverUrl"
        const val KEY_LAST_ROOM = "lastRoom"
        const val KEY_AVATAR = "avatar"
        const val KEY_FRIENDS = "friends"
        const val MAX_FRIENDS = 60
        const val ONLINE_POLL_MS = 15_000L
        const val PHOTO_SIZE = 160
    }
}

private fun android.graphics.Bitmap.asImageBitmapCompat() = asImageBitmap()

/** wss://host/ws and a code → https://host/j/CODE; null for a table on the Wi-Fi. */
fun roomLink(serverUrl: String, code: String): String? {
    if (!serverUrl.startsWith("wss://")) return null
    val host = serverUrl.removePrefix("wss://").substringBefore('/')
    return "https://$host/j/$code"
}

/** The room code in a link the app was opened with: https://host/j/CODE or teera://join/CODE. */
fun codeFromLink(link: String?): String? {
    val uri = link ?: return null
    val code = when {
        uri.startsWith("teera://join/") -> uri.removePrefix("teera://join/")
        "/j/" in uri -> uri.substringAfter("/j/")
        else -> return null
    }.substringBefore('?').substringBefore('/').uppercase().filter { it.isLetterOrDigit() }
    return code.takeIf { it.length == 4 }
}

/** A base64 JPEG from the wire, or null if it is not one. */
private fun decodePhoto(base64: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmapCompat()
}.getOrNull()

/** The server writes its refusals in English for the logs; the table reads them in Arabic. */
private fun ServerMessage.Failure.explain(): String = when (code) {
    ErrorCode.ROOM_NOT_FOUND -> "ماكو غرفة بهذا الكود. يمكن خلصت أو تسكّرت"
    ErrorCode.ROOM_FULL -> "الغرفة مليانة"
    ErrorCode.ROOM_ALREADY_STARTED -> "اللعب بدأ بهذي الغرفة"
    ErrorCode.NOT_HOST -> "بس صاحب الغرفة يقدر يسويها"
    ErrorCode.SEAT_TAKEN -> "هذا الكرسي محجوز"
    ErrorCode.NOT_ENOUGH_PLAYERS -> "لازم أربعة، أو كمّلوا ببوتات"
    ErrorCode.VERSION_MISMATCH -> "نسختك قديمة، نزّل النسخة الجديدة من التطبيق"
    else -> message
}

private fun com.teera.core.Rejection.explain(): String = when (this) {
    com.teera.core.Rejection.NOT_YOUR_TURN -> "مو دورك"
    com.teera.core.Rejection.WRONG_PHASE -> "مو وقت هذي الحركة"
    com.teera.core.Rejection.TILE_NOT_IN_HAND -> "هذا البول مو بيدك"
    com.teera.core.Rejection.MUST_OPEN_WITH_A_DOUBLE -> "الگيم يُفتح بچفت"
    com.teera.core.Rejection.MUST_OPEN_WITH_THE_SIX -> "أول گيم يُفتح بالست ست"
    com.teera.core.Rejection.MUST_OPEN_A_FRESH_SIDE -> "لازم تسكّر جهة جديدة من الچفت"
    com.teera.core.Rejection.TILE_DOES_NOT_MATCH -> "البول ما يطابق الطرف"
    com.teera.core.Rejection.NO_ARM_GIVEN -> "اختر الجهة"
    com.teera.core.Rejection.MUST_PLAY_IF_ABLE -> "عندك بول تلعبه"
    com.teera.core.Rejection.CANNOT_PLAY_FOR_THEM -> "ما تقدر تلعب هذا البول"
    com.teera.core.Rejection.TOO_HEAVY_FOR_A_TEERA -> "بيدك أكثر من نقطة وحدة"
}
