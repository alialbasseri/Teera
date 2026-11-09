package com.teera.room

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

/** A problem or suggestion sent from the app. */
@Serializable
data class FeedbackItem(
    val id: String,
    val playerId: String,
    val name: String,
    /** "problem" or "idea". */
    val kind: String,
    val text: String,
    val appVersion: Int,
    val roomCode: String? = null,
    val atEpochMs: Long,
    val done: Boolean = false,
    /** A picture came with it; the page fetches it separately. */
    val hasImage: Boolean = false,
)

/** A player as the developer's page lists them. */
@Serializable
data class PlayerRow(
    val id: String,
    val code: String,
    val name: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val appVersion: Int,
    val matches: Int,
    val banned: Boolean,
    val banReason: String = "",
    val reachable: Boolean = false,
)

/** A room as the developer's page shows it, live. */
@Serializable
data class RoomRow(
    val code: String,
    val players: List<String>,
    val bots: Int,
    val watchers: Int,
    val started: Boolean,
    val phase: String,
    val scores: List<Int>,
    val target: Int,
    val game: Int,
    val listed: Boolean,
    val locked: Boolean,
    val idleSeconds: Long,
)

/** What the developer set on the page; kept in the books so a restart keeps it. */
@Serializable
data class AdminSettings(
    /** Apps older than this build are told they must update. 0 leaves it to version.json. */
    val minAppVersion: Int = 0,
    /** Whether the evening reminders go out at all. */
    val remindersOn: Boolean = true,
)

/** The live part of the developer's controls: who is barred, and the settings. */
class AdminState {
    /** Player id → why. */
    val banned = ConcurrentHashMap<String, String>()

    @Volatile var settings = AdminSettings()

    fun isBanned(playerId: String?): Boolean = playerId != null && banned.containsKey(playerId)

    /** The build below which an app must update: the page's choice or version.json's, whichever is higher. */
    fun minimumApp(): Int = maxOf(LatestApp.minimum, settings.minAppVersion)

    suspend fun load(records: Records) {
        banned.putAll(records.bannedPlayers())
        val saved = records.loadSettings()
        settings = AdminSettings(
            minAppVersion = saved["minAppVersion"]?.toIntOrNull() ?: 0,
            remindersOn = saved["remindersOn"]?.toBooleanStrictOrNull() ?: true,
        )
    }
}
