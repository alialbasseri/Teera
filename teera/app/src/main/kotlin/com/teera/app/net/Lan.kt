package com.teera.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
