package com.teera.app

import java.io.File
import java.util.Properties

/**
 * The player's settings on a computer, in a small file in their profile
 * (%APPDATA%\Teera\settings.properties on Windows). It answers the same calls as the
 * phone's SharedPreferences, so the shared view model reads and writes it unchanged.
 */
class DesktopPrefs(private val file: File) {
    private val values = Properties()

    init {
        runCatching { file.inputStream().use { values.load(it.reader(Charsets.UTF_8)) } }
    }

    @Synchronized fun getString(key: String, default: String?): String? = values.getProperty(key) ?: default
    @Synchronized fun getInt(key: String, default: Int): Int = values.getProperty(key)?.toIntOrNull() ?: default
    @Synchronized fun getBoolean(key: String, default: Boolean): Boolean = values.getProperty(key)?.toBooleanStrictOrNull() ?: default

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = mutableListOf<(Properties) -> Unit>()
        fun putString(key: String, value: String?): Editor = apply {
            changes += { if (value == null) it.remove(key) else it.setProperty(key, value) }
        }
        fun putInt(key: String, value: Int): Editor = apply { changes += { it.setProperty(key, value.toString()) } }
        fun putBoolean(key: String, value: Boolean): Editor = apply { changes += { it.setProperty(key, value.toString()) } }
        fun remove(key: String): Editor = apply { changes += { it.remove(key) } }

        fun apply() = save()
        fun commit(): Boolean = runCatching { save() }.isSuccess

        private fun save() = synchronized(this@DesktopPrefs) {
            changes.forEach { it(values) }
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writer(Charsets.UTF_8).use { values.store(it, "Teera") }
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            }
        }
    }
}
