package com.teera.room

import com.teera.core.PlayerRecord
import com.teera.core.Room

/**
 * Where the server keeps its books: who has played, every match, every game and its
 * steps. The cloud server writes them to a database; a phone hosting on the Wi-Fi keeps
 * none ([NONE]).
 *
 * Writes must return at once (they are made while a room is locked) and must never
 * throw: a database that is down loses a record, not a game.
 */
interface Records {
    /** Somebody opened the app and said hello. */
    fun playerSeen(playerId: String, name: String, avatar: String) {}

    /** Something happened at a table; [matchId] is the same for one match from start to end. */
    fun happened(matchId: String, roomCode: String, happening: Room.Happening) {}

    /** What the books say about a player; null when none are kept. */
    suspend fun recordOf(playerId: String): PlayerRecord? = null

    /** The ranking; null when no books are kept. */
    suspend fun leaderboard(): com.teera.core.Leaderboard? = null

    /** One match, game by game; null when there is no such match or no books. */
    suspend fun matchDetail(matchId: String): com.teera.core.MatchDetail? = null

    companion object {
        val NONE = object : Records {}
    }
}
