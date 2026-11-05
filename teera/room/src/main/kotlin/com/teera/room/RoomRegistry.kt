package com.teera.room

import com.teera.core.Room
import com.teera.core.Rules
import io.ktor.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex

/**
 * Live rooms, keyed by the code friends type in to join.
 *
 * One [Mutex] per room: room state is only touched while holding it, so the engine sees
 * a single ordered stream of moves however the sockets interleave.
 */
class RoomRegistry(private val clock: () -> Long = System::currentTimeMillis) {

    class Holder(val room: Room) {
        val lock = Mutex()
        val sessions = ConcurrentHashMap<String, WebSocketSession>()

        /** Players' own pictures, by player id, handed to each newcomer once. */
        val photos = ConcurrentHashMap<String, String>()

        @Volatile
        var lastActivity: Long = System.currentTimeMillis()

        /** Bot moves are spaced out so the table reads at human speed. */
        @Volatile
        var nextBotMoveAt: Long = 0L
    }

    private val rooms = ConcurrentHashMap<String, Holder>()

    fun create(hostId: String, rules: Rules): Holder {
        var code = newCode()
        while (rooms.containsKey(code)) code = newCode()
        val holder = Holder(Room(code, hostId, rules, clock))
        rooms[code] = holder
        return holder
    }

    fun find(code: String): Holder? = rooms[code.trim().uppercase()]

    fun all(): Collection<Holder> = rooms.values

    fun size(): Int = rooms.size

    /** Drops rooms nobody has touched for [idleMillis]. */
    fun evictIdle(idleMillis: Long) {
        val cutoff = clock() - idleMillis
        rooms.entries.removeIf { (_, holder) ->
            holder.sessions.isEmpty() && holder.lastActivity < cutoff
        }
    }

    private fun newCode(): String = (1..CODE_LENGTH)
        .map { ALPHABET[Random.nextInt(ALPHABET.length)] }
        .joinToString("")

    companion object {
        /** No O/0, I/1 or S/5: these codes get read aloud across a table. */
        const val ALPHABET = "ACDEFGHJKLMNPQRTUVWXY3467"
        const val CODE_LENGTH = 4
    }
}
