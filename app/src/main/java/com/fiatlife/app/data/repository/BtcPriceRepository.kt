package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.network.NetworkClients
import com.fiatlife.app.domain.model.BtcPrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BtcPriceRepo"
private const val MEMPOOL_URL = "https://mempool.space/api/v1/prices"
private const val COINBASE_URL = "https://api.coinbase.com/v2/prices/BTC-USD/spot"
private const val REFRESH_AFTER_MS = 5 * 60 * 1000L

/**
 * BTC/USD spot price, same sources as the web server: mempool.space, then Coinbase.
 * Requests carry no user data — only the fact that a price was asked for.
 */
@Singleton
class BtcPriceRepository @Inject constructor(
    private val networkClients: NetworkClients,
    private val json: Json
) {
    private val _price = MutableStateFlow<BtcPrice?>(null)
    val price: StateFlow<BtcPrice?> = _price.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val mutex = Mutex()

    /** Fetch when there is no price or it is older than five minutes (or always when [force]). */
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val current = _price.value
        if (!force && current != null &&
            System.currentTimeMillis() - current.fetchedAtMs < REFRESH_AFTER_MS
        ) return@withLock
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val fetched = runCatching { BtcPrice(fetchMempool(), now, "mempool.space") }
                .recoverCatching { mempoolError ->
                    Log.w(TAG, "mempool.space price failed: ${mempoolError.message}")
                    BtcPrice(fetchCoinbase(), now, "Coinbase")
                }
            fetched.onSuccess {
                _price.value = it
                _error.value = null
            }.onFailure {
                Log.w(TAG, "BTC price unavailable: ${it.message}")
                _error.value = "BTC price unavailable."
            }
        }
    }

    private fun getJson(url: String): String {
        val client = networkClients.clientFor(url).newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body?.string().orEmpty()
        }
    }

    private fun valid(usd: Double?): Double =
        usd?.takeIf { it.isFinite() && it > 0 } ?: error("no USD price")

    private fun fetchMempool(): Double =
        valid(json.parseToJsonElement(getJson(MEMPOOL_URL)).jsonObject["USD"]?.jsonPrimitive?.doubleOrNull)

    private fun fetchCoinbase(): Double {
        val data = json.parseToJsonElement(getJson(COINBASE_URL)).jsonObject["data"]?.jsonObject
        return valid(data?.get("amount")?.jsonPrimitive?.content?.trim()?.toDoubleOrNull())
    }
}
