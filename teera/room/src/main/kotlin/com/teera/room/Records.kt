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
    /** Somebody opened the app and said hello, from app build [appVersion] (0 if too old to say). */
    fun playerSeen(playerId: String, name: String, avatar: String, appVersion: Int = 0) {}

    /** A player turned the evening reminders off, or back on. */
    fun setReminders(playerId: String, on: Boolean) {}

    /** The developer's numbers; null when no books are kept. */
    suspend fun adminStats(): AdminStats? = null

    // -- the developer's page: problems and suggestions, players, settings --------------

    /** A problem or suggestion sent from the app; must return at once. */
    fun saveFeedback(item: FeedbackItem) {}

    suspend fun feedback(limit: Int = 200): List<FeedbackItem> = emptyList()

    fun setFeedbackDone(id: String, done: Boolean) {}

    fun deleteFeedback(id: String) {}

    /** Players, most recently seen first; [query] matches a name or a player's short code. */
    suspend fun players(query: String = "", limit: Int = 100): List<PlayerRow> = emptyList()

    /** Bars a player from playing online, or lifts it; must return at once. */
    fun setBanned(playerId: String, banned: Boolean, reason: String = "") {}

    /** Everyone barred, read once when the server starts. */
    suspend fun bannedPlayers(): Map<String, String> = emptyMap()

    /** Settings the developer changed on the page, kept across restarts. */
    suspend fun loadSettings(): Map<String, String> = emptyMap()

    fun saveSetting(key: String, value: String) {}

    /** Everyone whose phone can be sent a notification. */
    suspend fun pushable(): List<String> = emptyList()

    /** Something happened at a table; [matchId] is the same for one match from start to end. */
    fun happened(matchId: String, roomCode: String, happening: Room.Happening) {}

    /** What the books say about a player; null when none are kept. */
    suspend fun recordOf(playerId: String): PlayerRecord? = null

    /** The ranking; null when no books are kept. */
    suspend fun leaderboard(): com.teera.core.Leaderboard? = null

    /** Keeps a tournament as it now stands; must return at once. */
    fun saveTournament(tournament: com.teera.core.Tournament) {}

    suspend fun loadTournament(id: String): com.teera.core.Tournament? = null

    /** One match, game by game; null when there is no such match or no books. */
    suspend fun matchDetail(matchId: String): com.teera.core.MatchDetail? = null

    companion object {
        val NONE = object : Records {}
    }
}
