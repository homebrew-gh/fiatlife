package com.fiatlife.app.data.blossom

import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.data.nostr.NostrSigner
import com.fiatlife.app.data.nostr.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BlobDescriptor(
    val url: String = "",
    val sha256: String = "",
    val size: Long = 0,
    val type: String = "",
    val uploaded: Long = 0
)

@Singleton
class BlossomClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var serverUrl: String = ""
    private var signer: NostrSigner? = null

    fun configure(serverUrl: String, signer: NostrSigner) {
        this.serverUrl = serverUrl.trimEnd('/')
        this.signer = signer
    }

    fun isConfigured(): Boolean = serverUrl.isNotEmpty() && signer != null

    suspend fun uploadBlob(
        data: ByteArray,
        contentType: String = "application/octet-stream",
        filename: String? = null
    ): Result<BlobDescriptor> = withContext(Dispatchers.IO) {
        try {
            val s = signer ?: return@withContext Result.failure(
                IllegalStateException("Not configured")
            )

            val sha256 = sha256Hex(data)
            val authHeader = createAuthHeader(s, "upload", sha256)
                ?: return@withContext Result.failure(IOException("Failed to create auth header"))

            val requestBuilder = Request.Builder()
                .url("$serverUrl/upload")
                .header("Authorization", "Nostr $authHeader")
                .put(data.toRequestBody(contentType.toMediaType()))

            if (filename != null) {
                requestBuilder.header("X-Filename", filename)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Upload failed: ${response.code} ${response.body?.string()}")
                )
            }

            val body = response.body?.string() ?: return@withContext Result.failure(
                IOException("Empty response")
            )
            val descriptor = Json.decodeFromString<BlobDescriptor>(body)
            Result.success(descriptor)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBlob(sha256: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val s = signer ?: return@withContext Result.failure(
                IllegalStateException("Not configured")
            )

            val authHeader = createAuthHeader(s, "get", sha256)
                ?: return@withContext Result.failure(IOException("Failed to create auth header"))

            val request = Request.Builder()
                .url("$serverUrl/$sha256")
                .header("Authorization", "Nostr $authHeader")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Download failed: ${response.code}")
                )
            }

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(
                IOException("Empty response")
            )
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBlobs(pubkey: String): Result<List<BlobDescriptor>> = withContext(Dispatchers.IO) {
        try {
            val s = signer ?: return@withContext Result.failure(
                IllegalStateException("Not configured")
            )

            val authHeader = createAuthHeader(s, "list", null)
                ?: return@withContext Result.failure(IOException("Failed to create auth header"))

            val request = Request.Builder()
                .url("$serverUrl/list/$pubkey")
                .header("Authorization", "Nostr $authHeader")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("List failed: ${response.code}")
                )
            }

            val body = response.body?.string() ?: "[]"
            val blobs = Json.decodeFromString<List<BlobDescriptor>>(body)
            Result.success(blobs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBlob(sha256: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val s = signer ?: return@withContext Result.failure(
                IllegalStateException("Not configured")
            )

            val authHeader = createAuthHeader(s, "delete", sha256)
                ?: return@withContext Result.failure(IOException("Failed to create auth header"))

            val request = Request.Builder()
                .url("$serverUrl/$sha256")
                .header("Authorization", "Nostr $authHeader")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Delete failed: ${response.code}")
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createAuthHeader(
        signer: NostrSigner,
        action: String,
        sha256: String?
    ): String? {
        val tags = mutableListOf(
            listOf("t", action),
            listOf("expiration", ((System.currentTimeMillis() / 1000) + 300).toString())
        )
        if (sha256 != null) {
            tags.add(listOf("x", sha256))
        }

        val unsignedJson = NostrEvent.buildUnsignedJson(
            pubkeyHex = signer.pubkeyHex,
            kind = 24242,
            content = "Authorize $action",
            tags = tags
        )

        val signedJson = signer.signEvent(unsignedJson) ?: return null
        return android.util.Base64.encodeToString(
            signedJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
    }

    private fun sha256Hex(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(data).toHex()
    }
}
