package com.teera.server

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The notification sender against a stand-in for Google: it must sign in with a token
 * Google would accept (signed with the service account's key), then send the message the
 * phone expects, and drop an address Google says is gone.
 */
class FcmTest {
    @Test
    fun `signs in with the service account and sends the notification to the phone`() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keys.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"

        val assertions = CopyOnWriteArrayList<String>()
        val sent = CopyOnWriteArrayList<String>()
        val auth = CopyOnWriteArrayList<String>()
        var gone = false
        val google = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/token") { exchange ->
                val form = exchange.requestBody.readBytes().decodeToString()
                assertions += URLDecoder.decode(form.substringAfter("assertion="), Charsets.UTF_8)
                val answer = """{"access_token":"pass-123","expires_in":3600}""".toByteArray()
                exchange.sendResponseHeaders(200, answer.size.toLong())
                exchange.responseBody.use { it.write(answer) }
            }
            createContext("/v1/projects/teera-test/messages:send") { exchange ->
                auth += exchange.requestHeaders.getFirst("Authorization")
                sent += exchange.requestBody.readBytes().decodeToString()
                val answer = (if (gone) """{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}""" else "{}").toByteArray()
                exchange.sendResponseHeaders(if (gone) 404 else 200, answer.size.toLong())
                exchange.responseBody.use { it.write(answer) }
            }
            start()
        }
        try {
            val base = "http://127.0.0.1:${google.address.port}"
            val account = Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("service_account"))
                    put("project_id", kotlinx.serialization.json.JsonPrimitive("teera-test"))
                    put("client_email", kotlinx.serialization.json.JsonPrimitive("push@teera-test.iam.gserviceaccount.com"))
                    put("private_key", kotlinx.serialization.json.JsonPrimitive(pem))
                    put("token_uri", kotlinx.serialization.json.JsonPrimitive("$base/token"))
                },
            )
            val saved = HashMap<String, String?>()
            val pusher = FcmPusher(account, object : TokenStore {
                override fun save(playerId: String, token: String?) { saved[playerId] = token }
            }, fcmBase = base)

            assertFalse(pusher.canReach("ali"))
            pusher.remember("ali", "phone-token-1")
            assertTrue(pusher.canReach("ali"))
            pusher.notify("ali", "عزيمة على طيرة", "سارة يعزمك على طاولته", mapOf("code" to "KHAN"))
            pusher.notify("ali", "دورك بطيرة!", "الطاولة تنتظرك", mapOf("code" to "KHAN"))
            pusher.flush()

            // One sign-in for both: the pass is kept until near its end.
            assertEquals(1, assertions.size)
            val (header, claims, signature) = assertions.single().split(".")
            val verifier = Signature.getInstance("SHA256withRSA").apply {
                initVerify(keys.public)
                update("$header.$claims".toByteArray())
            }
            assertTrue(verifier.verify(Base64.getUrlDecoder().decode(signature)), "Google would accept the signature")
            val claimed = Json.parseToJsonElement(Base64.getUrlDecoder().decode(claims).decodeToString()).jsonObject
            assertEquals("push@teera-test.iam.gserviceaccount.com", claimed["iss"]!!.jsonPrimitive.content)
            assertEquals("https://www.googleapis.com/auth/firebase.messaging", claimed["scope"]!!.jsonPrimitive.content)

            assertEquals(listOf("Bearer pass-123", "Bearer pass-123"), auth.toList())
            val message = Json.parseToJsonElement(sent.first()).jsonObject["message"]!!.jsonObject
            assertEquals("phone-token-1", message["token"]!!.jsonPrimitive.content)
            assertEquals("سارة يعزمك على طاولته", message["notification"]!!.jsonObject["body"]!!.jsonPrimitive.content)
            assertEquals("KHAN", message["data"]!!.jsonObject["code"]!!.jsonPrimitive.content)
            assertEquals("teera", message["android"]!!.jsonObject["notification"]!!.jsonObject["channel_id"]!!.jsonPrimitive.content)
            assertEquals("phone-token-1", saved["ali"], "kept for after a restart")

            // The app was removed: Google says so, and the address is dropped.
            gone = true
            pusher.notify("ali", "x", "y")
            pusher.flush()
            assertFalse(pusher.canReach("ali"))
            assertEquals(null, saved["ali"])
        } finally {
            google.stop(0)
        }
    }
}
