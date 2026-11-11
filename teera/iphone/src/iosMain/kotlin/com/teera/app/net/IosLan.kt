package com.teera.app.net

import com.teera.app.PlatformApp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * A table on the Wi-Fi needs the room server running on the device, which is the JVM's
 * (the phone's and the computer's). An iPhone plays on the internet server, or joins a
 * table another device hosts, by its address.
 */

/** A table somebody is hosting on this network. */
data class WifiTable(val name: String, val host: String, val port: Int, val code: String?)

class LanHost {
    val port: Int = 0
    val isRunning: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun start(preferred: Int = DEFAULT_PORT): Int = throw UnsupportedOperationException("no table server on iOS")
    fun stop() = Unit

    companion object {
        const val DEFAULT_PORT = 8765
    }
}

fun localAddress(): String? = null

/** Asks a hosting device which room on it is waiting for players. */
suspend fun openRoomAt(host: String, port: Int): String? = runCatching {
    val body = client.get("http://$host:$port/rooms").bodyAsText()
    com.teera.core.Protocol.json.decodeFromString(ListSerializer(String.serializer()), body).firstOrNull()
}.getOrNull()

@Suppress("UNUSED_PARAMETER")
class LanBeacon(app: PlatformApp) {
    val tables: StateFlow<List<WifiTable>> = MutableStateFlow(emptyList<WifiTable>()).asStateFlow()
    fun announce(name: String, port: Int, code: String) = Unit
    fun stopAnnouncing() = Unit
    fun startLooking() = Unit
    fun stopLooking() = Unit
    fun close() = Unit
}
