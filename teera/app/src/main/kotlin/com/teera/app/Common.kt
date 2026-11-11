package com.teera.app

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/* Small helpers every platform shares, from Kotlin's own library (no Java underneath). */

@OptIn(ExperimentalEncodingApi::class)
fun base64(bytes: ByteArray): String = Base64.Default.encode(bytes)

/** Lenient about line breaks and stray characters, as photos from older phones come wrapped. */
@OptIn(ExperimentalEncodingApi::class)
fun unbase64(text: String): ByteArray? = runCatching { Base64.Mime.decode(text) }.getOrNull()

@OptIn(ExperimentalUuidApi::class)
fun randomId(): String = Uuid.random().toString()
