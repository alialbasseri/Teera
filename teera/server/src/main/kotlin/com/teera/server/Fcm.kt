package com.teera.server

import com.teera.room.Pusher
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Fcm")

/** Where the phones' notification addresses are kept between restarts. */
interface TokenStore {
    fun save(playerId: String, token: String?) {}
    fun load(playerId: String): String? = null
}

/**
 * Sends notifications through Firebase Cloud Messaging (its HTTP v1 interface), signed
 * in with the project's service account: the key Ali put in Render, never in the code.
 * Everything happens on one background thread, so a slow Google never holds a table up.
 */
class FcmPusher(
    serviceAccount: String,
    private val store: TokenStore = object : TokenStore {},
    /** Google's messaging address; a test points it at a stand-in. */
    private val fcmBase: String = "https://fcm.googleapis.com",
) : Pusher {
    private val account = Json.parseToJsonElement(serviceAccount).jsonObject
    private val email = account.text("client_email")
    private val project = account.text("project_id")
    private val tokenUri = account["token_uri"]?.jsonPrimitive?.content ?: "https://oauth2.googleapis.com/token"
    private val key = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(
                account.text("private_key")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), ""),
            ),
        ),
    )

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val sender = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(1_000),
        { task -> Thread(task, "push").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy(),
    )
    private val tokens = ConcurrentHashMap<String, String>()

    @Volatile private var access: String? = null
    @Volatile private var accessUntil = 0L

    override fun remember(playerId: String, token: String) {
        if (tokens.put(playerId, token) != token) sender.execute { runCatching { store.save(playerId, token) } }
    }

    override fun canReach(playerId: String): Boolean = tokenOf(playerId) != null

    private fun tokenOf(playerId: String): String? =
        tokens[playerId] ?: runCatching { store.load(playerId) }.getOrNull()?.also { tokens[playerId] = it }

    /** Waits for every notification so far to go out. For tests. */
    fun flush() {
        sender.submit {}.get(30, TimeUnit.SECONDS)
    }

    override fun notify(playerId: String, title: String, body: String, data: Map<String, String>) {
        sender.execute {
            try {
                val token = tokenOf(playerId) ?: return@execute
                val message = buildJsonObject {
                    putJsonObject("message") {
                        put("token", token)
                        putJsonObject("notification") {
                            put("title", title)
                            put("body", body)
                        }
                        putJsonObject("data") { data.forEach { (k, v) -> put(k, v) } }
                        putJsonObject("android") {
                            put("priority", "HIGH")
                            putJsonObject("notification") {
                                put("channel_id", "teera")
                                put("sound", "default")
                            }
                        }
                    }
                }
                val response = http.send(
                    HttpRequest.newBuilder(URI("$fcmBase/v1/projects/$project/messages:send"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer ${accessToken()}")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(message.toString()))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                when {
                    response.statusCode() in 200..299 -> Unit
                    // The app was removed, or the address went stale: stop using it.
                    response.statusCode() == 404 || "UNREGISTERED" in response.body() -> {
                        tokens.remove(playerId)
                        runCatching { store.save(playerId, null) }
                    }
                    else -> log.warn("notification refused: {} {}", response.statusCode(), response.body().take(300))
                }
            } catch (e: Exception) {
                log.warn("could not send a notification: {}", e.message)
            }
        }
    }

    /** A short-lived pass from Google, made from the service account and kept until near its end. */
    private fun accessToken(): String {
        val now = System.currentTimeMillis()
        access?.takeIf { now < accessUntil }?.let { return it }
        val seconds = now / 1000
        val header = encode("""{"alg":"RS256","typ":"JWT"}""")
        val claims = encode(
            buildJsonObject {
                put("iss", email)
                put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                put("aud", tokenUri)
                put("iat", seconds)
                put("exp", seconds + 3600)
            }.toString(),
        )
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update("$header.$claims".toByteArray())
        }
        val jwt = "$header.$claims.${Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())}"
        val response = http.send(
            HttpRequest.newBuilder(URI(tokenUri))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", Charsets.UTF_8) +
                            "&assertion=" + jwt,
                    ),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() == 200) { "Google refused the service account: ${response.statusCode()}" }
        val answer = Json.parseToJsonElement(response.body()).jsonObject
        val token = answer.text("access_token")
        val lasts = answer["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
        access = token
        accessUntil = now + (lasts - 300) * 1000
        return token
    }

    /** A JWT with [JsonObject] parts, made by hand: base64url, no padding. */
    private fun encode(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())

    private fun JsonObject.text(name: String) =
        get(name)?.jsonPrimitive?.content ?: error("the service account has no $name")
}
