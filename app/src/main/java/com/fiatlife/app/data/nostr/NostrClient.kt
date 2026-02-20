package com.fiatlife.app.data.nostr

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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

@Singleton
class NostrClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private var relayUrl: String = ""
    private var signer: NostrSigner? = null
    private var isAuthenticated = false
    private var authInFlight = false

    private val _messages = MutableSharedFlow<NostrMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<NostrMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val pendingQueue = Channel<String>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
     */
    suspend fun ensureConnected(): Boolean {
        if (_connectionState.value && isAuthenticated) return true
        val s = signer ?: return false
        if (relayUrl.isEmpty()) return false
        if (!_connectionState.value) {
            connect(relayUrl, s)
        }
        return awaitReady()
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

        val sent = publishSignedEventJson(signedJson)
        Log.d(TAG, "publishEncryptedAppData: dTag=$dTag, sent=$sent")
        return sent
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
            tagFilters = tagFilters
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
