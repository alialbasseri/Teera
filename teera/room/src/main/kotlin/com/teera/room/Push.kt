package com.teera.room

/**
 * Notifications to phones with the app closed. The cloud server sends them through
 * Firebase; a phone hosting on the Wi-Fi sends none ([NONE]). Sends must return at once
 * and never throw.
 */
interface Pusher {
    /** A phone's address for notifications, as its app reports it. */
    fun remember(playerId: String, token: String) {}

    /** Whether a notification could reach this player at all. */
    fun canReach(playerId: String): Boolean = false

    fun notify(playerId: String, title: String, body: String, data: Map<String, String> = emptyMap()) {}

    companion object {
        val NONE = object : Pusher {}
    }
}
