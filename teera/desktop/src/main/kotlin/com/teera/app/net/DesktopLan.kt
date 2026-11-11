package com.teera.app.net

import com.teera.app.PlatformApp
import java.net.InetAddress
import java.util.concurrent.Executors
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Announces a hosted table on the network, and finds tables others are hosting: the same
 * DNS-SD service (_teera._tcp, with "name" and "code") that phones use through Android's
 * service discovery, so computers and phones see each other's tables.
 */
@Suppress("UNUSED_PARAMETER")
class LanBeacon(app: PlatformApp) {

    private val _tables = MutableStateFlow<List<WifiTable>>(emptyList())
    val tables: StateFlow<List<WifiTable>> = _tables.asStateFlow()

    // JmDNS blocks while it starts and stops: one thread of its own, in order.
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "teera-lan").apply { isDaemon = true } }
    private var dns: JmDNS? = null
    private var announced: ServiceInfo? = null
    private var looking: ServiceListener? = null

    private fun dns(): JmDNS? {
        dns?.let { return it }
        val address = localAddress()?.let { runCatching { InetAddress.getByName(it) }.getOrNull() } ?: return null
        return runCatching { JmDNS.create(address, "teera-${address.hostAddress.replace('.', '-')}") }.getOrNull()?.also { dns = it }
    }

    fun announce(name: String, port: Int, code: String) = worker.execute {
        val dns = dns() ?: return@execute
        announced?.let { runCatching { dns.unregisterService(it) } }
        val info = ServiceInfo.create(SERVICE_TYPE, "Teera $name", port, 0, 0, mapOf("code" to code, "name" to name))
        runCatching { dns.registerService(info) }.onSuccess { announced = info }
    }

    fun stopAnnouncing() = worker.execute {
        announced?.let { info -> dns?.let { runCatching { it.unregisterService(info) } } }
        announced = null
    }

    fun startLooking() = worker.execute {
        if (looking != null) return@execute
        val dns = dns() ?: return@execute
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                event.dns.requestServiceInfo(event.type, event.name, 3_000)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val gone = displayName(event.info, event.name)
                _tables.update { list -> list.filterNot { it.name == gone } }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info
                if (info == announced) return
                val host = info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                val table = WifiTable(displayName(info, event.name), host, info.port, info.getPropertyString("code"))
                _tables.update { list -> list.filterNot { it.host == host && it.port == info.port } + table }
            }
        }
        looking = listener
        runCatching { dns.addServiceListener(SERVICE_TYPE, listener) }
    }

    fun stopLooking() = worker.execute {
        looking?.let { listener -> dns?.removeServiceListener(SERVICE_TYPE, listener) }
        looking = null
        _tables.value = emptyList()
    }

    fun close() {
        worker.execute {
            runCatching { dns?.unregisterAllServices() }
            runCatching { dns?.close() }
            dns = null
        }
        worker.shutdown()
    }

    private fun displayName(info: ServiceInfo?, serviceName: String): String =
        info?.getPropertyString("name") ?: serviceName.removePrefix("Teera ")

    private companion object {
        const val SERVICE_TYPE = "_teera._tcp.local."
    }
}
