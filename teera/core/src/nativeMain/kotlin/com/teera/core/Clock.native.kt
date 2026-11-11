package com.teera.core

@Suppress("DEPRECATION")
internal actual fun currentTimeMillis(): Long = kotlin.system.getTimeMillis()
