package com.teera.app.net

import com.teera.core.ClientMessage
import com.teera.core.Emote
import com.teera.core.GameEvent
import com.teera.core.teamOf
import com.teera.core.Room
import com.teera.core.Rules
import com.teera.core.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A table hosted inside the app, for playing bots on a plane or with no signal.
 *
 * It drives the same [Room] the server does, so a rule that holds online holds offline.
 */
class LocalGameClient(
    private val scope: CoroutineScope,
    private val playerId: String,
    private val playerName: String,
    rules: Rules = Rules.DEFAULT,
    private val botThinkMillis: Long = 750L,
    private val avatar: String = "",
) : GameClient {

    private val room = Room(code = "SOLO", hostId = playerId, rules = rules)

    // Replays, because the first messages are emitted while the screen is still
    // subscribing and a dropped deal would leave the table blank.
    private val events = MutableSharedFlow<ServerMessage>(
        replay = 32,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming: Flow<ServerMessage> = events.asSharedFlow()

    private var ticker: Job? = null

    init {
        events.tryEmit(ServerMessage.Welcome(playerId))
        publish(room.join(playerId, playerName, avatar))
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (room.awaitingBot()) {
                    delay(botThinkMillis)
                    publish(room.stepBot())
                    banter()
                } else {
                    publish(room.expireTurnIfDue())
                }
            }
        }
    }

    override fun send(message: ClientMessage) {
        val output = when (message) {
            is ClientMessage.SetRules -> room.setRules(playerId, message.rules)
            is ClientMessage.SetBot -> room.setBot(playerId, message.seat, message.enabled, message.difficulty)
            is ClientMessage.Ready -> room.setReady(playerId, message.ready)
            is ClientMessage.TakeSeat -> room.takeSeat(playerId, message.seat)
            ClientMessage.StartMatch -> room.start(playerId)
            is ClientMessage.MakeMove -> room.move(playerId, message.move)
            ClientMessage.NextGame -> room.nextGame(playerId)
            is ClientMessage.Chat -> room.chat(playerId, message.text)
                ?.let { Room.Output(direct = listOf(playerId to it)) } ?: Room.Output.NOTHING
            ClientMessage.LeaveRoom -> room.leave(playerId)
            ClientMessage.Ping -> Room.Output(direct = listOf(playerId to ServerMessage.Pong))
            is ClientMessage.SendEmote -> room.emote(playerId, message.emote)
            is ClientMessage.Hello, is ClientMessage.CreateRoom, is ClientMessage.JoinRoom -> Room.Output.ROOM
            // Friends and invitations live on the internet server, not at a table of bots.
            is ClientMessage.WhoIsOnline, is ClientMessage.FindPlayer, is ClientMessage.Invite -> Room.Output.NOTHING
        }
        publish(output)
    }

    /**
     * Bots talk too. Pushing a tile onto somebody who is stuck comes with "ابلع", the way
     * it would across a real table, and losing a game gets a sigh.
     */
    private fun banter() {
        val state = room.game ?: return
        val mySeat = room.seatOf(playerId) ?: return
        for (event in state.events) {
            when (event) {
                is GameEvent.Gave ->
                    if (event.to == mySeat) publish(room.botEmote(event.from, Emote.GULP))
                is GameEvent.GameEnded -> {
                    val winner = event.result.winnerSeat ?: continue
                    if (winner != mySeat && teamOf(winner) != teamOf(mySeat)) {
                        publish(room.botEmote(winner, Emote.SHEEL))
                    }
                }
                else -> Unit
            }
        }
    }

    override fun close() {
        ticker?.cancel()
        ticker = null
    }

    private fun publish(output: Room.Output) {
        if (output == Room.Output.NOTHING) return
        output.direct.forEach { (_, message) -> events.tryEmit(message) }
        if (output.roomChanged) events.tryEmit(ServerMessage.RoomUpdate(room.info()))
        if (output.viewChanged) {
            room.viewFor(playerId)?.let { events.tryEmit(ServerMessage.GameUpdate(it)) }
        }
    }

    private companion object {
        const val TICK_MS = 120L
    }
}
