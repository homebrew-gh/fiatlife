package com.fiatlife.app.data.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
    private var privateKey: ByteArray? = null
    private var isAuthenticated = false

    private val _messages = MutableSharedFlow<NostrMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<NostrMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val pendingQueue = Channel<String>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(relayUrl: String, privateKey: ByteArray) {
        this.relayUrl = relayUrl
        this.privateKey = privateKey
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

    suspend fun publishEvent(event: NostrEvent): Boolean {
        val message = buildJsonArray {
            add("EVENT")
            add(Json.encodeToJsonElement(event))
        }.toString()

        return sendOrQueue(message)
    }

    suspend fun subscribe(
        filter: NostrFilter,
        subscriptionId: String = UUID.randomUUID().toString().take(8)
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
        jsonContent: String,
        privateKey: ByteArray
    ): Boolean {
        val encrypted = Nip44Encryption.encryptToSelf(jsonContent, privateKey)
        val event = NostrEvent.createAppData(privateKey, dTag, encrypted)
        return publishEvent(event)
    }

    fun subscribeToAppData(
        pubkey: String,
        dTag: String? = null
    ): Flow<String> = flow {
        val tagFilters = mutableMapOf<String, List<String>>()
        if (dTag != null) tagFilters["d"] = listOf(dTag)

        val filter = NostrFilter(
            authors = listOf(pubkey),
            kinds = listOf(NostrEvent.KIND_APP_SPECIFIC_DATA),
            tagFilters = tagFilters
        )

        val subId = subscribe(filter)
        val pk = privateKey ?: throw IllegalStateException("Not connected")

        try {
            messages.collect { msg ->
                when (msg) {
                    is NostrMessage.EventReceived -> {
                        if (msg.subscriptionId == subId) {
                            try {
                                val decrypted = Nip44Encryption.decryptFromSelf(
                                    msg.event.content, pk
                                )
                                emit(decrypted)
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
        val pk = privateKey ?: return
        val authEvent = Nip42Auth.createAuthEvent(pk, challenge, relayUrl)
        val message = Nip42Auth.buildAuthMessage(authEvent)
        webSocket?.send(message)
        isAuthenticated = true
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
            val pk = privateKey
            if (pk != null && relayUrl.isNotEmpty()) {
                connect(relayUrl, pk)
            }
        }
    }
}
