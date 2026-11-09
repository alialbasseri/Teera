package com.teera.server

import com.teera.room.Records
import com.teera.room.gameServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    // The books are kept only when a database has been given (DEPLOY.md says how).
    val records: Records = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() }?.let { address ->
        try {
            SqlRecords.connect(address)
        } catch (e: Exception) {
            LoggerFactory.getLogger("Main").warn("DATABASE_URL could not be used: {}", e.message)
            null
        }
    } ?: Records.NONE
    LoggerFactory.getLogger("Main").info("records: {}", if (records === Records.NONE) "not kept" else "kept")

    // Notifications: the Firebase service account, as a Render secret file or a variable.
    val account = sequenceOf(
        System.getenv("FIREBASE_SERVICE_ACCOUNT"),
        runCatching { java.io.File("/etc/secrets/firebase.json").readText() }.getOrNull(),
    ).firstOrNull { !it.isNullOrBlank() }?.trim()?.let { text ->
        if (text.startsWith("{")) text else runCatching { String(java.util.Base64.getDecoder().decode(text)) }.getOrNull()
    }
    val pusher: com.teera.room.Pusher = account?.let { json ->
        try {
            FcmPusher(json, records as? SqlRecords ?: object : TokenStore {})
        } catch (e: Exception) {
            LoggerFactory.getLogger("Main").warn("the Firebase key could not be used: {}", e.message)
            null
        }
    } ?: com.teera.room.Pusher.NONE
    LoggerFactory.getLogger("Main").info("notifications: {}", if (pusher === com.teera.room.Pusher.NONE) "off" else "on")
    // Every turn at the server's tables gets 30 seconds; then a bot plays it for them.
    val registry = com.teera.room.RoomRegistry(turnSeconds = TURN_SECONDS)
    // The evening reminders need the books (who to reach, what they were sent) and notifications.
    if (records is SqlRecords && pusher !== com.teera.room.Pusher.NONE) {
        Reminders(records, pusher, registry).start(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        LoggerFactory.getLogger("Main").info("reminders: on")
    }
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        gameServer(
            registry = registry,
            records = records,
            latestFrom = com.teera.room.VERSION_URL,
            pusher = pusher,
            adminKey = System.getenv("ADMIN_KEY"),
        )
    }.start(wait = true)
}

/** How long a player has for each turn before a bot plays it for them. */
const val TURN_SECONDS = 30
