package com.fiatlife.app.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.MainActivity
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.nostr.LocalSigner
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrSigner
import com.fiatlife.app.data.nostr.hexToByteArray
import com.fiatlife.app.data.nostr.toHex
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val relayUrl: String = "",
    val blossomUrl: String = "",
    val publicKeyHex: String = "",
    val authType: String = "",
    val isConnected: Boolean = false,
    val statusMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val nostrClient: NostrClient,
    private val blossomClient: BlossomClient
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        observeConnection()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                val relayUrl = prefs[MainActivity.KEY_RELAY_URL] ?: ""
                val blossomUrl = prefs[MainActivity.KEY_BLOSSOM_URL] ?: ""
                val authType = prefs[MainActivity.KEY_AUTH_TYPE] ?: ""
                val publicKeyHex = when (authType) {
                    "amber" -> prefs[MainActivity.KEY_AMBER_PUBKEY] ?: ""
                    "local" -> {
                        val pk = prefs[MainActivity.KEY_PRIVATE_KEY] ?: ""
                        if (pk.isNotEmpty()) derivePublicKey(pk) else ""
                    }
                    else -> ""
                }

                _state.update {
                    it.copy(
                        relayUrl = relayUrl,
                        blossomUrl = blossomUrl,
                        publicKeyHex = publicKeyHex,
                        authType = authType
                    )
                }

                if (relayUrl.isNotEmpty() && nostrClient.hasSigner) {
                    // Already connected from MainActivity restore
                }
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            nostrClient.connectionState.collect { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }
    }

    fun updateRelayUrl(url: String) {
        _state.update { it.copy(relayUrl = url) }
    }

    fun updateBlossomUrl(url: String) {
        _state.update { it.copy(blossomUrl = url) }
    }

    fun saveAndConnect() {
        viewModelScope.launch {
            val current = _state.value

            dataStore.edit { prefs ->
                prefs[MainActivity.KEY_RELAY_URL] = current.relayUrl
                prefs[MainActivity.KEY_BLOSSOM_URL] = current.blossomUrl
            }

            val signer = buildSigner()
            if (signer != null) {
                if (current.relayUrl.isNotEmpty()) {
                    nostrClient.connect(current.relayUrl, signer)
                }
                if (current.blossomUrl.isNotEmpty()) {
                    blossomClient.configure(current.blossomUrl, signer)
                }
            }

            _state.update { it.copy(statusMessage = "Settings saved") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            nostrClient.clearSigner()
            dataStore.edit { it.clear() }
        }
    }

    private suspend fun buildSigner(): NostrSigner? {
        val prefs = dataStore.data.first()
        return when (prefs[MainActivity.KEY_AUTH_TYPE]) {
            "local" -> {
                val hex = prefs[MainActivity.KEY_PRIVATE_KEY] ?: return null
                if (hex.isNotEmpty()) LocalSigner(hex.hexToByteArray()) else null
            }
            "amber" -> null // Amber signer is managed by MainActivity
            else -> null
        }
    }

    private fun derivePublicKey(privateKeyHex: String): String {
        return try {
            val secp256k1 = Secp256k1.get()
            val compressed = secp256k1.pubKeyCompress(
                secp256k1.pubkeyCreate(privateKeyHex.hexToByteArray())
            )
            compressed.drop(1).toByteArray().toHex()
        } catch (_: Exception) {
            ""
        }
    }
}
