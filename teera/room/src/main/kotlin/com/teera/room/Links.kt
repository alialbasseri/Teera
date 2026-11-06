package com.teera.room

import io.ktor.http.ContentType
import io.ktor.server.application.call
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
</div>
<script>setTimeout(function(){location.href="$intent"},300)</script>
</body>
</html>"""
}
