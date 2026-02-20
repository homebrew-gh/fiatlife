package com.fiatlife.app.data.nostr

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
        this.relayUrl = relayUrl
        this.signer = signer
        this.isAuthenticated = false

        disconnect()

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = true
                _messages.tryEmit(NostrMessage.Connected)
                scope.launch { drainPendingQueue() }
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
                _connectionState.value = false
                _messages.tryEmit(NostrMessage.Error(t))
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _connectionState.value = false
        isAuthenticated = false
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

    /**
     * Encrypt and publish app data using the configured signer.
     * Returns false if no signer is configured or encryption/signing fails.
     */
    suspend fun publishEncryptedAppData(
        dTag: String,
        jsonContent: String
    ): Boolean {
        val s = signer ?: return false
        val encrypted = s.nip44Encrypt(jsonContent, s.pubkeyHex) ?: return false

        val unsignedJson = NostrEvent.buildUnsignedJson(
            pubkeyHex = s.pubkeyHex,
            kind = NostrEvent.KIND_APP_SPECIFIC_DATA,
            content = encrypted,
            tags = listOf(listOf("d", dTag))
        )
        val signedJson = s.signEvent(unsignedJson) ?: return false
        return publishSignedEventJson(signedJson)
    }

    /**
     * Subscribe to app data events and decrypt them using the configured signer.
     * When [dTag] is provided, only events with that exact d-tag are returned.
     * When [dTagPrefix] is provided (and [dTag] is null), events whose d-tag
     * starts with the prefix are returned. This prevents cross-app contamination
     * when multiple apps store kind 30078 events on the same relay.
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

        try {
            messages.collect { msg ->
                when (msg) {
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
                                if (decrypted != null) emit(eventDTag to decrypted)
                            } catch (e: Exception) {
                                _messages.tryEmit(NostrMessage.Error(e))
                            }
                        }
                    }
                    else -> {}
                }
            }
        } finally {
            closeSubscription(subId)
        }
    }

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
                    _messages.tryEmit(NostrMessage.Ok(eventId, success, message))
                }
                "EOSE" -> {
                    val subId = array[1].jsonPrimitive.content
                    _messages.tryEmit(NostrMessage.Eose(subId))
                }
                "NOTICE" -> {
                    val message = array[1].jsonPrimitive.content
                    _messages.tryEmit(NostrMessage.Notice(message))
                }
                "AUTH" -> {
                    val challenge = array[1].jsonPrimitive.content
                    _messages.tryEmit(NostrMessage.AuthChallenge(challenge))
                    handleAuthChallenge(challenge)
                }
            }
        } catch (e: Exception) {
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
                    drainPendingQueue()
                } else {
                    Log.w(TAG, "Auth signing failed/rejected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth challenge handling failed: ${e.message}")
            }
        }
    }

    private suspend fun sendOrQueue(message: String): Boolean {
        return if (_connectionState.value && isAuthenticated) {
            webSocket?.send(message) ?: false
        } else {
            pendingQueue.send(message)
            true
        }
    }

    private suspend fun drainPendingQueue() {
        while (!pendingQueue.isEmpty) {
            val msg = pendingQueue.receive()
            if (_connectionState.value) {
                webSocket?.send(msg)
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5000)
            val s = signer
            if (s != null && relayUrl.isNotEmpty()) {
                connect(relayUrl, s)
            }
        }
    }
}
