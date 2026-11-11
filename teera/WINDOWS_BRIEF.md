# Brief for Antigravity: Teera (طيرة) for Windows

You are adding a **Windows desktop version** of Teera, an Iraqi 4-player, 2-team domino game,
to an existing, working project. Android and the game server are live and played every
night; an iOS app exists. Read this whole brief before writing code.

## 1. The repository

- GitHub: `alialbasseri/Teera`, branch `claude/android-domino-multiplayer-0drvhg`.
- **Work on a new branch `antigravity/windows`** made from that branch. Open a pull request
  back into it when the acceptance checks in section 8 pass. Never push to the base branch
  directly, never force-push, never rewrite history.
- The Gradle project lives in `teera/` (wrapper: `teera/gradlew`). Modules:
  - `core` — **pure Kotlin/JVM**: the rules engine (`Engine`), `Room`, bots (`Bot`), the
    wire format (`Protocol.kt`: `ClientMessage`, `ServerMessage`, `PlayerView`, `RoomInfo`),
    board layout (`BoardLayout.kt`) and tile art (`TileArt.kt`). **Reuse it as is.**
  - `room` — the Ktor game server (do not change).
  - `server` — the deployed server (do not change).
  - `app` — the Android app, Jetpack Compose. Its UI is the model to follow.
  - `ios/` (outside `teera/`) — the SwiftUI app (ignore).
- Rules of the game: `teera/RULES.md` (Arabic). The engine is the referee; never re-implement
  rules in the UI.

## 2. The approach (decided — do not pick another stack)

Build a new Gradle module **`teera/desktop`** with **JetBrains Compose Multiplatform for
Desktop** (Kotlin/JVM), so the Windows app shares `:core` and reuses the Android Compose
screens almost line for line.

- Kotlin is **2.0.21** (root `teera/build.gradle.kts`). Use the JetBrains Compose plugin
  `org.jetbrains.compose` **1.7.3** with `org.jetbrains.kotlin.plugin.compose` 2.0.21.
  Add the plugin to the root `plugins {}` with `apply false`, include `desktop` in
  `teera/settings.gradle.kts`. Add the JetBrains Compose repository if needed.
- `desktop` depends on `project(":core")`, `compose.desktop.currentOs`,
  `compose.material3`, `compose.materialIconsExtended`, `kotlinx-coroutines-swing`,
  and OkHttp 4.12.0 for the WebSocket (same as Android).
- **Do not break the Android build**: `./gradlew :app:assembleDebug` must still work.
  Do not move or rename existing Android files. Copy what you need into `desktop`.
- Packaging: `compose.desktop { application { mainClass = "com.teera.desktop.MainKt";
  nativeDistributions { targetFormats(TargetFormat.Msi, TargetFormat.Exe);
  packageName = "Teera"; packageVersion = "1.0.0"; windows { iconFile, menu = true,
  shortcut = true, upgradeUuid = <a fixed UUID you generate once> } } } }`.
  Make the icon `.ico` from the Android launcher art (`app/src/main/res/...`) or
  `room/src/main/resources/site/icon.png`.

## 3. What to port from the Android app (`teera/app/src/main/kotlin/com/teera/app/`)

Copy into `desktop/src/main/kotlin/com/teera/desktop/` and replace Android-only APIs:

| Android file | Desktop |
|---|---|
| `ui/MenuScreen.kt`, `ui/LobbyScreen.kt`, `ui/TableScreen.kt`, `ui/Seats.kt`, `ui/BoardView.kt`, `ui/DominoTile.kt`, `ui/Faces.kt`, `ui/Furniture.kt`, `ui/Ornament.kt`, `ui/Cafe.kt`, `ui/Steps.kt`, `ui/RulesScreen.kt`, `ui/Changelog.kt`, `ui/theme/*` | Same Compose code. Replace `painterResource(R.drawable.x)` with desktop `painterResource("x.png")` (resources in `desktop/src/main/resources`), `R.font.*` with `Font(resource = "fonts/....ttf")`, remove `Dialog`/`Popup` Android imports in favour of the `androidx.compose.ui.window` desktop ones. |
| `net/GameClient.kt`, `net/WebSocketGameClient.kt` | Same logic on OkHttp (works on the JVM). Remove `android.*` (logging, `Uri`). |
| `net/LocalGameClient.kt` | Same: offline play against bots runs entirely on `:core` (`Room` + `Bot`). |
| `GameViewModel.kt` | A plain class with a `CoroutineScope(SupervisorJob() + Dispatchers.Main)` and `StateFlow<UiState>` — no `AndroidViewModel`. Preferences: `java.util.prefs.Preferences` instead of `SharedPreferences` (keys: player id, name, avatar, mute settings, friends). |
| `sound/SoundEngine.kt` | `javax.sound.sampled.Clip` for effects, a looping `Clip` for music. **Java cannot play Ogg**: convert `app/src/main/res/raw/*.ogg` to WAV (16-bit PCM, 44.1 kHz) once with ffmpeg and commit the WAVs to `desktop/src/main/resources/sounds/`. The soundboard's spoken lines: skip text-to-speech on Windows (sound only), or use none. |
| `sound/Reactions.kt` | Same. |
| Voice chat (`voice/*`), Firebase push (`Push.kt`), screenshot alert, share sheet, photo picker, deep links | **Leave out of version 1.** Where the UI has a button for them, hide it. |

Window: title «طيرة», minimum size 900×640, starts maximized or 1200×800, resizable. The
whole UI is **right-to-left**: wrap the root in
`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)`.
Fonts: copy `app/src/main/res/font/*.ttf` (Aref Ruqaa for the title, El Messiri for text)
and their licences in `app/src/main/assets/licenses/`.
Mouse: a tile is played by clicking; when it fits two ends, the ends light up and you click
one (exactly as touch on Android). Mouse wheel / pinch zooms the board as on Android.

## 4. Talking to the server (must be exact)

- URL: `wss://teera-xfvr.onrender.com/ws` (the free server sleeps: the first connection may
  take up to ~60 s — show «نصحّي السيرفر…» and retry with backoff up to 15 s between tries,
  then rejoin the room with `JoinRoom(code, rejoin = true)` after a drop).
- Encode/decode **only** with `com.teera.core.Protocol` (`Protocol.encode`,
  `Protocol.decodeServer`); messages are wrapped, e.g. `{"t":"room","room":{…}}`,
  `{"t":"view","view":{…}}`. Never hand-write JSON.
- First message: `ClientMessage.Hello(name, playerId, avatar = …, appVersion = 1,
  platform = "windows")`. Bump `appVersion` by one with every Windows release. The server
  then never offers the Android APK to this app.
- `ServerMessage.Failure` with `ErrorCode.INTERNAL` carries plain Arabic words from the
  server (banned, notices): show `message` as is. `UpdateAvailable` is for Android: ignore it.
- Ignore any message type you do not know (the server adds new ones over time).

## 5. Scope of version 1 (must work)

1. Menu: name, avatar from the drawn presets, «العب ضد البوتات» (levels سهل / متوسط / صعب),
   «افتح غرفة», join by code, «تفرّج» (list of tables, ask-to-join for hidden ones),
   «القوانين», «شنو الجديد», «بلّغ أو اقترح» (feedback; picture optional, may skip in v1).
2. Lobby: room name (host can rename), code, seats, «حط بوت», take a seat, choose
   201 / 251 / 10 گيمات, start, watchers list, host swap/kick/lock if simple.
3. Table: everything the Android table has except voice — board, hand (drag to reorder),
   seats with avatars and the 30-second turn ring (`PlayerView.turnMillisLeft`), prompts at the
   **top** (تلعب تنطي؟ with «العب بداله» / «انطيه بول», طيرة offer, خلط offer), score sheet
   on clicking the scores (`PlayerView.history`), soundboard + the 12 animated faces, chat,
   «خاوة» vote, target vote, game summary with «الگيم الجاي», and after the match
   «ارجع للغرفة» (`ClientMessage.BackToLobby`).
4. Offline vs bots works with no internet.

**Design rule from the owner: the app must never help a player count tiles** (no counters of
what has been played, no lists of what remains) during a game.

**Words**: use the Android app's Iraqi wording, never formal Arabic: گيم، چفت، گلى، اكس، طيرة،
قفلت، خلط، يفكّر…، دورك — اختر بول، الگيم الجاي، سد (not «إغلاق» or «سكّر»).

## 6. Build and release on GitHub

Add `.github/workflows/windows-build.yml`: on push to `antigravity/windows` and
`workflow_dispatch`, `runs-on: windows-latest`, JDK 17 (temurin), working directory
`teera`, run `./gradlew :core:test :desktop:packageMsi :desktop:packageExe --no-daemon`,
upload `teera/desktop/build/compose/binaries/main/msi/*.msi` and `.../exe/*.exe` as the
artifact `Teera-Windows`. The installers are unsigned; that is fine for now.

## 7. Do not

- Change anything in `core`, `room` or `server` (if you think the server needs something,
  write it in the PR description instead).
- Add secrets, keys or the Firebase files to the desktop app.
- Commit build outputs, `.idea/`, or large binaries other than the WAV sounds and fonts.

## 8. Done means

- `cd teera && ./gradlew :core:test :desktop:run` opens the game on Windows; a match against
  bots can be played to the end; a room can be opened online and an Android phone can join it
  with the code and play a full match together.
- `./gradlew :app:assembleDebug` still builds.
- The GitHub Actions run on `antigravity/windows` is green and its artifact contains a
  working `.msi` that installs «طيرة» with a Start-menu shortcut.
- The pull request lists what was ported, what was left out, and screenshots of the menu,
  lobby and table.
