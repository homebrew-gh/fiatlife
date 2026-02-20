package com.fiatlife.app.data.nostr

/**
 * Abstraction for Nostr signing and encryption operations.
 * Implementations: [LocalSigner] (raw private key) and [AmberSigner] (NIP-55 external signer).
 * The private key never leaves the signer implementation.
 */
interface NostrSigner {
    val pubkeyHex: String

    /**
     * Sign an unsigned Nostr event JSON (contains pubkey, created_at, kind, tags, content
     * but id and sig are empty). Returns full signed event JSON or null on failure/rejection.
     */
    suspend fun signEvent(unsignedEventJson: String): String?

    /**
     * NIP-44 encrypt plaintext for the given peer pubkey (hex, 64 chars, x-only).
     * For self-encryption, pass our own pubkey.
     */
    suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String?

    /**
     * NIP-44 decrypt ciphertext from the given peer pubkey (hex, 64 chars, x-only).
     * For self-decryption, pass our own pubkey.
     */
    suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String?
}
