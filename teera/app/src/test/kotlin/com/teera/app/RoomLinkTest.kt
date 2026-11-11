package com.teera.app

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class RoomLinkTest {

    @Test
    fun `a room's link is made from the server address`() {
        assertEquals("https://teera-xfvr.onrender.com/j/KHAN", roomLink("wss://teera-xfvr.onrender.com/ws", "KHAN"))
        assertNull(roomLink("ws://192.168.1.5:8765/ws", "KHAN"), "a Wi-Fi table has no link anyone outside could use")
    }

    @Test
    fun `the code is read back out of either kind of link`() {
        assertEquals("KHAN", codeFromLink("https://teera-xfvr.onrender.com/j/KHAN"))
        assertEquals("KHAN", codeFromLink("https://teera-xfvr.onrender.com/j/khan?utm=wa"))
        assertEquals("KHAN", codeFromLink("teera://join/KHAN"))
        assertNull(codeFromLink("https://teera-xfvr.onrender.com/health"))
        assertNull(codeFromLink("https://teera-xfvr.onrender.com/j/TOOLONG"))
        assertNull(codeFromLink(null))
    }
}
