package com.teera.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            gameServer(RoomRegistry())
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

/**
 * Announces a hosted table on the network, and finds tables other phones are hosting,
 * using Android's built-in service discovery.
 */
class LanBeacon(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Some phones drop the multicast packets discovery rides on unless asked to keep them.
    private val lock = wifi.createMulticastLock("teera").apply { setReferenceCounted(false) }

    private val _tables = MutableStateFlow<List<WifiTable>>(emptyList())
    val tables: StateFlow<List<WifiTable>> = _tables.asStateFlow()

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    fun announce(name: String, port: Int, code: String) {
        stopAnnouncing()
        val info = NsdServiceInfo().apply {
            serviceName = "Teera $name"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("code", code)
            setAttribute("name", name)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopAnnouncing() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun startLooking() {
        if (discovery != null) return
        lock.acquire()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit

            override fun onServiceFound(found: NsdServiceInfo) {
                if (found.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return
                resolve(found)
            }

            override fun onServiceLost(lost: NsdServiceInfo) {
                _tables.update { list -> list.filterNot { it.name == displayName(lost) } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopLooking() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        if (lock.isHeld) lock.release()
        _tables.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private fun resolve(found: NsdServiceInfo) {
        runCatching {
            nsd.resolveService(found, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) = Unit
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    val code = info.attributes["code"]?.let { String(it) }
                    val table = WifiTable(displayName(info), host, info.port, code)
                    _tables.update { list -> (list.filterNot { it.host == host && it.port == info.port } + table) }
                }
            })
        }
    }

    private fun displayName(info: NsdServiceInfo): String =
        info.attributes["name"]?.let { String(it) } ?: info.serviceName.removePrefix("Teera ")

    fun close() {
        stopAnnouncing()
        stopLooking()
    }

    private companion object {
        const val SERVICE_TYPE = "_teera._tcp."
    }
}
