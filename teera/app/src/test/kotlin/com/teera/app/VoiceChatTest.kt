package com.teera.app

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import com.teera.app.voice.VoiceChat
import com.teera.app.voice.VoiceWire
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The microphone and speaker side, on Robolectric's stand-ins for Android's audio: it
 * cannot tell how it sounds, only that switching things on and off and hearing a packet
 * work without throwing, and that the phone's audio mode is put back afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceChatTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `the mic opens and closes, and the phone's audio mode is put back`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val audio = app.getSystemService(android.media.AudioManager::class.java)
        val before = audio.mode
        val chat = VoiceChat(app)
        chat.setMic(true)
        assertTrue(chat.micOn.value)
        assertEquals(android.media.AudioManager.MODE_IN_COMMUNICATION, audio.mode, "call mode, for echo cancelling")
        chat.setMic(false)
        assertFalse(chat.micOn.value)
        assertEquals(before, audio.mode)
    }

    @Test
    fun `a friend's voice is played and their ring lights, until hearing is switched off`() {
        val chat = VoiceChat(app)
        val words = ShortArray(VoiceWire.FRAME_SAMPLES) { (kotlin.math.sin(it / 5.0) * 9_000).toInt().toShort() }
        val deadline = System.currentTimeMillis() + 5_000
        var seq = 0
        while (2 !in chat.speaking.value && System.currentTimeMillis() < deadline) {
            chat.receive(byteArrayOf(2) + VoiceWire.pack(seq++, words))
            Thread.sleep(20)
        }
        assertTrue(2 in chat.speaking.value, "seat 2's ring is lit")
        chat.setHearing(false)
        assertTrue(chat.speaking.value.isEmpty())
        chat.receive(byteArrayOf(2) + VoiceWire.pack(seq, words))
        Thread.sleep(200)
        assertTrue(chat.speaking.value.isEmpty(), "muted means muted")
        chat.stopAll()
    }
}
