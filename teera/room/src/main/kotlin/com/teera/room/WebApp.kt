package com.teera.room

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.queryString
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream

/**
 * Teera in the browser, at /play: the files `gradle :web:publishToServer` put in the
 * resources (play/). Sent gzipped to browsers that take it (all do), which turns about
 * 15 MB into a few, for players on mobile data; each file is compressed once and kept.
 */
fun Route.webApp() {
    get("/play") { call.respondRedirect("/play/" + (call.request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")) }
    get("/play/") { serve(call, "index.html") }
    get("/play/{path...}") {
        val path = call.parameters.getAll("path").orEmpty().joinToString("/")
        serve(call, path)
    }
}

private val playName = Regex("[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*\\.[a-z0-9]+")
private val gzipped = ConcurrentHashMap<String, ByteArray>()
private val plain = ConcurrentHashMap<String, ByteArray>()

private fun playFile(path: String): ByteArray? {
    if (!playName.matches(path) || ".." in path) return null
    plain[path]?.let { return it }
    val bytes = LatestApp::class.java.getResourceAsStream("/play/$path")?.use { it.readBytes() } ?: return null
    // Small files are kept as they are; the big ones only gzipped (see below).
    if (bytes.size < 200_000) plain[path] = bytes
    return bytes
}

private fun typeOf(path: String): ContentType = when (path.substringAfterLast('.')) {
    "html" -> ContentType.Text.Html.withParameter("charset", "utf-8")
    "js" -> ContentType.Application.JavaScript
    "wasm" -> ContentType("application", "wasm")
    "png" -> ContentType.Image.PNG
    "ttf" -> ContentType("font", "ttf")
    "m4a" -> ContentType("audio", "mp4")
    "json" -> ContentType.Application.Json
    else -> ContentType.Application.OctetStream
}

private suspend fun serve(call: io.ktor.server.application.ApplicationCall, path: String) {
    val name = path.ifEmpty { "index.html" }
    // The page and its loader change with every build; the rest are named by content or never change.
    val revalidate = name == "index.html" || name == "teera.js" || name == "teera.wasm"
    call.response.header(HttpHeaders.CacheControl, if (revalidate) "no-cache" else "public, max-age=604800")
    val compressible = name.substringAfterLast('.') in setOf("html", "js", "wasm", "ttf", "json")
    val acceptsGzip = call.request.header(HttpHeaders.AcceptEncoding)?.contains("gzip") == true
    if (compressible && acceptsGzip) {
        val packed = gzipped[name] ?: playFile(name)?.let { gzip(it) }?.also { gzipped[name] = it }
        if (packed == null) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return
        }
        call.response.header(HttpHeaders.ContentEncoding, "gzip")
        call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
        call.respondBytes(packed, typeOf(name))
        return
    }
    val bytes = playFile(name)
    if (bytes == null) {
        call.respondText("Not Found", status = HttpStatusCode.NotFound)
        return
    }
    call.respondBytes(bytes, typeOf(name))
}

private fun gzip(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(bytes.size / 3)
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}
