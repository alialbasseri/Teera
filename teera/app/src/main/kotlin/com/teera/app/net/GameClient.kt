package com.teera.app.net

import com.teera.core.ClientMessage
import com.teera.core.Protocol
import com.teera.core.ServerMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The link between the phone and whoever is refereeing.
 *
 * Online that is the room server over a websocket; offline it is a [LocalGameClient]
 * running the same engine in this process. The screens above cannot tell the difference,
 * which is what keeps solo play and online play honest with each other.
 */
interface GameClient {
    val incoming: Flow<ServerMessage>

    /** How the line to the referee is doing. A table in this process is always up. */
    val link: StateFlow<Link> get() = ALWAYS_UP

    fun send(message: ClientMessage)

    /**
     * Table talk, as relayed by the referee: `[seat][packet]`. Empty for a table in this
     * process, where everybody else is a bot.
     */
    val voice: Flow<ByteArray> get() = emptyFlow()

    /** Sends one packet of this player's voice; dropped when the line is not up. */
    fun sendVoice(packet: ByteArray) = Unit

    fun close()
}

/** The state of the line to the referee, as the screens describe it. */
enum class Link {
    /** Knocking on a sleeping internet server; the first visit after a quiet spell is slow. */
    WAKING,
    CONNECTING,
    UP,
    /** It was up and dropped; reconnecting on its own, seat held. */
    LOST,
    /** Never got through, and has stopped trying. */
    UNREACHABLE,
}

private val ALWAYS_UP: StateFlow<Link> = MutableStateFlow(Link.UP)

internal fun encode(message: ClientMessage): String = Protocol.encode(message)
