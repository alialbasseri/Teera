package com.teera.room

import com.teera.core.Room
import com.teera.core.Rules
import io.ktor.websocket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex

/**
 * Live rooms, keyed by the code friends type in to join.
 *
 * One [Mutex] per room: room state is only touched while holding it, so the engine sees
 * a single ordered stream of moves however the sockets interleave.
 */
class RoomRegistry(
    private val clock: () -> Long = System::currentTimeMillis,
    /** Seconds every turn gets at this server's tables; 0 leaves turns untimed. */
    private val turnSeconds: Int = 0,
) {

    class Holder(val room: Room) {
        val lock = Mutex()
        val sessions = ConcurrentHashMap<String, WebSocketSession>()

        /** Players' own pictures, by player id, handed to each newcomer once. */
        val photos = ConcurrentHashMap<String, String>()

        @Volatile
        var lastActivity: Long = System.currentTimeMillis()

        /** For a tournament match: told which side lost, so the winners go through. */
        @Volatile
        var onMatchFinished: ((losingSide: Int) -> Unit)? = null

        /**
         * Players already called back since they last looked away: one notification per
         * absence, however many turns pass while they are gone.
         */
        val calledBack: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        /** When each player was last called back, so a flickering connection is not a stream of them. */
        val lastCalledAt: MutableMap<String, Long> = ConcurrentHashMap()

        /** The match being played here, as the books know it; set when it starts. */
        @Volatile
        var matchId: String? = null

        /** Bot moves are spaced out so the table reads at human speed. */
        @Volatile
        var nextBotMoveAt: Long = 0L

        /** The table as it stood when a bot's turn came, so its thinking time starts then. */
        @Volatile
        var botTurnOf: Any? = null

        /** Stands for this room in the list without giving its code away. */
        val ref: String = UUID.randomUUID().toString().replace("-", "").take(12)

        /** Who asked from the list to come in, and when; answered once. */
        val asks: MutableMap<String, Long> = ConcurrentHashMap()
    }

    private val rooms = ConcurrentHashMap<String, Holder>()

    /** Somebody with the app open: in a room or just on the menu. */
    class Present(val name: String, val avatar: String, val sessions: MutableSet<WebSocketSession>)

    /** Everyone connected, by player id, so friends can find and invite each other. */
    val online = ConcurrentHashMap<String, Present>()

    fun arrived(playerId: String, name: String, avatar: String, session: WebSocketSession) {
        online.compute(playerId) { _, was ->
            val sessions = was?.sessions ?: java.util.Collections.newSetFromMap(ConcurrentHashMap<WebSocketSession, Boolean>())
            sessions += session
            Present(name, avatar, sessions)
        }
    }

    fun left(playerId: String, session: WebSocketSession) {
        online.computeIfPresent(playerId) { _, was ->
            was.sessions -= session
            if (was.sessions.isEmpty()) null else was
        }
    }

    fun byShortCode(code: String): Pair<String, Present>? =
        online.entries.firstOrNull { com.teera.core.shortCodeOf(it.key) == code.trim().uppercase() }?.toPair()

    fun create(hostId: String, rules: Rules): Holder {
        var code = newCode()
        while (rooms.containsKey(code)) code = newCode()
        val holder = Holder(Room(code, hostId, rules, clock, fixedTurnSeconds = turnSeconds.takeIf { it > 0 }))
        rooms[code] = holder
        return holder
    }

    fun find(code: String): Holder? = rooms[code.trim().uppercase()]

    fun byRef(ref: String): Holder? = rooms.values.firstOrNull { it.ref == ref }

    /** Opens a room under a code that is already known, for players following it back. */
    fun reopen(code: String, hostId: String): Holder {
        val key = code.trim().uppercase()
        return rooms.computeIfAbsent(key) {
            Holder(Room(key, hostId, Rules.DEFAULT, clock, fixedTurnSeconds = turnSeconds.takeIf { it > 0 }))
        }
    }

    /** Whether [code] could be one of ours, so a stray string never becomes a room. */
    fun isCode(code: String): Boolean =
        code.trim().length == CODE_LENGTH && code.trim().uppercase().all { it in ALPHABET }

    fun all(): Collection<Holder> = rooms.values

    fun size(): Int = rooms.size

    /** Drops rooms nobody has touched for [idleMillis]. */
    /** Takes a room off the server at once (the developer closed it). */
    fun remove(code: String): Holder? = rooms.remove(code.trim().uppercase())

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
