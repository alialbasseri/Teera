package com.teera.app.net

import com.teera.app.PlatformApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phones find each other's tables with Android's service discovery; a computer does not
 * look or announce in this version. It can still host a table on the Wi-Fi, and join one
 * by typing the address the host's screen shows.
 */
@Suppress("UNUSED_PARAMETER")
class LanBeacon(app: PlatformApp) {
    val tables: StateFlow<List<WifiTable>> = MutableStateFlow(emptyList<WifiTable>()).asStateFlow()
    fun announce(name: String, port: Int, code: String) = Unit
    fun stopAnnouncing() = Unit
    fun startLooking() = Unit
    fun stopLooking() = Unit
    fun close() = Unit
}
