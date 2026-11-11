package com.teera.room

/** What is happening on the server right now, for the top of the developer's page. */
@kotlinx.serialization.Serializable
data class LiveNow(val online: Int, val rooms: Int, val playing: Int, val watchers: Int, val peakToday: Int)

private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private val adminTemplate: String by lazy {
    LiveNow::class.java.getResourceAsStream("/admin/index.html")!!.use { it.readBytes().decodeToString() }
}

/**
 * The developer's page: the dashboard, the live rooms, the players, the problems and
 * suggestions sent in, and the server's controls. Drawn in the browser from [overview],
 * then kept fresh from /admin/api.
 */
fun adminPage(overview: AdminOverview): String {
    // Inside a <script>: a "</" in a name must not end it.
    val data = com.teera.core.Protocol.json.encodeToString(AdminOverview.serializer(), overview)
        .replace("</", "<\\/")
    return adminTemplate
        .replace("/*INIT*/null", data)
        .replace("{{LATEST}}", LatestApp.code.toString())
}

/** Why the developer's page is not shown yet. */
enum class AdminGate { ASK, WRONG, NOT_SET, TOO_SHORT }

/**
 * The door to the developer's page: a password box, and a plain word on what is wrong.
 * It never shows the password, only whether one is set in Render.
 */
fun adminGatePage(gate: AdminGate): String {
    val note = when (gate) {
        AdminGate.ASK -> ""
        AdminGate.WRONG -> "<p class=\"bad\">الباسورد غلط. انتبه للحروف الكبيرة والصغيرة والمسافات.</p>"
        AdminGate.NOT_SET -> "<p class=\"bad\">السيرفر ما لگى <b>ADMIN_KEY</b>. بـ Render ← Environment ضيفه، وبعدين اختار <b>Save and deploy</b> (مو Save only)، وانتظر لين يخلص النشر.</p>"
        AdminGate.TOO_SHORT -> "<p class=\"bad\">الباسورد اللي بـ Render أقصر من 6 حروف. طوّله وسوي Save and deploy.</p>"
    }
    val box = if (gate == AdminGate.NOT_SET || gate == AdminGate.TOO_SHORT) "" else """
<form method="get" action="/admin">
 <input type="password" name="key" placeholder="الباسورد" autocomplete="current-password" autofocus>
 <button type="submit">ادخل</button>
</form>"""
    return """<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex">
<title>طيرة · الأرقام</title>
<style>
 body{margin:0;background:#1e1309;color:#fbf7ef;font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;padding:16px}
 h1{color:#d9b166;margin:4px 0 16px}
 form{display:flex;flex-direction:column;gap:10px;max-width:360px}
 input{font-size:18px;padding:12px;border-radius:10px;border:1px solid #5a4422;background:#2b1c10;color:#fbf7ef}
 button{font-size:18px;padding:12px;border-radius:10px;border:0;background:#d9b166;color:#1e1309;font-weight:bold}
 .bad{color:#ff9b8a;line-height:1.7;max-width:420px}
</style></head><body>
<h1>طيرة · الأرقام</h1>
$note
$box
</body></html>"""
}
