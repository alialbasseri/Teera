package com.teera.app.net

import com.teera.core.Protocol
import com.teera.room.RoomRegistry
import com.teera.room.gameServer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A table on the Wi-Fi: one phone runs the room server, the others join it.
 *
 * The host runs exactly the code the cloud server runs, on Ktor's CIO engine rather than
 * Netty, so a game at home and a game over the internet are refereed identically. Nobody
 * needs an account, a server, or a code typed in: the table announces itself on the
 * network and the other phones see it appear.
 */

/** A table somebody is hosting on this network. */
data class WifiTable(val name: String, val host: String, val port: Int, val code: String?)

/** Runs the room server on this phone. */
class LanHost {
    private var engine: ApplicationEngine? = null

    var port: Int = 0
        private set

    val isRunning: Boolean get() = engine != null

    /** Starts the server and returns the port it is listening on. */
    fun start(preferred: Int = DEFAULT_PORT): Int {
        engine?.let { return port }
        port = freePort(preferred)
        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            gameServer(RoomRegistry(turnSeconds = 30))
        }.start(wait = false)
        return port
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        engine = null
    }

    private fun freePort(preferred: Int): Int {
        for (candidate in preferred until preferred + 20) {
            val free = runCatching { ServerSocket(candidate).use { true } }.getOrDefault(false)
            if (free) return candidate
        }
        return ServerSocket(0).use { it.localPort }
    }

    companion object {
        const val DEFAULT_PORT = 8765
    }
}

/** This phone's address on the local network, for friends whose phones cannot find the table. */
fun localAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

/** Asks a hosting phone which room on it is waiting for players. */
suspend fun openRoomAt(host: String, port: Int, http: OkHttpClient = OkHttpClient()): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("http://$host:$port/rooms").build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                Protocol.json.decodeFromString(ListSerializer(String.serializer()), body).firstOrNull()
            }
        }.getOrNull()
    }
