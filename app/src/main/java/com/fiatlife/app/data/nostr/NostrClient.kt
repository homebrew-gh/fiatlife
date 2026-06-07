package com.fiatlife.app.data.nostr

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NostrClient"

sealed class NostrMessage {
    data class EventReceived(val subscriptionId: String, val event: NostrEvent) : NostrMessage()
    data class Ok(val eventId: String, val success: Boolean, val message: String) : NostrMessage()
    data class Eose(val subscriptionId: String) : NostrMessage()
    data class Notice(val message: String) : NostrMessage()
    data class AuthChallenge(val challenge: String) : NostrMessage()
    data class Error(val error: Throwable) : NostrMessage()
    data object Connected : NostrMessage()
    data object Disconnected : NostrMessage()
}

data class PublishStatus(
    val success: Boolean,
    val stage: String,
    val detail: String = ""
)

/** Background relay-publish queue state (drives the "not synced" badge). */
data class OutboxState(
    val pending: Int = 0,
    val failed: Int = 0,
)

@Singleton
class NostrClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private var relayUrl: String = ""
    private var signer: NostrSigner? = null
    private var isAuthenticated = false
    private var authInFlight = false

    /** Buffer must be large enough to avoid dropping events when relay sends many at once. */
    private val _messages = MutableSharedFlow<NostrMessage>(extraBufferCapacity = 512)
    val messages: SharedFlow<NostrMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val pendingQueue = Channel<String>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Background relay-publish outbox ---------------------------------
    // Events are signed synchronously (so signer rejections still surface to
    // the caller) but the relay round-trip happens here in the background with
    // retry + backoff, so the UI is never blocked waiting on the network.
    private val _outbox = MutableStateFlow(OutboxState())
    val outbox: StateFlow<OutboxState> = _outbox.asStateFlow()

    private data class OutboxJob(val id: Long, val label: String, val signedJson: String)
    private val outboxMutex = Mutex()
    private val failedJobs = mutableListOf<OutboxJob>()
    private var outboxSeq = 0L
    private val outboxBackoffsMs = longArrayOf(1000, 3000, 8000, 20000)

    val hasSigner: Boolean get() = signer != null
    val currentSigner: NostrSigner? get() = signer

    fun setSigner(signer: NostrSigner) {
        this.signer = signer
    }

    fun connect(relayUrl: String) {
        val s = signer ?: return
        connect(relayUrl, s)
    }

    fun connect(relayUrl: String, signer: NostrSigner) {
        if (_connectionState.value && this.relayUrl == relayUrl) return

        this.relayUrl = relayUrl
        this.signer = signer
        this.isAuthenticated = false
        this.authInFlight = false

        disconnect()

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket open to $relayUrl")
                _connectionState.value = true
                _messages.tryEmit(NostrMessage.Connected)
                // Don't drain the pending queue yet — wait for NIP-42 auth.
                // If the relay doesn't send AUTH within 1.5s, assume no auth
                // is required and drain then.
                scope.launch {
                    delay(1500)
                    if (_connectionState.value && !isAuthenticated && !authInFlight) {
                        Log.d(TAG, "No AUTH challenge received, assuming open relay")
                        isAuthenticated = true
                        drainPendingQueue()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _connectionState.value = false
                _messages.tryEmit(NostrMessage.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = false
                _messages.tryEmit(NostrMessage.Error(t))
            }
        })
    }

    /**
     * Reconnect if needed and wait until ready (connected + authenticated).
     * Returns true if the relay is ready to accept messages.
     *
     * @param readyTimeoutMs max time to wait for the socket + NIP-42 auth handshake
     */
    suspend fun ensureConnected(readyTimeoutMs: Long = 5000): Boolean {
        if (_connectionState.value && isAuthenticated) return true
        val s = signer ?: return false
        if (relayUrl.isEmpty()) return false
        if (!_connectionState.value) {
            connect(relayUrl, s)
        }
        return awaitReady(readyTimeoutMs)
    }

    /**
     * Suspend until the relay WebSocket is connected AND NIP-42 auth is complete,
     * or until timeout. Returns true if ready, false if timed out.
     */
    suspend fun awaitReady(timeoutMs: Long = 5000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (_connectionState.value && isAuthenticated) return true
            delay(100)
        }
        Log.d(TAG, "awaitReady timed out — connected=${_connectionState.value}, authed=$isAuthenticated")
        return _connectionState.value && isAuthenticated
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _connectionState.value = false
        isAuthenticated = false
        authInFlight = false
    }

    fun clearSigner() {
        disconnect()
        signer = null
    }

    suspend fun publishSignedEventJson(signedEventJson: String): Boolean {
        val message = """["EVENT",$signedEventJson]"""
        return sendOrQueue(message)
    }

    /**
     * Hand a signed event to the background outbox and return immediately.
     * Delivery (connect + AUTH + send) happens off the UI path with retry.
     */
    fun enqueueSignedEvent(signedEventJson: String, label: String) {
        _outbox.update { it.copy(pending = it.pending + 1) }
        val job = OutboxJob(id = ++outboxSeq, label = label, signedJson = signedEventJson)
        scope.launch { runOutboxJob(job) }
    }

    private suspend fun runOutboxJob(job: OutboxJob) {
        var attempt = 0
        while (true) {
            val ready = ensureConnected()
            if (ready && publishSignedEventJson(job.signedJson)) {
                _outbox.update { it.copy(pending = (it.pending - 1).coerceAtLeast(0)) }
                Log.d(TAG, "outbox delivered ${job.label}")
                return
            }
            if (attempt >= outboxBackoffsMs.size) {
                outboxMutex.withLock { failedJobs.add(job) }
                _outbox.update {
                    it.copy(
                        pending = (it.pending - 1).coerceAtLeast(0),
                        failed = it.failed + 1,
                    )
                }
                Log.w(TAG, "outbox gave up on ${job.label} after ${attempt + 1} attempts")
                return
            }
            delay(outboxBackoffsMs[attempt])
            attempt++
        }
    }

    /** Re-attempt all previously-failed background sends. */
    fun retryOutbox() {
        scope.launch {
            val jobs = outboxMutex.withLock {
                val copy = failedJobs.toList()
                failedJobs.clear()
                copy
            }
            if (jobs.isEmpty()) return@launch
            _outbox.update {
                it.copy(
                    failed = (it.failed - jobs.size).coerceAtLeast(0),
                    pending = it.pending + jobs.size,
                )
            }
            jobs.forEach { job -> launch { runOutboxJob(job) } }
        }
    }

    suspend fun publishEvent(event: NostrEvent): Boolean {
        val message = buildJsonArray {
            add("EVENT")
            add(Json.encodeToJsonElement(event))
        }.toString()

        return sendOrQueue(message)
    }

    suspend fun subscribe(
        filter: NostrFilter,
        subscriptionId: String = java.util.UUID.randomUUID().toString().take(8)
    ): String {
        val message = buildJsonArray {
            add("REQ")
            add(subscriptionId)
            filter.toJsonArray().forEach { add(it) }
        }.toString()

        sendOrQueue(message)
        return subscriptionId
    }

    suspend fun closeSubscription(subscriptionId: String) {
        val message = buildJsonArray {
            add("CLOSE")
            add(subscriptionId)
        }.toString()

        sendOrQueue(message)
    }

    suspend fun publishEncryptedAppData(
        dTag: String,
        jsonContent: String
    ): Boolean {
        val s = signer ?: return false

        val ready = ensureConnected()
        if (!ready) {
            Log.w(TAG, "publishEncryptedAppData: relay not ready, event will be queued")
        }

        val encrypted = s.nip44Encrypt(jsonContent, s.pubkeyHex)
        if (encrypted == null) {
            Log.e(TAG, "publishEncryptedAppData: NIP-44 encryption failed for dTag=$dTag")
            return false
        }

        val unsignedJson = NostrEvent.buildUnsignedJson(
            pubkeyHex = s.pubkeyHex,
            kind = NostrEvent.KIND_APP_SPECIFIC_DATA,
            content = encrypted,
            tags = listOf(listOf("d", dTag))
        )
        val signedJson = s.signEvent(unsignedJson)
        if (signedJson == null) {
            Log.e(TAG, "publishEncryptedAppData: event signing failed for dTag=$dTag")
            return false
        }

        enqueueSignedEvent(signedJson, "app-data:$dTag")
        Log.d(TAG, "publishEncryptedAppData: dTag=$dTag enqueued for background delivery")
        return true
    }

    /**
     * Publish a NIP-09 deletion event (kind 5) targeting a parameterized
     * replaceable event identified by kind:pubkey:d-tag.
     */
    suspend fun publishDeletion(targetKind: Int, dTag: String): Boolean {
        val s = signer ?: return false

        val ready = ensureConnected()
        if (!ready) {
            Log.w(TAG, "publishDeletion: relay not ready, event will be queued")
        }

        val aTag = "$targetKind:${s.pubkeyHex}:$dTag"
        val unsignedJson = NostrEvent.buildUnsignedJson(
            pubkeyHex = s.pubkeyHex,
            kind = 5,
            content = "",
            tags = listOf(listOf("a", aTag))
        )
        val signedJson = s.signEvent(unsignedJson)
        if (signedJson == null) {
            Log.e(TAG, "publishDeletion: event signing failed for aTag=$aTag")
            return false
        }

        enqueueSignedEvent(signedJson, "deletion:$aTag")
        Log.d(TAG, "publishDeletion: aTag=$aTag enqueued for background delivery")
        return true
    }

    /**
     * Subscribe to CypherLog subscription events (kind 37004). Collects events until
     * the relay sends EOSE, then closes the subscription. Plaintext tags only.
     */
    fun subscribeToKind37004(): Flow<NostrEvent> = flow {
        val s = signer ?: throw IllegalStateException("No signer configured")
        val filter = NostrFilter(
            authors = listOf(s.pubkeyHex),
            kinds = listOf(NostrEvent.KIND_CYPHERLOG_SUBSCRIPTION)
        )
        val subId = subscribe(filter)
        Log.d(TAG, "Subscribed for kind 37004: subId=$subId")
        try {
            messages.collect { msg ->
                when (msg) {
                    is NostrMessage.Eose -> {
                        if (msg.subscriptionId == subId) throw EoseSignal()
                    }
                    is NostrMessage.EventReceived -> {
                        if (msg.subscriptionId == subId) emit(msg.event)
                    }
                    else -> {}
                }
            }
        } catch (_: EoseSignal) {
        } finally {
            closeSubscription(subId)
        }
    }

    /**
     * Publish a replaceable CypherLog subscription event (kind 37004).
     * Tags must include "d" (unique id). Content is empty (tags-only per CypherLog).
     */
    suspend fun publishReplaceable37004(dTag: String, tags: List<List<String>>): Boolean {
        return publishReplaceable37004Detailed(dTag, tags).success
    }

    /** Like [publishReplaceable37004] but returns stage/detail for user-facing diagnostics. */
    suspend fun publishReplaceable37004Detailed(dTag: String, tags: List<List<String>>): PublishStatus {
        val s = signer ?: return PublishStatus(
            success = false,
            stage = "no_signer",
            detail = "No signer configured."
        )
        val ready = ensureConnected()
        if (!ready) Log.w(TAG, "publishReplaceable37004: relay not ready")
        val tagsWithD = tags.toMutableList()
        if (!tagsWithD.any { it.isNotEmpty() && it[0] == "d" }) {
            tagsWithD.add(0, listOf("d", dTag))
        }
        val unsignedJson = NostrEvent.buildUnsignedJson(
            pubkeyHex = s.pubkeyHex,
            kind = NostrEvent.KIND_CYPHERLOG_SUBSCRIPTION,
            content = "",
            tags = tagsWithD
        )
        val signedJson = s.signEvent(unsignedJson) ?: run {
            Log.e(TAG, "publishReplaceable37004: event signing failed for d=$dTag")
            val amberReason = (s as? AmberSigner)?.consumeLastSignError()
            return PublishStatus(
                success = false,
                stage = "sign_event",
                detail = buildString {
                    append("Signer returned null (rejected/cancelled or unsupported request).")
                    if (!amberReason.isNullOrBlank()) {
                        append(" ")
                        append(amberReason)
                    }
                }
            )
        }
        enqueueSignedEvent(signedJson, "cypherlog:$dTag")
        Log.d(TAG, "publishReplaceable37004: d=$dTag enqueued for background delivery")
        return PublishStatus(success = true, stage = "ok")
    }

    /**
     * Publish a replaceable app-data event (kind 30078) with explicit tags/content.
     * Used by CypherLog subscription interop migration from custom kinds.
     */
    suspend fun publishReplaceable30078Detailed(
        dTag: String,
        tags: List<List<String>>,
        content: String = ""
    ): PublishStatus {
        val s = signer ?: return PublishStatus(
            success = false,
            stage = "no_signer",
            detail = "No signer configured."
        )
        val ready = ensureConnected()
        if (!ready) Log.w(TAG, "publishReplaceable30078: relay not ready")
        val tagsWithD = tags.toMutableList()
        if (!tagsWithD.any { it.isNotEmpty() && it[0] == "d" }) {
            tagsWithD.add(0, listOf("d", dTag))
        }
        val unsignedJson = NostrEvent.buildUnsignedJson(
            pubkeyHex = s.pubkeyHex,
            kind = NostrEvent.KIND_APP_SPECIFIC_DATA,
            content = content,
            tags = tagsWithD
        )
        val signedJson = s.signEvent(unsignedJson) ?: run {
            Log.e(TAG, "publishReplaceable30078: event signing failed for d=$dTag")
            val amberReason = (s as? AmberSigner)?.consumeLastSignError()
            return PublishStatus(
                success = false,
                stage = "sign_event",
                detail = buildString {
                    append("Signer returned null (rejected/cancelled or unsupported request).")
                    if (!amberReason.isNullOrBlank()) {
                        append(" ")
                        append(amberReason)
                    }
                }
            )
        }
        enqueueSignedEvent(signedJson, "cypherlog30078:$dTag")
        Log.d(TAG, "publishReplaceable30078: d=$dTag enqueued for background delivery")
        return PublishStatus(success = true, stage = "ok")
    }

    /**
     * Subscribe to replaceable app-data events (kind 30078) by d-tag prefix.
     * Emits raw events (tags + content) until EOSE and then closes.
     */
    fun subscribeToKind30078ByDTagPrefix(dTagPrefix: String): Flow<NostrEvent> = flow {
        val s = signer ?: throw IllegalStateException("No signer configured")
        val filter = NostrFilter(
            authors = listOf(s.pubkeyHex),
            kinds = listOf(NostrEvent.KIND_APP_SPECIFIC_DATA)
        )
        val subId = subscribe(filter)
        Log.d(TAG, "Subscribed for kind 30078 with dTagPrefix=$dTagPrefix: subId=$subId")
        try {
            messages.collect { msg ->
                when (msg) {
                    is NostrMessage.Eose -> {
                        if (msg.subscriptionId == subId) throw EoseSignal()
                    }
                    is NostrMessage.EventReceived -> {
                        if (msg.subscriptionId != subId) return@collect
                        val eventDTag = msg.event.tags
                            .firstOrNull { it.size >= 2 && it[0] == "d" }
                            ?.getOrNull(1)
                            .orEmpty()
                        if (eventDTag.startsWith(dTagPrefix)) {
                            emit(msg.event)
                        }
                    }
                    else -> {}
                }
            }
        } catch (_: EoseSignal) {
        } finally {
            closeSubscription(subId)
        }
    }

    /**
     * Subscribe to app data events and decrypt them. Collects events until
     * the relay sends EOSE (End of Stored Events), then closes the subscription
     * and terminates the flow. Safe for one-shot sync operations.
     */
    fun subscribeToAppData(
        dTag: String? = null,
        dTagPrefix: String? = null
    ): Flow<Pair<String, String>> = flow {
        val s = signer ?: throw IllegalStateException("No signer configured")

        val tagFilters = mutableMapOf<String, List<String>>()
        if (dTag != null) tagFilters["d"] = listOf(dTag)

        val filter = NostrFilter(
            authors = listOf(s.pubkeyHex),
            kinds = listOf(NostrEvent.KIND_APP_SPECIFIC_DATA),
            tagFilters = tagFilters,
            limit = 5000
        )

        val subId = subscribe(filter)
        Log.d(TAG, "Subscribed for app data: subId=$subId, dTag=$dTag, dTagPrefix=$dTagPrefix")

        try {
            messages.collect { msg ->
                when (msg) {
                    is NostrMessage.Eose -> {
                        if (msg.subscriptionId == subId) {
                            Log.d(TAG, "EOSE received for $subId, closing subscription")
                            throw EoseSignal()
                        }
                    }
                    is NostrMessage.EventReceived -> {
                        if (msg.subscriptionId == subId) {
                            val eventDTag = msg.event.tags
                                .firstOrNull { it.size >= 2 && it[0] == "d" }
                                ?.getOrNull(1) ?: ""

                            if (dTagPrefix != null && !eventDTag.startsWith(dTagPrefix)) {
                                return@collect
                            }

                            try {
                                val decrypted = s.nip44Decrypt(msg.event.content, s.pubkeyHex)
                                if (decrypted != null) {
                                    Log.d(TAG, "Decrypted event: dTag=$eventDTag")
                                    emit(eventDTag to decrypted)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Decryption failed for event ${msg.event.id}: ${e.message}")
                            }
                        }
                    }
                    else -> {}
                }
            }
        } catch (_: EoseSignal) {
            // Normal termination after EOSE
        } finally {
            closeSubscription(subId)
        }
    }

    private class EoseSignal : Exception()

    private fun handleMessage(text: String) {
        try {
            val array = Json.parseToJsonElement(text).jsonArray
            val type = array[0].jsonPrimitive.content

            when (type) {
                "EVENT" -> {
                    val subId = array[1].jsonPrimitive.content
                    val event = Json.decodeFromJsonElement<NostrEvent>(array[2])
                    _messages.tryEmit(NostrMessage.EventReceived(subId, event))
                }
                "OK" -> {
                    val eventId = array[1].jsonPrimitive.content
                    val success = array[2].jsonPrimitive.boolean
                    val message = if (array.size > 3) array[3].jsonPrimitive.content else ""
                    Log.d(TAG, "OK: eventId=${eventId.take(8)}… success=$success msg=$message")
                    _messages.tryEmit(NostrMessage.Ok(eventId, success, message))
                }
                "EOSE" -> {
                    val subId = array[1].jsonPrimitive.content
                    Log.d(TAG, "EOSE from relay for sub: $subId")
                    _messages.tryEmit(NostrMessage.Eose(subId))
                }
                "NOTICE" -> {
                    val message = array[1].jsonPrimitive.content
                    Log.d(TAG, "NOTICE: $message")
                    _messages.tryEmit(NostrMessage.Notice(message))
                }
                "AUTH" -> {
                    val challenge = array[1].jsonPrimitive.content
                    Log.d(TAG, "AUTH challenge received")
                    authInFlight = true
                    _messages.tryEmit(NostrMessage.AuthChallenge(challenge))
                    handleAuthChallenge(challenge)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
            _messages.tryEmit(NostrMessage.Error(e))
        }
    }

    private fun handleAuthChallenge(challenge: String) {
        val s = signer ?: return
        scope.launch {
            try {
                val unsignedJson = NostrEvent.buildUnsignedJson(
                    pubkeyHex = s.pubkeyHex,
                    kind = 22242,
                    content = "",
                    tags = listOf(
                        listOf("relay", relayUrl),
                        listOf("challenge", challenge)
                    )
                )
                val signedJson = s.signEvent(unsignedJson)
                if (signedJson != null) {
                    webSocket?.send("""["AUTH",$signedJson]""")
                    isAuthenticated = true
                    authInFlight = false
                    Log.d(TAG, "Auth complete, draining pending queue")
                    drainPendingQueue()
                } else {
                    Log.w(TAG, "Auth signing failed/rejected")
                    authInFlight = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth challenge handling failed: ${e.message}")
                authInFlight = false
            }
        }
    }

    private suspend fun sendOrQueue(message: String): Boolean {
        return if (_connectionState.value && isAuthenticated) {
            val sent = webSocket?.send(message) ?: false
            if (!sent) Log.w(TAG, "WebSocket send returned false")
            sent
        } else {
            Log.d(TAG, "Queueing message (connected=${_connectionState.value}, authed=$isAuthenticated)")
            pendingQueue.send(message)
            true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun drainPendingQueue() {
        var count = 0
        while (!pendingQueue.isEmpty) {
            val msg = pendingQueue.receive()
            if (_connectionState.value) {
                webSocket?.send(msg)
                count++
            }
        }
        if (count > 0) Log.d(TAG, "Drained $count messages from pending queue")
    }
}
