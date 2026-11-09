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
import com.teera.app.ui.RulesScreen
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
        Push.start(applicationContext)
        setContent {
            TeeraTheme(dark = true) {
                TeeraApp(sound = sound)
            }
        }
        // Opened from a room's link: but not again when the activity is merely recreated.
        if (savedInstanceState == null) followLink(intent)
    }

    /** Already open, and a room's link is tapped in the chat: this activity is reused. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        followLink(intent)
    }

    private fun followLink(intent: Intent?) {
        // A room's link, or a notification tapped (an invitation, or the table waiting).
        val code = codeFromLink(intent?.dataString)
            ?: intent?.getStringExtra(Push.EXTRA_CODE)?.uppercase()?.filter(Char::isLetterOrDigit)?.take(8)?.ifEmpty { null }
        code?.let(model::openLink)
        intent?.removeExtra(Push.EXTRA_CODE)
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

    private val screenshots = ScreenshotWatch { model.screenCaptured() }

    override fun onStart() {
        super.onStart()
        screenshots.start(this)
        // While the app is open, friends can see it and invitations can reach it.
        model.setForeground(true)
    }

    override fun onStop() {
        screenshots.stop(this)
        // Out of sight, the mic closes: nobody at the table should overhear the room.
        model.pauseVoice()
        model.setForeground(false)
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
    // Notifications (Android 13+): asked once, so invitations reach a closed app.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("dominoes", android.content.Context.MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            System.getProperty("teera.noPresence") != "true" &&
            !prefs.getBoolean("askedNotifications", false) &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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

    var inviting by remember { mutableStateOf(false) }
    LaunchedEffect(state.screen) { if (state.screen != Screen.Lobby) inviting = false }

    // The system photo picker: no permission needed, and the app only sees the one picked.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::choosePhoto)
    }

    /** Draws whoever sits in a seat: their chosen picture, their photo, or their initial. */
    val seatPicture: @Composable (com.teera.core.Seat?, androidx.compose.ui.unit.Dp) -> Unit = { seat, size ->
        when {
            seat == null || seat.playerId.isEmpty() -> com.teera.app.ui.Initial("", size)
            seat.isBot -> com.teera.app.ui.Initial("ب", size)
            else -> com.teera.app.ui.Avatar(seat.avatar, seat.name, size, state.photos[seat.playerId])
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
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
                )

                Screen.Friends -> com.teera.app.ui.FriendsScreen(
                    myCode = state.myCode,
                    friends = state.friends,
                    online = state.online,
                    onAdd = viewModel::addFriend,
                    onForget = viewModel::forget,
                    onShareCode = { shareText(context, "ضيفني بطاولة الدومينا، رمزي: ${state.myCode}") },
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
                    onPickPhoto = {
                        pickPhoto.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onClose = viewModel::closeProfile,
                    myCode = state.myCode,
                    record = state.record,
                    recordLoading = state.recordLoading,
                    recordKept = state.recordKept,
                    onOpenMatch = { id, seat -> viewModel.openMatch(id, seat) },
                    onFelt = viewModel::chooseFelt,
                    onBones = viewModel::chooseBones,
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
                        // One button. With friends who have the app open, it offers them first
                        // as well as WhatsApp; otherwise it goes straight to sharing.
                        onShare = { code ->
                            val seated = room.seats.map { it.playerId }.toSet()
                            if (state.friends.any { it.id in state.online && it.id !in seated }) inviting = true
                            else shareRoomCode(context, code, viewModel.linkFor(code))
                        },
                        talk = talk,
                        avatarFor = seatPicture,
                        onKick = if (state.offline) null else { id: String -> viewModel.kick(id) },
                        onLock = if (state.offline) null else { locked: Boolean -> viewModel.setLocked(locked) },
                    )
                    if (inviting) {
                        com.teera.app.ui.InviteDialog(
                            friends = state.friends,
                            online = state.online,
                            seated = room.seats.map { it.playerId }.toSet(),
                            onInvite = { friend -> viewModel.invite(friend) },
                            onShareLink = { shareRoomCode(context, room.code, viewModel.linkFor(room.code)) },
                            onClose = { inviting = false },
                        )
                    }
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
                        onLock = { locked -> viewModel.setLocked(locked) },
                        blocked = state.blocked.keys,
                        onBlock = { id, name, block -> if (block) viewModel.block(id, name) else viewModel.unblock(id) },
                        onVoteTarget = { yes -> viewModel.voteTarget(yes) },
                        onVoteAlert = { yes -> viewModel.voteAlert(yes) },
                        steps = state.steps,
                    )
                }
            }

            // Somebody else took a screenshot of the table: a camera's flash, briefly.
            val flash = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(state.screenshots) {
                if (state.screenshots > 0) {
                    flash.snapTo(0.75f)
                    flash.animateTo(0f, androidx.compose.animation.core.tween(700))
                }
            }
            if (flash.value > 0f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
            }

            // A newer app is out: one press downloads it.
            state.update?.let { update ->
                val context = LocalContext.current
                com.teera.app.ui.UpdateDialog(
                    update,
                    onDownload = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.url)))
                    },
                    onLater = viewModel::updateLater,
                )
            }

            // An invitation can arrive on any screen.
            state.invite?.let { invite ->
                com.teera.app.ui.InvitedDialog(invite, onAccept = viewModel::acceptInvite, onLater = viewModel::dismissInvite)
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

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** Hands the room's link to WhatsApp, Telegram or whatever the phone offers. */
private fun shareRoomCode(context: Context, code: String, link: String?) {
    val text = if (link != null) {
        "تعال نلعب 🎲\nدوس هنا وتدخل الغرفة مباشرة:\n$link\n\n(أو افتح التطبيق واكتب الكود: $code)"
    } else {
        "تعال نلعب 🎲\nكود الغرفة: $code"
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "دزّ اللنك"))
}
