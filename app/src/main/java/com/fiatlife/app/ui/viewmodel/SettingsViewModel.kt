package com.fiatlife.app.ui.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fiatlife.app.MainActivity
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.notification.BillReminderWorker
import com.fiatlife.app.data.notification.KEY_NOTIF_DAYS_BEFORE
import com.fiatlife.app.data.notification.KEY_NOTIF_DETAIL_LEVEL
import com.fiatlife.app.data.notification.KEY_NOTIF_ENABLED
import com.fiatlife.app.data.notification.NotifDetailLevel
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.hexToByteArray
import com.fiatlife.app.data.nostr.toHex
import com.fiatlife.app.data.security.PinPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val Context.notifPrefsStore by preferencesDataStore(name = "bill_notif_prefs")

data class SettingsState(
    val relayUrl: String = "",
    val blossomUrl: String = "",
    val publicKeyHex: String = "",
    val authType: String = "",
    val isConnected: Boolean = false,
    val isBlossomConfigured: Boolean = false,
    val isPinLockEnabled: Boolean = false,
    val hasPinSet: Boolean = false,
    val billNotifEnabled: Boolean = false,
    val billNotifDetailLevel: NotifDetailLevel = NotifDetailLevel.PRIVATE,
    val billNotifDaysBefore: Int = 3,
    val statusMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
    private val nostrClient: NostrClient,
    private val blossomClient: BlossomClient,
    val pinPrefs: PinPrefs
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

                val notifPrefs = appContext.notifPrefsStore.data.first()
                val notifEnabled = notifPrefs[KEY_NOTIF_ENABLED] ?: false
                val detailStr = notifPrefs[KEY_NOTIF_DETAIL_LEVEL] ?: NotifDetailLevel.PRIVATE.name
                val daysBefore = notifPrefs[KEY_NOTIF_DAYS_BEFORE] ?: 3

                _state.update {
                    it.copy(
                        relayUrl = relayUrl,
                        blossomUrl = blossomUrl,
                        publicKeyHex = publicKeyHex,
                        authType = authType,
                        isBlossomConfigured = blossomClient.isConfigured(),
                        isPinLockEnabled = pinPrefs.isPinLockEnabled(),
                        hasPinSet = pinPrefs.hasPinSet(),
                        billNotifEnabled = notifEnabled,
                        billNotifDetailLevel = try {
                            NotifDetailLevel.valueOf(detailStr)
                        } catch (_: Exception) {
                            NotifDetailLevel.PRIVATE
                        },
                        billNotifDaysBefore = daysBefore
                    )
                }
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            nostrClient.connectionState.collect { connected ->
                _state.update {
                    val msg = when {
                        connected && it.statusMessage == "Connecting to relay..." -> {
                            val blossomPart = if (it.isBlossomConfigured) " | Blossom ready" else ""
                            "Connected to relay$blossomPart"
                        }
                        !connected && it.isConnected -> "Disconnected from relay"
                        else -> it.statusMessage
                    }
                    it.copy(isConnected = connected, statusMessage = msg)
                }
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

            if (!nostrClient.hasSigner) {
                _state.update { it.copy(statusMessage = "No signer configured. Sign out and sign back in.") }
                return@launch
            }

            if (current.relayUrl.isNotEmpty()) {
                _state.update { it.copy(statusMessage = "Connecting to relay...") }
                nostrClient.connect(current.relayUrl)
            }

            val signer = nostrClient.currentSigner
            if (current.blossomUrl.isNotEmpty() && signer != null) {
                blossomClient.configure(current.blossomUrl, signer)
                _state.update { it.copy(isBlossomConfigured = true) }
            } else if (current.blossomUrl.isEmpty()) {
                _state.update { it.copy(isBlossomConfigured = false) }
            }

            if (current.relayUrl.isEmpty()) {
                _state.update { it.copy(statusMessage = "Settings saved") }
            }
        }
    }

    fun setPinLockEnabled(enabled: Boolean) {
        if (enabled && !pinPrefs.hasPinSet()) return
        pinPrefs.setPinLockEnabled(enabled)
        _state.update { it.copy(isPinLockEnabled = enabled, hasPinSet = pinPrefs.hasPinSet()) }
    }

    fun onPinSet() {
        _state.update { it.copy(isPinLockEnabled = true, hasPinSet = true) }
    }

    fun setBillNotifEnabled(enabled: Boolean) {
        _state.update { it.copy(billNotifEnabled = enabled) }
        viewModelScope.launch {
            appContext.notifPrefsStore.edit { it[KEY_NOTIF_ENABLED] = enabled }
            syncNotifWorker()
        }
    }

    fun setBillNotifDetailLevel(level: NotifDetailLevel) {
        _state.update { it.copy(billNotifDetailLevel = level) }
        viewModelScope.launch {
            appContext.notifPrefsStore.edit { it[KEY_NOTIF_DETAIL_LEVEL] = level.name }
        }
    }

    fun setBillNotifDaysBefore(days: Int) {
        val clamped = days.coerceIn(1, 14)
        _state.update { it.copy(billNotifDaysBefore = clamped) }
        viewModelScope.launch {
            appContext.notifPrefsStore.edit { it[KEY_NOTIF_DAYS_BEFORE] = clamped }
        }
    }

    private fun syncNotifWorker() {
        val wm = WorkManager.getInstance(appContext)
        if (_state.value.billNotifEnabled) {
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(
                12, TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(
                "bill_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } else {
            wm.cancelUniqueWork("bill_reminders")
        }
    }

    fun logout() {
        viewModelScope.launch {
            nostrClient.clearSigner()
            dataStore.edit { it.clear() }
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
