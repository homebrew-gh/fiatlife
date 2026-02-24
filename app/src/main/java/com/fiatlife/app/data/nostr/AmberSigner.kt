package com.fiatlife.app.data.nostr

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "AmberSigner"

/**
 * NIP-55 signer that delegates signing and encryption to the Amber app.
 * Prefers the Content Resolver approach (silent, no UI) and falls back to
 * Intent-based approach (launches Amber activity) when the resolver returns null.
 */
class AmberSigner(
    private val activity: ComponentActivity,
    override val pubkeyHex: String,
    private val signerPackage: String,
) : NostrSigner {

    private val launchDecryptRef = AtomicReference<(Intent) -> Unit>({})
    private val launchEncryptRef = AtomicReference<(Intent) -> Unit>({})
    private val launchSignRef = AtomicReference<(Intent) -> Unit>({})

    private val pendingDecrypt = AtomicReference<Continuation<String?>?>(null)
    private val pendingEncrypt = AtomicReference<Continuation<String?>?>(null)
    private val pendingSign = AtomicReference<Continuation<String?>?>(null)
    private val lastSignError = AtomicReference<String?>(null)

    private val decryptMutex = Mutex()
    private val encryptMutex = Mutex()
    private val signMutex = Mutex()

    fun setLaunchDecrypt(launch: (Intent) -> Unit) { launchDecryptRef.set(launch) }
    fun setLaunchEncrypt(launch: (Intent) -> Unit) { launchEncryptRef.set(launch) }
    fun setLaunchSign(launch: (Intent) -> Unit) { launchSignRef.set(launch) }

    fun onDecryptResult(result: ActivityResult) {
        val cont = pendingDecrypt.getAndSet(null)
        if (cont == null) {
            Log.w(TAG, "onDecryptResult: no pending continuation")
            return
        }
        val plain = if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("result")?.takeIf { it.isNotBlank() }
        } else null
        Log.d(TAG, "onDecryptResult: got ${if (plain != null) "${plain.length} chars" else "null"}")
        (cont as Continuation<String?>).resume(plain)
    }

    fun onEncryptResult(result: ActivityResult) {
        val cont = pendingEncrypt.getAndSet(null)
        if (cont == null) {
            Log.w(TAG, "onEncryptResult: no pending continuation")
            return
        }
        val cipher = if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("result")?.takeIf { it.isNotBlank() }
        } else null
        Log.d(TAG, "onEncryptResult: got ${if (cipher != null) "${cipher.length} chars" else "null"}")
        (cont as Continuation<String?>).resume(cipher)
    }

    fun onSignResult(result: ActivityResult) {
        val cont = pendingSign.getAndSet(null)
        if (cont == null) {
            Log.w(TAG, "onSignResult: no pending continuation")
            return
        }
        val event = result.data?.getStringExtra("event")?.takeIf { it.isNotBlank() }
        val res = result.data?.getStringExtra("result")?.takeIf { it.isNotBlank() }
        val err = result.data?.getStringExtra("error")?.takeIf { it.isNotBlank() }
        val msg = result.data?.getStringExtra("message")?.takeIf { it.isNotBlank() }
        val candidate = event ?: res
        val signed = if (result.resultCode == android.app.Activity.RESULT_OK &&
            candidate != null &&
            looksLikeSignedEventJson(candidate)
        ) {
            lastSignError.set(null)
            candidate
        } else {
            val reason = err ?: msg ?: res ?: event ?: "Signer rejected/cancelled or unsupported request."
            lastSignError.set(reason)
            null
        }
        Log.d(TAG, "onSignResult: got ${if (signed != null) "${signed.length} chars" else "null"}")
        (cont as Continuation<String?>).resume(signed)
    }

    // ── NostrSigner implementation ──

    override suspend fun signEvent(unsignedEventJson: String): String? {
        lastSignError.set(null)
        val fromResolver = withContext(Dispatchers.IO) { resolverSignEvent(unsignedEventJson) }
        if (fromResolver != null && looksLikeSignedEventJson(fromResolver)) return fromResolver
        if (fromResolver != null) {
            lastSignError.set(fromResolver)
        }
        Log.d(TAG, "signEvent: content resolver returned null, falling back to intent")
        return signViaIntent(unsignedEventJson)
    }

    fun consumeLastSignError(): String? = lastSignError.getAndSet(null)

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String? {
        val fromResolver = withContext(Dispatchers.IO) { resolverEncrypt(plaintext, peerPubkeyHex) }
        if (fromResolver != null) return fromResolver
        Log.d(TAG, "nip44Encrypt: content resolver returned null, falling back to intent")
        return encryptViaIntent(plaintext, peerPubkeyHex)
    }

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String? {
        val fromResolver = withContext(Dispatchers.IO) { resolverDecrypt(ciphertext, peerPubkeyHex) }
        if (fromResolver != null) return fromResolver
        Log.d(TAG, "nip44Decrypt: content resolver returned null, falling back to intent")
        return decryptViaIntent(ciphertext, peerPubkeyHex)
    }

    // ── Content Resolver approach (silent, no Amber UI) ──

    private fun resolverDecrypt(ciphertext: String, otherPubkeyHex: String): String? = try {
        val uri = Uri.parse("content://$signerPackage.NIP44_DECRYPT")
        val cursor = activity.contentResolver.query(
            uri, arrayOf(ciphertext, otherPubkeyHex, pubkeyHex), null, null, null
        )
        cursor?.use {
            if (it.getColumnIndex("rejected") >= 0) return null
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("result")
                if (idx >= 0) it.getString(idx)?.takeIf { s -> s.isNotBlank() } else null
            } else null
        }
    } catch (e: Throwable) {
        Log.d(TAG, "resolverDecrypt: not available (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private fun resolverEncrypt(plaintext: String, otherPubkeyHex: String): String? = try {
        val uri = Uri.parse("content://$signerPackage.NIP44_ENCRYPT")
        val cursor = activity.contentResolver.query(
            uri, arrayOf(plaintext, otherPubkeyHex, pubkeyHex), null, null, null
        )
        cursor?.use {
            if (it.getColumnIndex("rejected") >= 0) return null
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("result")
                if (idx >= 0) it.getString(idx)?.takeIf { s -> s.isNotBlank() } else null
            } else null
        }
    } catch (e: Throwable) {
        Log.d(TAG, "resolverEncrypt: not available (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private fun resolverSignEvent(unsignedEventJson: String): String? = try {
        val uri = Uri.parse("content://$signerPackage.SIGN_EVENT")
        val cursor = activity.contentResolver.query(
            uri, arrayOf(unsignedEventJson, "", pubkeyHex), null, null, null
        )
        cursor?.use {
            if (it.getColumnIndex("rejected") >= 0) return null
            if (it.moveToFirst()) {
                val eventIdx = it.getColumnIndex("event")
                val resultIdx = it.getColumnIndex("result")
                val event = if (eventIdx >= 0) it.getString(eventIdx)?.takeIf { s -> s.isNotBlank() } else null
                val result = if (resultIdx >= 0) it.getString(resultIdx)?.takeIf { s -> s.isNotBlank() } else null
                event ?: result
            } else null
        }
    } catch (e: Throwable) {
        Log.d(TAG, "resolverSignEvent: not available (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    // ── Intent-based fallback (launches Amber activity) ──

    private suspend fun decryptViaIntent(ciphertext: String, otherPubkeyHex: String): String? =
        decryptMutex.withLock {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    pendingDecrypt.set(cont as Continuation<String?>)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${Uri.encode(ciphertext)}"))
                    intent.`package` = signerPackage
                    intent.putExtra("type", "nip44_decrypt")
                    intent.putExtra("current_user", pubkeyHex)
                    intent.putExtra("pubkey", otherPubkeyHex)
                    intent.putExtra("id", "decrypt_${System.currentTimeMillis()}")
                    launchDecryptRef.get().invoke(intent)
                }
            }
        }

    private suspend fun encryptViaIntent(plaintext: String, otherPubkeyHex: String): String? =
        encryptMutex.withLock {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    pendingEncrypt.set(cont as Continuation<String?>)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${Uri.encode(plaintext)}"))
                    intent.`package` = signerPackage
                    intent.putExtra("type", "nip44_encrypt")
                    intent.putExtra("current_user", pubkeyHex)
                    intent.putExtra("pubkey", otherPubkeyHex)
                    intent.putExtra("id", "encrypt_${System.currentTimeMillis()}")
                    launchEncryptRef.get().invoke(intent)
                }
            }
        }

    private suspend fun signViaIntent(unsignedEventJson: String): String? =
        signMutex.withLock {
            val first = requestSignIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                    `package` = signerPackage
                    putExtra("type", "sign_event")
                    putExtra("current_user", pubkeyHex)
                    putExtra("event", unsignedEventJson)
                    putExtra("unsigned_event", unsignedEventJson)
                    putExtra("id", "sign_${System.currentTimeMillis()}")
                }
            )
            if (first != null) return@withLock first

            // Fallback for Amber variants expecting encoded payload in URI.
            requestSignIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${Uri.encode(unsignedEventJson)}")).apply {
                    `package` = signerPackage
                    putExtra("type", "sign_event")
                    putExtra("current_user", pubkeyHex)
                    putExtra("unsigned_event", unsignedEventJson)
                    putExtra("id", "sign_uri_${System.currentTimeMillis()}")
                }
            )
        }

    private suspend fun requestSignIntent(intent: Intent): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                pendingSign.set(cont as Continuation<String?>)
                launchSignRef.get().invoke(intent)
            }
        }

    private fun looksLikeSignedEventJson(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("{") &&
            trimmed.contains("\"id\"") &&
            trimmed.contains("\"sig\"") &&
            trimmed.contains("\"kind\"")
    }
}
