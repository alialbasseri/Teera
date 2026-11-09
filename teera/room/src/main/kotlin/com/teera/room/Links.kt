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
  p{color:#a79274;line-height:1.7}
</style>
</head>
<body>
<div class="card">
  <p>جماعتك يعزمونك على الطاولة</p>
  <div class="code">$code</div>
  <a class="go" href="$intent">ادخل الغرفة</a>
  <p>إذا ما انفتح التطبيق: افتحه، ودوس «العب ويا جماعتك»، واكتب الكود.</p>
  <p><a href="/download" style="color:#d9b166">ما عندك اللعبة؟ نزّلها من هنا</a></p>
</div>
<script>setTimeout(function(){location.href="$intent"},300)</script>
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
const val LATEST_APP_VERSION = 27
const val LATEST_APP_NAME = "1.7.3"

/** Apps older than this are told they must update, not just that they may. */
const val MIN_APP_VERSION = 0

/**
 * The latest build, kept in the repository at a fixed path so this address never changes:
 * putting a new teera.apk there is all a release needs.
 */
const val APK_URL =
    "https://raw.githubusercontent.com/alialbasseri/johnny-five/claude/android-domino-multiplayer-0drvhg/teera/downloads/teera.apk"
