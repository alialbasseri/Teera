package com.teera.app

import org.junit.Test
import kotlin.test.assertEquals

class ServerAddressTest {
    @Test
    fun `whatever gets pasted becomes the server's websocket address`() {
        assertEquals("wss://teera.onrender.com/ws", normaliseServer("teera.onrender.com"))
        assertEquals("wss://teera.onrender.com/ws", normaliseServer("https://teera.onrender.com/"))
        assertEquals("ws://192.168.1.5:8080/ws", normaliseServer("http://192.168.1.5:8080"))
        assertEquals("wss://teera.fly.dev/ws", normaliseServer("wss://teera.fly.dev/ws"))
        assertEquals("", normaliseServer("   "))
    }
}
