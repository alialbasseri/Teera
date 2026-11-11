package com.teera.app

import com.teera.app.net.LanBeacon
import com.teera.app.net.localAddress
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A table announced on the network is found by another device looking (same as phones).
 * Needs a network that carries multicast, so only when asked (-Pteera.live).
 */
class LanBeaconTest {
    @Test
    fun `a hosted table is seen on the network`() {
        if (System.getProperty("teera.live") != "true" || localAddress() == null) return
        val app = DesktopApp(kotlin.io.path.createTempDirectory("teera-lan").toFile())
        val host = LanBeacon(app)
        val guest = LanBeacon(app)
        try {
            host.announce("علي", 8765, "ABCD")
            guest.startLooking()
            var found = false
            repeat(40) {
                if (guest.tables.value.any { it.code == "ABCD" && it.name == "علي" && it.port == 8765 }) found = true
                if (!found) Thread.sleep(250)
            }
            assertTrue(found, "the table was not seen: ${guest.tables.value}")
        } finally {
            host.close()
            guest.close()
        }
    }
}
