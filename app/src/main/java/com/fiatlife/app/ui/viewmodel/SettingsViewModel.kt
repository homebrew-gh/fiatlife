package com.fiatlife.app.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.nostr.NostrClient
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
    val privateKeyHex: String = "",
    val publicKeyHex: String = "",
    val isConnected: Boolean = false,
    val statusMessage: String = "",
    val isKeyGenerated: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val nostrClient: NostrClient,
    private val blossomClient: BlossomClient
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    companion object {
        val KEY_RELAY_URL = stringPreferencesKey("relay_url")
        val KEY_BLOSSOM_URL = stringPreferencesKey("blossom_url")
        val KEY_PRIVATE_KEY = stringPreferencesKey("private_key")
    }

    init {
        loadSettings()
        observeConnection()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                val relayUrl = prefs[KEY_RELAY_URL] ?: ""
                val blossomUrl = prefs[KEY_BLOSSOM_URL] ?: ""
                val privateKeyHex = prefs[KEY_PRIVATE_KEY] ?: ""
                val publicKeyHex = if (privateKeyHex.isNotEmpty()) {
                    derivePublicKey(privateKeyHex)
                } else ""

                _state.update {
                    it.copy(
                        relayUrl = relayUrl,
                        blossomUrl = blossomUrl,
                        privateKeyHex = privateKeyHex,
                        publicKeyHex = publicKeyHex,
                        isKeyGenerated = privateKeyHex.isNotEmpty()
                    )
                }

                if (relayUrl.isNotEmpty() && privateKeyHex.isNotEmpty()) {
                    connectToRelay()
                }
                if (blossomUrl.isNotEmpty() && privateKeyHex.isNotEmpty()) {
                    blossomClient.configure(blossomUrl, privateKeyHex.hexToByteArray())
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

    fun generateNewKey() {
        val secp256k1 = Secp256k1.get()
        val privateKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

        while (!secp256k1.secKeyVerify(privateKey)) {
            java.security.SecureRandom().nextBytes(privateKey)
        }

        val privateKeyHex = privateKey.toHex()
        val publicKeyHex = derivePublicKey(privateKeyHex)

        _state.update {
            it.copy(
                privateKeyHex = privateKeyHex,
                publicKeyHex = publicKeyHex,
                isKeyGenerated = true
            )
        }
    }

    fun importPrivateKey(nsecOrHex: String) {
        val hex = if (nsecOrHex.startsWith("nsec")) {
            _state.update { it.copy(statusMessage = "nsec import not yet supported, use hex") }
            return
        } else {
            nsecOrHex.trim()
        }

        try {
            val bytes = hex.hexToByteArray()
            require(bytes.size == 32) { "Invalid key length" }
            val secp256k1 = Secp256k1.get()
            require(secp256k1.secKeyVerify(bytes)) { "Invalid private key" }

            val publicKeyHex = derivePublicKey(hex)
            _state.update {
                it.copy(
                    privateKeyHex = hex,
                    publicKeyHex = publicKeyHex,
                    isKeyGenerated = true,
                    statusMessage = "Key imported successfully"
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(statusMessage = "Invalid key: ${e.message}") }
        }
    }

    fun saveAndConnect() {
        viewModelScope.launch {
            val current = _state.value

            dataStore.edit { prefs ->
                prefs[KEY_RELAY_URL] = current.relayUrl
                prefs[KEY_BLOSSOM_URL] = current.blossomUrl
                prefs[KEY_PRIVATE_KEY] = current.privateKeyHex
            }

            if (current.relayUrl.isNotEmpty() && current.privateKeyHex.isNotEmpty()) {
                connectToRelay()
            }
            if (current.blossomUrl.isNotEmpty() && current.privateKeyHex.isNotEmpty()) {
                blossomClient.configure(current.blossomUrl, current.privateKeyHex.hexToByteArray())
            }

            _state.update { it.copy(statusMessage = "Settings saved") }
        }
    }

    private fun connectToRelay() {
        val current = _state.value
        if (current.relayUrl.isNotEmpty() && current.privateKeyHex.isNotEmpty()) {
            nostrClient.connect(current.relayUrl, current.privateKeyHex.hexToByteArray())
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

    fun getPrivateKeyBytes(): ByteArray? {
        val hex = _state.value.privateKeyHex
        return if (hex.isNotEmpty()) hex.hexToByteArray() else null
    }
}
