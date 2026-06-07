package com.fiatlife.app.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.fiatlife.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and caches the "trust self-signed LAN TLS" preference used for relay
 * (wss://) and Blossom (https://) connections to home servers over LAN/VPN.
 */
@Singleton
class RelayTlsSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var trustSelfSignedLanTls: Boolean = false
        private set

    init {
        trustSelfSignedLanTls = runBlocking {
            dataStore.data.map { prefs ->
                prefs[MainActivity.KEY_TRUST_SELF_SIGNED_LAN_TLS] ?: false
            }.first()
        }
        scope.launch {
            dataStore.data.collect { prefs ->
                trustSelfSignedLanTls = prefs[MainActivity.KEY_TRUST_SELF_SIGNED_LAN_TLS] ?: false
            }
        }
    }

    fun shouldTrust(url: String): Boolean =
        LanTls.shouldTrustSelfSigned(url, trustSelfSignedLanTls)

    /** Update in-memory flag immediately so the next WebSocket uses the right TLS mode. */
    fun applyTrustSelfSignedLanTls(enabled: Boolean) {
        trustSelfSignedLanTls = enabled
    }

    suspend fun setTrustSelfSignedLanTls(enabled: Boolean) {
        applyTrustSelfSignedLanTls(enabled)
        dataStore.edit { prefs ->
            prefs[MainActivity.KEY_TRUST_SELF_SIGNED_LAN_TLS] = enabled
        }
    }
}
