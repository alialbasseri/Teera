package com.teera.core

/** Milliseconds since 1970, by the wall clock: the JVM's and iOS's own (Clock.jvm.kt, Clock.native.kt). */
internal expect fun currentTimeMillis(): Long
