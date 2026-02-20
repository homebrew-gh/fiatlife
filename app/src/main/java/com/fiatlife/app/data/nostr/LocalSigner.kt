package com.fiatlife.app.data.nostr

import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * [NostrSigner] backed by a local secp256k1 private key.
 * All signing and encryption happens in-process.
 */
class LocalSigner(private val privateKey: ByteArray) : NostrSigner {

    override val pubkeyHex: String = run {
        val secp256k1 = Secp256k1.get()
        secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privateKey))
            .drop(1).toByteArray().toHex()
    }

    override suspend fun signEvent(unsignedEventJson: String): String? = try {
        val json = Json.parseToJsonElement(unsignedEventJson).jsonObject
        val pubkey = json["pubkey"]?.jsonPrimitive?.content ?: pubkeyHex
        val createdAt = json["created_at"]?.jsonPrimitive?.long ?: (System.currentTimeMillis() / 1000)
        val kind = json["kind"]?.jsonPrimitive?.int ?: return null
        val tags = json["tags"]?.jsonArray ?: buildJsonArray {}
        val content = json["content"]?.jsonPrimitive?.content ?: ""

        val serializedForId = buildJsonArray {
            add(0)
            add(pubkey)
            add(createdAt)
            add(kind)
            add(tags)
            add(content)
        }.toString()

        val idBytes = MessageDigest.getInstance("SHA-256").digest(serializedForId.toByteArray())
        val secp256k1 = Secp256k1.get()
        val sig = secp256k1.signSchnorr(idBytes, privateKey, null).toHex()

        buildJsonObject {
            put("id", idBytes.toHex())
            put("pubkey", pubkey)
            put("created_at", createdAt)
            put("kind", kind)
            put("tags", tags)
            put("content", content)
            put("sig", sig)
        }.toString()
    } catch (_: Exception) {
        null
    }

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String? = try {
        Nip44Encryption.encrypt(plaintext, privateKey, peerPubkeyHex.hexToByteArray())
    } catch (_: Exception) {
        null
    }

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String? = try {
        Nip44Encryption.decrypt(ciphertext, privateKey, peerPubkeyHex.hexToByteArray())
    } catch (_: Exception) {
        null
    }
}
