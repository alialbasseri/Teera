package com.teera.app.net

import com.teera.app.PlatformApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * A browser cannot run the room server nor find tables on the Wi-Fi; it plays on the
 * internet server. These keep the shared view model's calls working.
 */

data class WifiTable(val name: String, val host: String, val port: Int, val code: String?)

class LanHost {
    val port: Int = 0
    val isRunning: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun start(preferred: Int = DEFAULT_PORT): Int = throw UnsupportedOperationException("no table server in a browser")
    fun stop() = Unit

    companion object {
        const val DEFAULT_PORT = 8765
    }
}

fun localAddress(): String? = null

@Suppress("UNUSED_PARAMETER")
suspend fun openRoomAt(host: String, port: Int): String? = null

@Suppress("UNUSED_PARAMETER")
class LanBeacon(app: PlatformApp) {
    val tables: StateFlow<List<WifiTable>> = MutableStateFlow(emptyList<WifiTable>()).asStateFlow()
    fun announce(name: String, port: Int, code: String) = Unit
    fun stopAnnouncing() = Unit
    fun startLooking() = Unit
    fun stopLooking() = Unit
    fun close() = Unit
}
