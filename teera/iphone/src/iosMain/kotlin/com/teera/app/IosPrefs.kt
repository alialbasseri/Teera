package com.teera.app

import platform.Foundation.NSUserDefaults

/**
 * The player's settings on an iPhone, in the system's own store. It answers the same calls
 * as the phone's SharedPreferences, so the shared view model reads and writes it unchanged.
 */
class IosPrefs {
    private val store = NSUserDefaults.standardUserDefaults

    private fun has(key: String) = store.objectForKey(key) != null

    fun getString(key: String, default: String?): String? = store.stringForKey(key) ?: default
    fun getInt(key: String, default: Int): Int = if (has(key)) store.integerForKey(key).toInt() else default
    fun getBoolean(key: String, default: Boolean): Boolean = if (has(key)) store.boolForKey(key) else default

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = mutableListOf<() -> Unit>()

        fun putString(key: String, value: String?): Editor = apply {
            changes += { if (value == null) store.removeObjectForKey(key) else store.setObject(value, forKey = key) }
        }
        fun putInt(key: String, value: Int): Editor = apply { changes += { store.setInteger(value.toLong(), forKey = key) } }
        fun putBoolean(key: String, value: Boolean): Editor = apply { changes += { store.setBool(value, forKey = key) } }
        fun remove(key: String): Editor = apply { changes += { store.removeObjectForKey(key) } }

        fun apply() = changes.forEach { it() }
        fun commit(): Boolean {
            apply()
            return true
        }
    }
}
