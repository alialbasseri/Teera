package com.teera.app.voice

import com.teera.app.PlatformApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Table talk on the iPhone comes later; the same calls as the phone's VoiceChat. */
@Suppress("UNUSED_PARAMETER")
class VoiceChat(app: PlatformApp) {
    var send: (ByteArray) -> Unit = {}

    val micOn: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    val talking: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    val speaking: StateFlow<Set<Int>> = MutableStateFlow(emptySet<Int>()).asStateFlow()
    private val _hearing = MutableStateFlow(true)
    val hearing: StateFlow<Boolean> = _hearing.asStateFlow()

    var mutedSeats: Set<Int> = emptySet()

    fun setMic(on: Boolean) = Unit
    fun setHearing(on: Boolean) { _hearing.value = on }
    fun receive(relayed: ByteArray) = Unit
    fun stopAll() = Unit
}
