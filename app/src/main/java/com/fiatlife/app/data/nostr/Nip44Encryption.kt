package com.fiatlife.app.data.nostr

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.sun.jna.ptr.LongByReference
import fr.acinq.secp256k1.Secp256k1
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NIP-44 v2 encryption/decryption using XChaCha20-Poly1305 with
 * HKDF-SHA256 key derivation and secp256k1 ECDH shared secret.
 */
object Nip44Encryption {

    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val secp256k1 = Secp256k1.get()
    private val secureRandom = SecureRandom()

    private const val VERSION: Byte = 2
    private const val NONCE_SIZE = 24
    private const val POLY1305_TAG_SIZE = 16

    fun encrypt(
        plaintext: String,
        privateKey: ByteArray,
        recipientPubKey: ByteArray
    ): String {
        val conversationKey = deriveConversationKey(privateKey, recipientPubKey)
        val nonce = ByteArray(NONCE_SIZE).also { secureRandom.nextBytes(it) }
        val (chachaKey, chachaNonce, hmacKey) = deriveMessageKeys(conversationKey, nonce)

        val padded = padPlaintext(plaintext.toByteArray(Charsets.UTF_8))

        val ciphertext = ByteArray(padded.size + POLY1305_TAG_SIZE)
        val ciphertextLen = LongByReference(0)

        val success = sodium.sodiumJNI.crypto_aead_xchacha20poly1305_ietf_encrypt(
            ciphertext, ciphertextLen,
            padded, padded.size.toLong(),
            null, 0L,
            null,
            chachaNonce,
            chachaKey
        )
        check(success == 0) { "Encryption failed" }

        val encrypted = ciphertext.copyOf(ciphertextLen.value.toInt())

        val payload = ByteBuffer.allocate(1 + NONCE_SIZE + encrypted.size)
            .put(VERSION)
            .put(nonce)
            .put(encrypted)
            .array()

        val mac = hmacSha256(hmacKey, payload)

        val result = ByteBuffer.allocate(payload.size + mac.size)
            .put(payload)
            .put(mac)
            .array()

        return android.util.Base64.encodeToString(result, android.util.Base64.NO_WRAP)
    }

    fun decrypt(
        payload: String,
        privateKey: ByteArray,
        senderPubKey: ByteArray
    ): String {
        val data = android.util.Base64.decode(payload, android.util.Base64.NO_WRAP)

        require(data[0] == VERSION) { "Unsupported NIP-44 version: ${data[0]}" }

        val conversationKey = deriveConversationKey(privateKey, senderPubKey)
        val nonce = data.sliceArray(1 until 1 + NONCE_SIZE)
        val encrypted = data.sliceArray(1 + NONCE_SIZE until data.size - 32)
        val receivedMac = data.sliceArray(data.size - 32 until data.size)

        val (chachaKey, chachaNonce, hmacKey) = deriveMessageKeys(conversationKey, nonce)

        val macPayload = data.sliceArray(0 until data.size - 32)
        val computedMac = hmacSha256(hmacKey, macPayload)
        require(computedMac.contentEquals(receivedMac)) { "MAC verification failed" }

        val decrypted = ByteArray(encrypted.size)
        val decryptedLen = LongByReference(0)

        val result = sodium.sodiumJNI.crypto_aead_xchacha20poly1305_ietf_decrypt(
            decrypted, decryptedLen,
            null,
            encrypted, encrypted.size.toLong(),
            null, 0L,
            chachaNonce,
            chachaKey
        )

        check(result == 0) { "Decryption failed" }

        return unpadPlaintext(decrypted.copyOf(decryptedLen.value.toInt()))
    }

    fun encryptToSelf(plaintext: String, privateKey: ByteArray): String {
        val pubKey = derivePubKey(privateKey)
        return encrypt(plaintext, privateKey, pubKey)
    }

    fun decryptFromSelf(payload: String, privateKey: ByteArray): String {
        val pubKey = derivePubKey(privateKey)
        return decrypt(payload, privateKey, pubKey)
    }

    private fun derivePubKey(privateKey: ByteArray): ByteArray {
        val compressed = secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(privateKey))
        return compressed.drop(1).toByteArray()
    }

    private fun deriveConversationKey(
        privateKey: ByteArray,
        pubKey: ByteArray
    ): ByteArray {
        val fullPubKey = byteArrayOf(0x02) + pubKey
        val sharedPoint = secp256k1.ecdh(privateKey, fullPubKey)
        return hkdfExtract(
            salt = "nip44-v2".toByteArray(Charsets.UTF_8),
            ikm = sharedPoint
        )
    }

    private data class MessageKeys(
        val chachaKey: ByteArray,
        val chachaNonce: ByteArray,
        val hmacKey: ByteArray
    )

    private fun deriveMessageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        val expanded = hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(
            chachaKey = expanded.sliceArray(0 until 32),
            chachaNonce = expanded.sliceArray(32 until 56),
            hmacKey = expanded.sliceArray(56 until 76)
        )
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        return hmacSha256(salt, ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        var t = ByteArray(0)
        val okm = ByteBuffer.allocate(n * hashLen)

        for (i in 1..n) {
            val input = t + info + byteArrayOf(i.toByte())
            t = hmacSha256(prk, input)
            okm.put(t)
        }

        return okm.array().copyOf(length)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun padPlaintext(plaintext: ByteArray): ByteArray {
        val unpaddedLen = plaintext.size
        require(unpaddedLen in 1..65535) { "Plaintext too long" }

        val paddedLen = calcPaddedLen(unpaddedLen)
        val result = ByteBuffer.allocate(2 + paddedLen)
        result.putShort(unpaddedLen.toShort())
        result.put(plaintext)
        result.put(ByteArray(paddedLen - unpaddedLen))
        return result.array()
    }

    private fun unpadPlaintext(padded: ByteArray): String {
        val len = ByteBuffer.wrap(padded, 0, 2).short.toInt() and 0xFFFF
        require(len > 0 && len <= padded.size - 2) { "Invalid padding" }
        return String(padded, 2, len, Charsets.UTF_8)
    }

    private fun calcPaddedLen(unpaddedLen: Int): Int {
        if (unpaddedLen <= 32) return 32
        val nextPower = Integer.highestOneBit(unpaddedLen - 1) shl 1
        val chunk = nextPower / 8
        return ((unpaddedLen + chunk - 1) / chunk) * chunk
    }
}
