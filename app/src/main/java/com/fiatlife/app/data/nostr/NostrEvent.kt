package com.fiatlife.app.data.nostr

import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable
data class NostrEvent(
    val id: String = "",
    val pubkey: String = "",
    val created_at: Long = 0L,
    val kind: Int = 0,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
    val sig: String = ""
) {
    companion object {
        const val KIND_APP_SPECIFIC_DATA = 30078

        fun create(
            privateKey: ByteArray,
            kind: Int,
            content: String,
            tags: List<List<String>> = emptyList()
        ): NostrEvent {
            val secp256k1 = Secp256k1.get()
            val pubkey = secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privateKey))
                .drop(1).toByteArray().toHex()
            val createdAt = System.currentTimeMillis() / 1000

            val serializedForId = buildJsonArray {
                add(0)
                add(pubkey)
                add(createdAt)
                add(kind)
                add(buildJsonArray {
                    tags.forEach { tag ->
                        add(buildJsonArray { tag.forEach { add(it) } })
                    }
                })
                add(content)
            }.toString()

            val id = sha256(serializedForId.toByteArray())
            val sig = secp256k1.signSchnorr(id, privateKey, null).toHex()

            return NostrEvent(
                id = id.toHex(),
                pubkey = pubkey,
                created_at = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )
        }

        /**
         * Build an unsigned event JSON string suitable for passing to [NostrSigner.signEvent].
         * The id and sig fields are empty placeholders.
         */
        fun buildUnsignedJson(
            pubkeyHex: String,
            kind: Int,
            content: String,
            tags: List<List<String>> = emptyList()
        ): String {
            val createdAt = System.currentTimeMillis() / 1000
            return buildJsonObject {
                put("id", "")
                put("pubkey", pubkeyHex)
                put("created_at", createdAt)
                put("kind", kind)
                put("tags", buildJsonArray {
                    tags.forEach { tag ->
                        add(buildJsonArray { tag.forEach { add(it) } })
                    }
                })
                put("content", content)
                put("sig", "")
            }.toString()
        }

        fun createAppData(
            privateKey: ByteArray,
            dTag: String,
            encryptedContent: String
        ): NostrEvent {
            return create(
                privateKey = privateKey,
                kind = KIND_APP_SPECIFIC_DATA,
                content = encryptedContent,
                tags = listOf(listOf("d", dTag))
            )
        }

        private fun sha256(data: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(data)
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

@Serializable
data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    @Serializable(with = TagFilterSerializer::class)
    val tagFilters: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null
) {
    fun toJsonArray(): JsonArray = buildJsonArray {
        addJsonObject {
            ids?.let { put("ids", buildJsonArray { it.forEach { add(it) } }) }
            authors?.let { put("authors", buildJsonArray { it.forEach { add(it) } }) }
            kinds?.let { put("kinds", buildJsonArray { it.forEach { add(it) } }) }
            tagFilters.forEach { (key, values) ->
                put("#$key", buildJsonArray { values.forEach { add(it) } })
            }
            since?.let { put("since", it) }
            until?.let { put("until", it) }
            limit?.let { put("limit", it) }
        }
    }
}

object TagFilterSerializer : kotlinx.serialization.KSerializer<Map<String, List<String>>> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("TagFilter")

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Map<String, List<String>>) {
        val jsonEncoder = encoder as kotlinx.serialization.json.JsonEncoder
        jsonEncoder.encodeJsonElement(buildJsonObject {
            value.forEach { (k, v) ->
                put(k, buildJsonArray { v.forEach { add(it) } })
            }
        })
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Map<String, List<String>> {
        val jsonDecoder = decoder as kotlinx.serialization.json.JsonDecoder
        val element = jsonDecoder.decodeJsonElement().jsonObject
        return element.map { (k, v) ->
            k to v.jsonArray.map { it.jsonPrimitive.content }
        }.toMap()
    }
}
