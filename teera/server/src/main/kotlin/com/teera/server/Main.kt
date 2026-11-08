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
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        gameServer(records = records, latestFrom = com.teera.room.VERSION_URL)
    }.start(wait = true)
}
