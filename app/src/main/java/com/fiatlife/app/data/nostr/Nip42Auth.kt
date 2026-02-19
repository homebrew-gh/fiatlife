package com.fiatlife.app.data.nostr

import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.json.*

/**
 * NIP-42 Authentication handler.
 * Responds to relay AUTH challenges by signing an authentication event.
 */
object Nip42Auth {

    private const val KIND_AUTH = 22242

    fun createAuthEvent(
        privateKey: ByteArray,
        challenge: String,
        relayUrl: String
    ): NostrEvent {
        return NostrEvent.create(
            privateKey = privateKey,
            kind = KIND_AUTH,
            content = "",
            tags = listOf(
                listOf("relay", relayUrl),
                listOf("challenge", challenge)
            )
        )
    }

    fun buildAuthMessage(event: NostrEvent): String {
        val json = buildJsonArray {
            add("AUTH")
            add(Json.encodeToJsonElement(event))
        }
        return json.toString()
    }

    fun parseAuthChallenge(message: String): String? {
        return try {
            val array = Json.parseToJsonElement(message).jsonArray
            if (array[0].jsonPrimitive.content == "AUTH") {
                array[1].jsonPrimitive.content
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
