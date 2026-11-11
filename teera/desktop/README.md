# Teera for Windows

The desktop app is the Android app's own code, run with Compose for Desktop on the JVM.

- **Shared, not copied.** `desktop/build.gradle.kts` compiles `app/src/main/kotlin` directly,
  every file except the few that only make sense on a phone (`phoneOnly` in that file). A change
  to a screen or to `GameViewModel` reaches Windows with the next build.
- **What differs** is answered in one place per platform: `app/.../Platform.kt` on Android and
  `desktop/.../DesktopPlatform.kt` here (settings, fonts, faces, pictures, back button, version).
  The desktop's other own files: `Main.kt` (the window), `DesktopSound.kt` (javax.sound, WAV),
  `DesktopPrefs.kt` (`%APPDATA%\Teera\settings.properties`), table talk through the computer's
  mic and speakers (`DesktopVoice.kt`, same packets as the phone), and finding tables on the
  Wi-Fi with JmDNS (`DesktopLan.kt`, the same DNS-SD service phones announce).
- **Sounds** are WAV copies of `app/src/main/res/raw/*.ogg` (Java cannot play Ogg). After
  changing a sound on Android, convert it again:
  `ffmpeg -i app/src/main/res/raw/X.ogg -ac 1 -ar 44100 -c:a pcm_s16le desktop/src/main/resources/sounds/X.wav`
- **Faces, fonts and licences** come from the Android folders at build time.
- **Server**: `Hello` says `platform = "windows"` and `appVersion = windowsBuild` (in
  `build.gradle.kts`). Bump `windowsBuild` and `windowsVersion` together for every release.

## Run and build

```
cd teera
gradlew.bat :desktop:run            # play it
gradlew.bat :desktop:test           # two whole games against bots, with the mouse
gradlew.bat :desktop:packageMsi     # the installer, in desktop/build/compose/binaries/main/msi
```

`-Pteera.live` adds a test that opens a room on the real server and joins it as a phone.

GitHub Actions (`.github/workflows/windows-build.yml`) builds the `.msi` and `.exe` on Windows
and publishes them as the release `windows-v<version>`.
