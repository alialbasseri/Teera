package com.teera.room

import com.teera.core.ErrorCode
import com.teera.core.Phase
import com.teera.core.Protocol
import com.teera.core.ServerMessage
import com.teera.core.shortCodeOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Everything the developer's page shows at once, refreshed every few seconds. */
@Serializable
data class AdminOverview(val live: LiveNow, val stats: AdminStats?, val settings: AdminSettings)

/** What the page needs to work the server: its rooms, its books, its notifications. */
class AdminContext(
    val registry: RoomRegistry,
    val records: Records,
    val pusher: Pusher,
    val admin: AdminState,
    val adminKey: String,
    val peak: () -> Int,
)

/** Whether a call carries the developer's password, in its header or its address. */
internal fun ApplicationCall.hasAdminKey(key: String): Boolean {
    if (key.length < 6) return false
    val given = (request.headers["X-Admin-Key"] ?: request.queryParameters["key"])?.trim().orEmpty()
    return given.isNotEmpty() && java.security.MessageDigest.isEqual(given.toByteArray(), key.toByteArray())
}

internal fun liveNow(registry: RoomRegistry, peak: Int): LiveNow {
    val holders = registry.all()
    return LiveNow(
        online = registry.online.size,
        rooms = holders.size,
        playing = holders.count { h -> h.room.game?.let { it.phase != Phase.MATCH_OVER } == true },
        watchers = holders.sumOf { it.room.info().spectators.size },
        peakToday = peak,
    )
}

private suspend fun <T> ApplicationCall.json(serializer: KSerializer<T>, value: T) =
    respondText(Protocol.json.encodeToString(serializer, value), ContentType.Application.Json)

private suspend fun ApplicationCall.ok(extra: String = "") =
    respondText("""{"ok":true$extra}""", ContentType.Application.Json)

/** The developer's controls, as JSON under /admin/api, all behind the password. */
fun Route.adminApi(context: AdminContext) = route("/admin/api") {
    val registry = context.registry
    val records = context.records
    val admin = context.admin

    // Every call checks the password first; a wrong one waits, so it cannot be guessed fast.
    suspend fun ApplicationCall.allowed(): Boolean {
        if (hasAdminKey(context.adminKey)) return true
        kotlinx.coroutines.delay(1_500)
        respondText("""{"ok":false}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
        return false
    }

    suspend fun ApplicationCall.body(): JsonObject? =
        runCatching { Protocol.json.parseToJsonElement(receiveText()) as JsonObject }.getOrNull()

    fun JsonObject.text(name: String, max: Int): String =
        (this[name]?.jsonPrimitive?.content ?: "").trim().take(max)

    get("/overview") {
        if (!call.allowed()) return@get
        call.json(
            AdminOverview.serializer(),
            AdminOverview(liveNow(registry, context.peak()), records.adminStats(), admin.settings),
        )
    }

    // -- rooms -----------------------------------------------------------------------

    get("/rooms") {
        if (!call.allowed()) return@get
        val now = System.currentTimeMillis()
        val rows = registry.all().map { holder ->
            val room = holder.room
            val info = room.info()
            val state = room.game
            RoomRow(
                code = room.code,
                players = info.seats.filter { !it.isBot && it.playerId.isNotBlank() }.map { it.name },
                bots = info.seats.count { it.isBot },
                watchers = info.spectators.size,
                started = state != null,
                phase = state?.phase?.name ?: "LOBBY",
                scores = state?.scores ?: emptyList(),
                target = room.rules.target,
                game = state?.gameNumber ?: 0,
                listed = info.listed,
                locked = info.locked,
                idleSeconds = (now - holder.lastActivity) / 1000,
            )
        }.sortedWith(compareByDescending<RoomRow> { it.started }.thenBy { it.idleSeconds })
        call.json(ListSerializer(RoomRow.serializer()), rows)
    }

    post("/rooms/{code}/say") {
        if (!call.allowed()) return@post
        val holder = registry.find(call.parameters["code"].orEmpty())
        val text = call.body()?.text("text", 300).orEmpty()
        if (holder == null || text.isEmpty()) return@post call.respondText("""{"ok":false}""", ContentType.Application.Json)
        val line = ServerMessage.ChatMessage(seat = -1, name = "الإدارة", text = text, atEpochMs = System.currentTimeMillis())
        holder.sessions.values.forEach { it.trySend(line) }
        call.ok()
    }

    post("/rooms/{code}/close") {
        if (!call.allowed()) return@post
        val holder = registry.remove(call.parameters["code"].orEmpty())
            ?: return@post call.respondText("""{"ok":false}""", ContentType.Application.Json)
        val told = ServerMessage.Failure(ErrorCode.KICKED, "الإدارة سدّت هالطاولة")
        holder.sessions.values.forEach { it.trySend(told) }
        holder.sessions.clear()
        call.ok()
    }

    // -- players -----------------------------------------------------------------------

    get("/players") {
        if (!call.allowed()) return@get
        val query = call.request.queryParameters["q"].orEmpty().trim().take(40)
        val rows = records.players(query, limit = 150).map { row ->
            row.copy(
                code = row.code.ifEmpty { shortCodeOf(row.id) },
                banned = admin.isBanned(row.id),
                banReason = admin.banned[row.id] ?: "",
            )
        }
        call.json(ListSerializer(PlayerRow.serializer()), rows)
    }

    post("/players/{id}/ban") {
        if (!call.allowed()) return@post
        val id = call.parameters["id"].orEmpty()
        if (id.isBlank()) return@post call.respondText("""{"ok":false}""", ContentType.Application.Json)
        val reason = call.body()?.text("reason", 200).orEmpty()
        admin.banned[id] = reason
        records.setBanned(id, banned = true, reason = reason)
        // Off the server now: every app they have open is told and let go.
        registry.online[id]?.sessions?.toList()?.forEach { session ->
            session.trySend(ServerMessage.Failure(ErrorCode.INTERNAL, BANNED_WORDS))
            runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "banned")) }
        }
        call.ok()
    }

    post("/players/{id}/unban") {
        if (!call.allowed()) return@post
        val id = call.parameters["id"].orEmpty()
        admin.banned.remove(id)
        records.setBanned(id, banned = false)
        call.ok()
    }

    // -- problems and suggestions --------------------------------------------------------

    get("/feedback") {
        if (!call.allowed()) return@get
        call.json(ListSerializer(FeedbackItem.serializer()), records.feedback())
    }

    get("/feedback/{id}/image") {
        if (!call.allowed()) return@get
        val bytes = records.feedbackImage(call.parameters["id"].orEmpty())
            ?: return@get call.respondText("", status = HttpStatusCode.NotFound)
        call.respondBytes(bytes, ContentType.Image.JPEG)
    }

    post("/feedback/{id}/done") {
        if (!call.allowed()) return@post
        val done = call.request.queryParameters["done"]?.toBooleanStrictOrNull() ?: true
        records.setFeedbackDone(call.parameters["id"].orEmpty(), done)
        call.ok()
    }

    post("/feedback/{id}/delete") {
        if (!call.allowed()) return@post
        records.deleteFeedback(call.parameters["id"].orEmpty())
        call.ok()
    }

    // -- the server's settings, and words to everyone ----------------------------------

    post("/settings") {
        if (!call.allowed()) return@post
        val next = runCatching { Protocol.json.decodeFromString(AdminSettings.serializer(), call.receiveText()) }.getOrNull()
            ?: return@post call.respondText("""{"ok":false}""", ContentType.Application.Json)
        val clean = next.copy(minAppVersion = next.minAppVersion.coerceIn(0, 100_000))
        admin.settings = clean
        records.saveSetting("minAppVersion", clean.minAppVersion.toString())
        records.saveSetting("remindersOn", clean.remindersOn.toString())
        call.ok()
    }

    post("/announce") {
        if (!call.allowed()) return@post
        val text = call.body()?.text("text", 400).orEmpty()
        if (text.isEmpty()) return@post call.respondText("""{"ok":false}""", ContentType.Application.Json)
        val words = ServerMessage.Announcement(text)
        var reached = 0
        registry.online.values.forEach { present ->
            present.sessions.forEach { it.trySend(words) }
            reached++
        }
        call.ok(""","reached":$reached""")
    }

    post("/push") {
        if (!call.allowed()) return@post
        val body = call.body()
        val title = body?.text("title", 60).orEmpty()
        val words = body?.text("body", 200).orEmpty()
        if (title.isEmpty() || words.isEmpty()) return@post call.respondText("""{"ok":false}""", ContentType.Application.Json)
        val everyone = records.pushable().filter { !admin.isBanned(it) }
        everyone.forEach { context.pusher.notify(it, title, words) }
        call.ok(""","reached":${everyone.size}""")
    }
}

/** What a barred player's app shows. */
const val BANNED_WORDS = "انحظرت من اللعب أونلاين. إذا تشوفه غلط، راسل صاحب اللعبة."
