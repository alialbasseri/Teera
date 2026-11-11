package com.teera.room

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.request.host
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Room links: what a friend taps in the group chat.
 *
 * `/j/KHAN` is a room's address. On a phone with the app, Android opens the app straight
 * into the room - it checks `/.well-known/assetlinks.json` to be sure the link and the app
 * belong together. Where it has not checked yet, the link opens this little page, whose
 * button hands the code to the app instead.
 */
fun Route.roomLinks(live: () -> LiveCount = { LiveCount(0, 0) }) {
    webApp()
    get("/.well-known/assetlinks.json") {
        call.respondText(ASSET_LINKS, ContentType.Application.Json)
    }

    // What friends are sent to get the game: the game's own page, always on the newest build.
    get("/") {
        call.respondText(landingPage(siteBase(call.request.host()), live()), ContentType.Text.Html)
    }
    get("/download") {
        call.respondText(landingPage(siteBase(call.request.host()), live()), ContentType.Text.Html)
    }
    get("/site/{file}") {
        val name = call.parameters["file"].orEmpty()
        val bytes = siteFile(name)
        if (bytes == null) {
            call.respondText("Not Found", status = io.ktor.http.HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(io.ktor.http.HttpHeaders.CacheControl, "public, max-age=86400")
        val type = if (name.endsWith(".png")) ContentType.Image.PNG else ContentType.Image.JPEG
        call.respondBytes(bytes, type)
    }
    get("/teera.apk") {
        call.respondRedirect(APK_URL)
    }
    get("/windows") {
        call.respondRedirect(WindowsDownload.url())
    }
    get("/ios") {
        call.respondText(iosPage(siteBase(call.request.host())), ContentType.Text.Html)
    }
    get("/ios.ipa") {
        call.respondRedirect(IOS_URL)
    }

    get("/j/{code}") {
        val code = call.parameters["code"].orEmpty().uppercase().filter { it.isLetterOrDigit() }.take(8)
        call.respondText(joinPage(code), ContentType.Text.Html)
    }
}

/**
 * The fingerprint of the key the app is signed with (app/signing), for the two package
 * names a build can carry. Android compares it with the installed app before letting a
 * link open it.
 */
private const val FINGERPRINT =
    "B8:DE:1E:43:0A:91:A3:A3:2C:2E:E3:88:AD:2D:0A:62:81:50:11:A9:6A:77:EA:A5:4B:29:A4:73:D9:7E:42:EC"

private val ASSET_LINKS = listOf("com.teera.app", "com.teera.app.debug").joinToString(",", "[", "]") { pkg ->
    """{"relation":["delegate_permission/common.handle_all_urls"],""" +
        """"target":{"namespace":"android_app","package_name":"$pkg","sha256_cert_fingerprints":["$FINGERPRINT"]}}"""
}

private fun joinPage(code: String): String {
    // The app answers teera://join/CODE; Chrome's intent form opens it, or stays on this page.
    val intent = "intent://join/$code#Intent;scheme=teera;end"
    return """<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta property="og:title" content="تعال نلعب — الغرفة $code">
<meta property="og:description" content="دوس وادخل الغرفة مباشرة">
<title>الغرفة $code</title>
<style>
  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
       background:radial-gradient(circle at 50% 30%,#4a3020,#1e1309 70%);color:#fbf7ef;
       font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;text-align:center}
  .card{padding:28px 24px;max-width:360px}
  .code{font-size:56px;letter-spacing:6px;color:#d9b166;font-weight:800;margin:8px 0 18px;direction:ltr}
  a.go{display:block;padding:16px;border-radius:16px;font-size:20px;font-weight:700;text-decoration:none;
       color:#14110c;background:linear-gradient(#f0d79a,#d9b166,#a67c2e)}
  a.web{display:block;margin-top:12px;padding:12px;border-radius:14px;font-size:16px;text-decoration:none;
       color:#f0d79a;border:1px solid #d9b166}
  p{color:#a79274;line-height:1.7}
</style>
</head>
<body>
<div class="card">
  <p>جماعتك يعزمونك على الطاولة</p>
  <div class="code">$code</div>
  <a class="go" id="go" href="$intent">ادخل الغرفة</a>
  <a class="web" id="web" href="/play/?room=$code">🌐 العب من المتصفح، بدون تنزيل</a>
  <p id="how">إذا ما انفتح التطبيق: افتحه، ودوس «العب ويا جماعتك»، واكتب الكود.</p>
  <p><a href="/download" style="color:#d9b166">ما عندك اللعبة؟ نزّلها من هنا</a></p>
</div>
<script>
// Android opens the app straight into the room. Anywhere else (iPhone, a computer) the
// browser is the quickest way in, so it becomes the main button.
if (/Android/.test(navigator.userAgent)) {
  setTimeout(function(){location.href="$intent"},300)
} else {
  var go = document.getElementById("go"), web = document.getElementById("web");
  go.href = web.href; go.textContent = "ادخل الغرفة من المتصفح";
  web.href = "/ios"; web.textContent = "عندك آيفون؟ شلون تنصّب اللعبة";
  document.getElementById("how").textContent = "إذا عندك اللعبة: افتحها، ودوس «العب ويا جماعتك»، واكتب الكود.";
}
</script>
</body>
</html>"""
}

/**
 * What the server knows of the newest build. It starts from the constants below and
 * then reads [VERSION_URL] every few minutes, so putting a new teera.apk and its
 * version.json in the repository is enough: no server restart, no closed rooms.
 */
object LatestApp {
    @Volatile var code: Int = LATEST_APP_VERSION
        private set
    @Volatile var name: String = LATEST_APP_NAME
        private set
    @Volatile var minimum: Int = MIN_APP_VERSION
        private set
    /** What the newest build brought, for the game's page; empty until version.json says. */
    @Volatile var notes: List<String> = emptyList()
        private set

    @kotlinx.serialization.Serializable
    private data class Build(val code: Int, val name: String, val min: Int = 0, val notes: List<String> = emptyList())

    /** Takes in what version.json says; false if it could not be read. */
    fun update(json: String): Boolean {
        val build = runCatching { com.teera.core.Protocol.json.decodeFromString(Build.serializer(), json) }.getOrNull()
            ?: return false
        if (build.code <= 0 || build.name.isBlank() || build.name.length > 16) return false
        code = build.code
        name = build.name
        minimum = build.min
        notes = build.notes.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 300 }.take(12)
        return true
    }

    /** Back to the built-in values; for tests. */
    fun reset() {
        code = LATEST_APP_VERSION
        name = LATEST_APP_NAME
        minimum = MIN_APP_VERSION
        notes = emptyList()
    }
}

/** The newest build's number and name, next to the build itself. */
const val VERSION_URL =
    "https://raw.githubusercontent.com/alialbasseri/johnny-five/claude/android-domino-multiplayer-0drvhg/teera/downloads/version.json"

/** The build at [APK_URL] when the server was last deployed; [LatestApp] keeps up after that. */
const val LATEST_APP_VERSION = 30
const val LATEST_APP_NAME = "1.8.2"

/**
 * The oldest build allowed at a table: 1.4.1 and later show the update window, so they hear
 * of every new build. Earlier ones send no build number (0) and are turned away.
 */
const val OLDEST_PLAYABLE_APP = 1

/** Apps older than this are told they must update, not just that they may. */
const val MIN_APP_VERSION = 0

/**
 * The latest build, kept in the repository at a fixed path so this address never changes:
 * putting a new teera.apk there is all a release needs.
 */
const val APK_URL =
    "https://raw.githubusercontent.com/alialbasseri/johnny-five/claude/android-domino-multiplayer-0drvhg/teera/downloads/teera.apk"

/** The Windows installer known to exist, for when GitHub cannot be asked. */
const val WINDOWS_URL =
    "https://github.com/alialbasseri/Teera/releases/download/windows-v1.0.0/Teera-1.0.0.msi"

/**
 * Where /windows sends people: the .msi of the Windows version the code says is current
 * (windowsVersion in teera/desktop/build.gradle.kts, read like [VERSION_URL]), once its
 * release exists on GitHub. Checked at most every ten minutes, so a new build is offered
 * without touching the server, and the link never points at a release that is not there.
 */
object WindowsDownload {
    private const val BUILD_FILE =
        "https://raw.githubusercontent.com/alialbasseri/johnny-five/claude/android-domino-multiplayer-0drvhg/teera/desktop/build.gradle.kts"
    private const val EVERY_MS = 10 * 60_000L

    @Volatile private var cached: String = WINDOWS_URL
    @Volatile private var checkedAt = 0L

    fun url(now: Long = System.currentTimeMillis()): String {
        if (now - checkedAt > EVERY_MS) {
            checkedAt = now
            current()?.let { cached = it }
        }
        return cached
    }

    /** The current version's installer, if its release is up; null otherwise. */
    private fun current(): String? = runCatching {
        val version = versionIn(fetch(BUILD_FILE)) ?: return null
        val url = msiFor(version)
        val probe = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        probe.instanceFollowRedirects = false
        probe.requestMethod = "HEAD"
        probe.connectTimeout = 4_000
        probe.readTimeout = 4_000
        // GitHub answers a file that exists with a redirect to its storage.
        url.takeIf { probe.responseCode in 200..399 }
    }.getOrNull()

    private fun fetch(url: String): String {
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }

    fun msiFor(version: String) = "https://github.com/alialbasseri/Teera/releases/download/windows-v$version/Teera-$version.msi"

    /** windowsVersion = "1.1.0" in the desktop build file. */
    fun versionIn(buildFile: String): String? =
        Regex("""val\s+windowsVersion\s*=\s*"([0-9.]+)"""").find(buildFile)?.groupValues?.get(1)

    /** From GitHub's list of releases: the .msi of the highest windows-v<version>. */
    fun newestMsi(releasesJson: String): String? {
        val releases = com.teera.core.Protocol.json.parseToJsonElement(releasesJson) as? kotlinx.serialization.json.JsonArray ?: return null
        return releases.mapNotNull { element ->
            val release = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val tag = (release["tag_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            val version = tag.removePrefix("windows-v").takeIf { tag.startsWith("windows-v") }
                ?.split('.')?.map { it.toIntOrNull() ?: return@mapNotNull null } ?: return@mapNotNull null
            val msi = (release["assets"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("browser_download_url") as? kotlinx.serialization.json.JsonPrimitive }
                .map { it.content }
                .firstOrNull { it.endsWith(".msi") } ?: return@mapNotNull null
            version to msi
        }.maxWithOrNull { a, b ->
            a.first.zip(b.first).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: a.first.size.compareTo(b.first.size)
        }?.second
    }
}

/**
 * The iPhone build, unsigned (Sideloadly signs it with the player's own Apple ID), published
 * by .github/workflows/ios-build.yml under a fixed tag so this address never changes.
 */
const val IOS_URL =
    "https://github.com/alialbasseri/Teera/releases/download/ios-latest/Teera.ipa"
