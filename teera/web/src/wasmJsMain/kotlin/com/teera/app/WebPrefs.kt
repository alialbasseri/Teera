package com.teera.app

import kotlinx.browser.localStorage

/**
 * The player's settings in this browser (its local storage). It answers the same calls as
 * the phone's SharedPreferences, so the shared view model reads and writes it unchanged.
 */
class WebPrefs {
    private fun key(name: String) = "teera.$name"

    fun getString(key: String, default: String?): String? = localStorage.getItem(key(key)) ?: default
    fun getInt(key: String, default: Int): Int = localStorage.getItem(key(key))?.toIntOrNull() ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = localStorage.getItem(key(key))?.toBooleanStrictOrNull() ?: default

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = mutableListOf<() -> Unit>()

        fun putString(key: String, value: String?): Editor = apply {
            changes += { if (value == null) localStorage.removeItem(key(key)) else localStorage.setItem(key(key), value) }
        }
        fun putInt(key: String, value: Int): Editor = apply { changes += { localStorage.setItem(key(key), value.toString()) } }
        fun putBoolean(key: String, value: Boolean): Editor = apply { changes += { localStorage.setItem(key(key), value.toString()) } }
        fun remove(key: String): Editor = apply { changes += { localStorage.removeItem(key(key)) } }

        // A full or blocked storage (a private window) loses the setting, never the game.
        fun apply() = changes.forEach { runCatching { it() } }
        fun commit(): Boolean {
            apply()
            return true
        }
    }
}
