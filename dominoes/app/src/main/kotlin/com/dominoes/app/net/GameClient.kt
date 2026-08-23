package com.dominoes.app.net

import com.dominoes.core.ClientMessage
import com.dominoes.core.ErrorCode
import com.dominoes.core.Protocol
import com.dominoes.core.ServerMessage
import kotlinx.coroutines.flow.Flow

/**
 * The link between the phone and whoever is refereeing.
 *
 * Online that is the room server over a websocket; offline it is a [LocalGameClient]
 * running the same engine in this process. The screens above cannot tell the difference,
 * which is what keeps solo play and online play honest with each other.
 */
interface GameClient {
    val incoming: Flow<ServerMessage>
    fun send(message: ClientMessage)
    fun close()
}

/** Turns a socket failure into something a person can read. */
internal fun connectionLost(detail: String?) = ServerMessage.Failure(
    ErrorCode.INTERNAL,
    detail?.takeIf { it.isNotBlank() } ?: "Lost connection to the table",
)

internal fun encode(message: ClientMessage): String = Protocol.encode(message)
