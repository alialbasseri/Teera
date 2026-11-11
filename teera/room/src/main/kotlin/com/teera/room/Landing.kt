package com.teera.room

import java.util.concurrent.ConcurrentHashMap

/** How busy the game is right now, for the line under the download button. */
data class LiveCount(val online: Int, val playing: Int)

private fun esc(text: String) =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** The address the page's share picture is read from: phones fetch it without the page. */
internal fun siteBase(host: String): String {
    val clean = host.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' }
    val local = clean.startsWith("localhost") || clean.startsWith("127.") || clean.startsWith("10.") || clean.startsWith("192.168.")
    return (if (local) "http://" else "https://") + clean
}

private val siteCache = ConcurrentHashMap<String, ByteArray>()
private val siteName = Regex("[a-z0-9-]+\\.(jpg|png|html)")

/** A file of the game's page, from the server's own resources; null if there is no such file. */
internal fun siteFile(name: String): ByteArray? {
    if (!siteName.matches(name) || name.endsWith(".html")) return null
    siteCache[name]?.let { return it }
    val bytes = LatestApp::class.java.getResourceAsStream("/site/$name")?.use { it.readBytes() } ?: return null
    siteCache[name] = bytes
    return bytes
}

private val template: String by lazy {
    LatestApp::class.java.getResourceAsStream("/site/index.html")!!.use { it.readBytes().decodeToString() }
}

/**
 * The game's page: what it is, pictures, how to install, and the newest build's number
 * and notes. Friends are sent here; its address never changes.
 */
fun landingPage(base: String, live: LiveCount): String {
    val liveLine = when {
        live.playing > 0 -> "<div class=\"live\"><i></i>هسه ${live.playing} ${if (live.playing == 1) "لعبة ماشية" else "لعبات ماشية"}</div>"
        live.online >= 2 -> "<div class=\"live\"><i></i>هسه ${live.online} فاتحين اللعبة</div>"
        else -> ""
    }
    val notes = LatestApp.notes
    val news = if (notes.isEmpty()) "" else buildString {
        append("<div class=\"news\"><span class=\"ver\">نسخة ").append(esc(LatestApp.name)).append("</span>")
        append("<h2 style=\"font-size:24px\">شنو الجديد</h2><ul>")
        notes.forEach { append("<li>").append(esc(it)).append("</li>") }
        append("</ul></div>")
    }
    return template
        .replace("{{BASE}}", esc(base))
        .replace("{{VERSION}}", esc(LatestApp.name))
        .replace("{{LIVE}}", liveLine)
        .replace("{{NEWS}}", news)
}

private val iosTemplate: String by lazy {
    LatestApp::class.java.getResourceAsStream("/site/ios.html")!!.use { it.readBytes().decodeToString() }
}

/** How to put the game on an iPhone without the App Store: Sideloadly with one's own Apple ID. */
fun iosPage(base: String): String = iosTemplate.replace("{{BASE}}", esc(base))
