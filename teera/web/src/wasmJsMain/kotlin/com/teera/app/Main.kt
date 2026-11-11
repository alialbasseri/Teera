package com.teera.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import com.teera.app.net.Link
import com.teera.app.sound.SoundEngine
import com.teera.app.ui.LobbyScreen
import com.teera.app.ui.MenuScreen
import com.teera.app.ui.RulesScreen
import com.teera.app.ui.SoundSettings
import com.teera.app.ui.TableScreen
import com.teera.app.ui.theme.Felt
import com.teera.app.ui.theme.Messiri
import com.teera.app.ui.theme.TeeraTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private fun jsShare(text: String): Boolean =
    js("(() => { if (navigator.share) { navigator.share({ text: text }).catch(() => {}); return true; } return false; })()")
private fun jsOpen(url: String): Unit = js("{ window.open(url, '_blank'); }")
private fun jsHideLoading(): Unit = js("{ const e = document.getElementById('loading'); if (e) e.remove(); }")
private fun jsPageQuery(name: String): String? = js("new URLSearchParams(location.search).get(name)")
private fun jsOnHidden(onChange: (Boolean) -> Unit): Unit =
    js("{ document.addEventListener('visibilitychange', () => onChange(document.hidden)); }")

/** The phone's share sheet in a mobile browser; false where there is none. */
fun share(text: String): Boolean = jsShare(text)

fun openPage(url: String) = jsOpen(url)

/** Teera in the browser: the fonts and faces first, then the same screens as everywhere. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        WebAssets.load()
        val app = WebApp()
        val model = GameViewModel(app)
        val sound = SoundEngine(app.prefs)
        // A room's link opens straight into the room: /play?room=CODE
        jsPageQuery("room")?.let { code -> codeFromLink("teera://join/$code")?.let(model::openLink) }
        model.setForeground(true)
        jsOnHidden { hidden ->
            model.setForeground(!hidden)
            if (hidden) {
                model.pauseVoice()
                sound.stopMusic()
            } else if (model.state.value.screen == Screen.Table) {
                sound.startMusic()
            }
        }
        jsHideLoading()
        CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "طيرة") {
            // Skia here has no fonts of its own: the game's font, and the emoji, stand in for
            // anything missing. Nothing is drawn until they are in, so no word is laid out without them.
            val fonts = LocalFontFamilyResolver.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                runCatching { fonts.preload(Messiri) }
                WebAssets.fallbacks.forEach { name ->
                    runCatching {
                        fonts.preload(androidx.compose.ui.text.font.FontFamily(appFont(name, androidx.compose.ui.text.font.FontWeight.Normal)))
                    }
                }
                ready = true
            }
            if (!ready) return@CanvasBasedWindow
            Box(
                Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape && BackStack.back()
                },
            ) {
                TeeraTheme(dark = true) {
                    TeeraApp(sound, model)
                }
            }
        }
    }
}

@Composable
fun TeeraApp(sound: SoundEngine?, viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    /** The phone's share sheet where the browser has one; otherwise the clipboard. */
    fun copy(text: String, said: String = "انسخ. الصقه بالواتساب أو وين ما تريد") {
        if (share(text)) return
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbars.showSnackbar(said) }
    }

    fun shareRoomCode(code: String, link: String?) = copy(
        if (link != null) {
            "تعال نلعب 🎲\nدوس هنا وتدخل الغرفة مباشرة:\n$link\n\n(أو افتح التطبيق واكتب الكود: $code)"
        } else {
            "تعال نلعب 🎲\nكود الغرفة: $code"
        },
        said = "انسخ لنك الغرفة. الصقه لجماعتك بالواتساب",
    )

    // Table talk through the computer's microphone and speakers.
    val micOn by viewModel.voice.micOn.collectAsState()
    val talking by viewModel.voice.talking.collectAsState()
    val hearing by viewModel.voice.hearing.collectAsState()
    val speaking by viewModel.voice.speaking.collectAsState()
    val talk = com.teera.app.ui.Talk(
        micOn = micOn,
        talking = talking,
        hearing = hearing,
        speaking = speaking,
        onMic = { viewModel.setMic(!micOn) },
        onHearing = { viewModel.setHearing(!hearing) },
    ).takeUnless { state.offline }

    // Tables on the Wi-Fi are only looked for while somebody could pick one.
    LaunchedEffect(state.screen) { viewModel.lookForWifiTables(state.screen == Screen.Menu) }

    var effectsOn by remember { mutableStateOf(sound?.effectsOn ?: false) }
    var musicOn by remember { mutableStateOf(sound?.musicOn ?: false) }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbars.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    LaunchedEffect(sound) {
        val engine = sound ?: return@LaunchedEffect
        viewModel.reactions.collect { reaction ->
            reaction.cues.forEach(engine::play)
            reaction.lines.forEach { engine.say(it.key, it.text) }
            reaction.mood?.let(engine::setMood)
        }
    }

    LaunchedEffect(state.screen, sound) {
        if (state.screen == Screen.Table) sound?.startMusic() else sound?.stopMusic()
    }

    BackPress(enabled = state.screen != Screen.Menu) { viewModel.back() }

    var inviting by remember { mutableStateOf(false) }
    LaunchedEffect(state.screen) { if (state.screen != Screen.Lobby) inviting = false }

    val seatPicture: @Composable (com.teera.core.Seat?, androidx.compose.ui.unit.Dp) -> Unit = { seat, size ->
        when {
            seat == null || seat.playerId.isEmpty() -> com.teera.app.ui.Initial("", size)
            seat.isBot -> com.teera.app.ui.Initial("ب", size)
            else -> com.teera.app.ui.Avatar(seat.avatar, seat.name, size, state.photos[seat.playerId])
        }
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                Screen.Menu -> MenuScreen(
                    playerName = state.playerName,
                    onNameChange = viewModel::setName,
                    onHost = viewModel::hostOnline,
                    onJoin = viewModel::join,
                    onSolo = { level -> viewModel.playOffline(level) },
                    connecting = state.connecting,
                    onHostWifi = viewModel::hostOnWifi,
                    wifiTables = state.wifiTables,
                    onJoinWifi = viewModel::joinWifiTable,
                    onJoinAddress = viewModel::joinAddress,
                    hasInternetServer = state.hasInternetServer,
                    waking = state.link == Link.WAKING,
                    lastRoom = state.lastRoom,
                    onRules = viewModel::showRules,
                    onProfile = viewModel::showProfile,
                    onRejoin = viewModel::rejoin,
                    onFriends = viewModel::showFriends,
                    onFeedback = { kind, text, image -> viewModel.sendFeedback(kind, text, image) },
                    onLive = viewModel::showLive,
                    onRanking = viewModel::showRanking,
                    liveCount = state.liveGames.size,
                    friendsOnline = state.friends.count { it.id in state.online },
                    avatar = { size ->
                        com.teera.app.ui.Avatar(state.avatar, state.playerName, size, state.photos[state.myId])
                    },
                )

                Screen.Rules -> RulesScreen(onClose = viewModel::closeRules, onTutorial = viewModel::showTutorial)

                Screen.Tutorial -> com.teera.app.ui.TutorialScreen(
                    onTry = viewModel::tryFromTutorial,
                    onClose = viewModel::closeTutorial,
                )

                Screen.Ranking -> com.teera.app.ui.RankingScreen(
                    board = state.leaderboard,
                    loading = state.leaderboardLoading,
                    kept = state.recordKept,
                    myId = state.myId,
                    onClose = viewModel::closeRanking,
                    tournaments = state.tournaments.values.sortedByDescending { it.createdAtEpochMs },
                    onOpenTournament = viewModel::openTournament,
                    onCreateTournament = viewModel::createTournament,
                )

                Screen.Tournament -> com.teera.app.ui.TournamentScreen(
                    tournament = state.tournamentId?.let { state.tournaments[it] },
                    myId = state.myId,
                    onPlay = { round, index -> state.tournamentId?.let { viewModel.playTournamentMatch(it, round, index) } },
                    onWinner = { round, index, team -> state.tournamentId?.let { viewModel.setTournamentWinner(it, round, index, team) } },
                    onShare = { t ->
                        copy("بطولة طيرة: ${t.name} 🏆\nافتح طيرة ← الكأس ← البطولات، واكتب الكود: ${t.id}\n\nما عندك اللعبة؟ ${viewModel.linkFor("")?.substringBefore("/j/") ?: "https://teera-xfvr.onrender.com"}/download")
                    },
                    onClose = viewModel::closeTournament,
                )

                Screen.Match -> com.teera.app.ui.MatchScreen(
                    match = state.match,
                    loading = state.matchLoading,
                    mySeat = state.matchSeat,
                    onClose = viewModel::closeMatch,
                )

                Screen.Live -> com.teera.app.ui.LiveGamesScreen(
                    games = state.liveGames,
                    onWatch = viewModel::watchLive,
                    onClose = viewModel::closeLive,
                    onAsk = viewModel::askToJoin,
                    asked = state.askedRooms,
                )

                Screen.Friends -> com.teera.app.ui.FriendsScreen(
                    myCode = state.myCode,
                    friends = state.friends,
                    online = state.online,
                    onAdd = viewModel::addFriend,
                    onForget = viewModel::forget,
                    onShareCode = { copy("ضيفني بطاولة الدومينا، رمزي: ${state.myCode}", said = "انسخ رمزك. دزّه لصديقك") },
                    onClose = viewModel::closeFriends,
                    blocked = state.blocked,
                    onUnblock = viewModel::unblock,
                )

                Screen.Profile -> com.teera.app.ui.ProfileScreen(
                    name = state.playerName,
                    avatar = state.avatar,
                    photo = state.photos[state.myId],
                    onName = viewModel::setName,
                    onPreset = viewModel::choosePreset,
                    onPickPhoto = { pickImage(viewModel::choosePhoto) },
                    onClose = viewModel::closeProfile,
                    myCode = state.myCode,
                    record = state.record,
                    recordLoading = state.recordLoading,
                    recordKept = state.recordKept,
                    onOpenMatch = { id, seat -> viewModel.openMatch(id, seat) },
                    onFelt = viewModel::chooseFelt,
                    onBones = viewModel::chooseBones,
                    reminders = state.reminders,
                    onReminders = viewModel::setReminders,
                    onRanking = viewModel::showRanking,
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
                        onShare = { code ->
                            val seated = room.seats.map { it.playerId }.toSet()
                            if (state.friends.any { it.id !in seated }) inviting = true
                            else shareRoomCode(code, viewModel.linkFor(code))
                        },
                        talk = talk,
                        avatarFor = seatPicture,
                        onKick = if (state.offline) null else { id: String -> viewModel.kick(id) },
                        onSwap = if (state.offline) null else { seated: String, watcher: String -> viewModel.swap(seated, watcher) },
                        onRename = if (state.offline) null else { name: String -> viewModel.renameRoom(name) },
                        onLock = if (state.offline) null else { locked: Boolean -> viewModel.setLocked(locked) },
                    )
                    if (inviting) {
                        com.teera.app.ui.InviteDialog(
                            friends = state.friends,
                            online = state.online,
                            seated = room.seats.map { it.playerId }.toSet(),
                            onInvite = { friend -> viewModel.invite(friend) },
                            onShareLink = { shareRoomCode(room.code, viewModel.linkFor(room.code)) },
                            onClose = { inviting = false },
                        )
                    }
                }

                Screen.Table -> state.view?.let { view ->
                    TableScreen(
                        view = view,
                        onMove = viewModel::play,
                        onNextGame = viewModel::nextGame,
                        onBackToLobby = viewModel::backToLobby,
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
                        avatarFor = { seat, size -> seatPicture(view.seatInfo(seat), size) },
                        onRaiseAlert = viewModel::raiseAlert.takeUnless { state.offline || view.spectating }?.let { raise -> { raise() } },
                        chat = state.chat,
                        onChat = viewModel::chat.takeUnless { state.offline }?.let { send -> { text: String -> send(text) } },
                        onShowHand = viewModel::showHand,
                        onWatch = viewModel::watch,
                        onStandIn = { seat -> viewModel.standIn(seat) },
                        onProposeTarget = { target -> viewModel.proposeTarget(target) },
                        onListed = { listed -> viewModel.setListed(listed) },
                        myId = state.myId,
                        isHost = state.isHost,
                        locked = state.room?.locked == true,
                        onKick = { id -> viewModel.kick(id) },
                        onSwap = { seated, watcher -> viewModel.swap(seated, watcher) },
                        onFeedback = if (state.offline) null else { kind: String, text: String, image: Picture? -> viewModel.sendFeedback(kind, text, image) },
                        onLock = { locked -> viewModel.setLocked(locked) },
                        blocked = state.blocked.keys,
                        onBlock = { id, name, block -> if (block) viewModel.block(id, name) else viewModel.unblock(id) },
                        onVoteTarget = { yes -> viewModel.voteTarget(yes) },
                        onVoteAlert = { yes -> viewModel.voteAlert(yes) },
                        steps = state.steps,
                    )
                }
            }

            // The server offers the phone's APK to phones only; the web is always the newest.
            state.update?.let { update ->
                com.teera.app.ui.UpdateDialog(
                    update,
                    onDownload = { openPage(update.url) },
                    onLater = viewModel::updateLater,
                )
            }

            state.joinAsks.firstOrNull()?.let { ask ->
                com.teera.app.ui.JoinAskDialog(
                    ask,
                    onYes = { viewModel.answerJoin(ask.playerId, true) },
                    onNo = { viewModel.answerJoin(ask.playerId, false) },
                )
            }
            state.announcement?.let { words ->
                com.teera.app.ui.AnnouncementDialog(words, onClose = viewModel::dismissAnnouncement)
            }
            state.invite?.let { invite ->
                com.teera.app.ui.InvitedDialog(invite, onAccept = viewModel::acceptInvite, onLater = viewModel::dismissInvite)
            }

            SnackbarHost(snackbars, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))

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
