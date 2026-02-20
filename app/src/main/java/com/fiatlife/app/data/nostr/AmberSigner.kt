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
        val signed = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val event = result.data?.getStringExtra("event")?.takeIf { it.isNotBlank() }
            val res = result.data?.getStringExtra("result")?.takeIf { it.isNotBlank() }
            event ?: res
        } else null
        Log.d(TAG, "onSignResult: got ${if (signed != null) "${signed.length} chars" else "null"}")
        (cont as Continuation<String?>).resume(signed)
    }

    // ── NostrSigner implementation ──

    override suspend fun signEvent(unsignedEventJson: String): String? {
        val fromResolver = withContext(Dispatchers.IO) { resolverSignEvent(unsignedEventJson) }
        if (fromResolver != null) return fromResolver
        Log.d(TAG, "signEvent: content resolver returned null, falling back to intent")
        return signViaIntent(unsignedEventJson)
    }

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
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    pendingSign.set(cont as Continuation<String?>)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${Uri.encode(unsignedEventJson)}"))
                    intent.`package` = signerPackage
                    intent.putExtra("type", "sign_event")
                    intent.putExtra("current_user", pubkeyHex)
                    intent.putExtra("id", "sign_${System.currentTimeMillis()}")
                    launchSignRef.get().invoke(intent)
                }
            }
        }
}
