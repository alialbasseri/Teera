package com.teera.room

import io.ktor.http.ContentType
import io.ktor.server.application.call
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
fun Route.roomLinks() {
    get("/.well-known/assetlinks.json") {
        call.respondText(ASSET_LINKS, ContentType.Application.Json)
    }

    // What friends are sent to get the game: a page with the button and how to install.
    get("/download") {
        call.respondText(downloadPage(), ContentType.Text.Html)
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

    @kotlinx.serialization.Serializable
    private data class Build(val code: Int, val name: String, val min: Int = 0)

    /** Takes in what version.json says; false if it could not be read. */
    fun update(json: String): Boolean {
        val build = runCatching { com.teera.core.Protocol.json.decodeFromString(Build.serializer(), json) }.getOrNull()
            ?: return false
        if (build.code <= 0 || build.name.isBlank() || build.name.length > 16) return false
        code = build.code
        name = build.name
        minimum = build.min
        return true
    }

    /** Back to the built-in values; for tests. */
    fun reset() {
        code = LATEST_APP_VERSION
        name = LATEST_APP_NAME
        minimum = MIN_APP_VERSION
    }
}

/** The newest build's number and name, next to the build itself. */
const val VERSION_URL =
    "https://raw.githubusercontent.com/alialbasseri/johnny-five/claude/android-domino-multiplayer-0drvhg/teera/downloads/version.json"

/** The build at [APK_URL] when the server was last deployed; [LatestApp] keeps up after that. */
const val LATEST_APP_VERSION = 26
const val LATEST_APP_NAME = "1.7.2"

/** Apps older than this are told they must update, not just that they may. */
const val MIN_APP_VERSION = 0

/**
 * The latest build, kept in the repository at a fixed path so this address never changes:
 * putting a new teera.apk there is all a release needs.
 */
const val APK_URL =
    "https://raw.githubusercontent.com/alialbasseri/johnny-five/claude/android-domino-multiplayer-0drvhg/teera/downloads/teera.apk"

private fun downloadPage() = """<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta property="og:title" content="طيرة — دومنة عراقية ويا جماعتك">
<meta property="og:description" content="نزّل اللعبة والعب ويا جماعتك أونلاين">
<title>نزّل طيرة</title>
<style>
  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
       background:radial-gradient(circle at 50% 30%,#4a3020,#1e1309 70%);color:#fbf7ef;
       font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;text-align:center}
  .card{padding:28px 24px;max-width:380px}
  h1{font-size:64px;color:#d9b166;margin:0 0 4px}
  a.go{display:block;padding:16px;border-radius:16px;font-size:20px;font-weight:700;text-decoration:none;
       color:#14110c;background:linear-gradient(#f0d79a,#d9b166,#a67c2e);margin:18px 0}
  p,li{color:#a79274;line-height:1.8}
  ol{text-align:right;padding-right:20px;margin:0}
</style>
</head>
<body>
<div class="card">
  <h1>طيرة</h1>
  <p>دومنة عراقية، أربعة لاعبين، فريقين. العب ويا جماعتك من كل مكان.</p>
  <a class="go" href="$APK_URL">نزّل اللعبة</a>
  <p>النسخة ${LatestApp.name}</p>
  <ol>
    <li>دوس «نزّل اللعبة» وانتظر يخلص التنزيل.</li>
    <li>افتح الملف <b>teera.apk</b> من الإشعارات أو من التنزيلات.</li>
    <li>إذا سألك التليفون، اسمح بالتثبيت من هذا المصدر، وكمّل.</li>
    <li>إذا عندك نسخة قديمة، تنزل فوگها وتبقى بياناتك.</li>
  </ol>
  <p>للأندرويد بس.</p>
</div>
</body>
</html>"""
