package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.nostr.NostrClient
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.text.toBooleanStrictOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppSettingsRepo"

/** Central registry for known future flag keys. */
object AppSettingsFlagKeys {
    const val BILLS_SHOW_ZERO_BALANCE_LINKED = "bills.show_zero_balance_linked"
    const val DASHBOARD_SHOW_COMING_DUE = "dashboard.show_coming_due"
    const val ENABLE_BETA_FEATURES = "app.enable_beta_features"
}

@Serializable
data class AppSettingsPayload(
    val schemaVersion: Int = 1,
    val blossomUrl: String = "",
    /** Forward-compatible bag for future settings/feature flags. */
    val flags: Map<String, String> = emptyMap(),
    val updatedAt: Long = 0L
)

@Singleton
class AppSettingsRepository @Inject constructor(
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        private const val SETTINGS_D_TAG = "fiatlife/settings/app"
    }

    suspend fun publishBlossomUrl(blossomUrl: String) {
        upsertSettings { current ->
            current.copy(blossomUrl = blossomUrl.trim())
        }
    }

    /** Set/unset a generic future flag in app settings. */
    suspend fun setFlag(key: String, value: String?) {
        val normalizedKey = key.trim()
        if (normalizedKey.isBlank()) return
        upsertSettings { current ->
            val nextFlags = current.flags.toMutableMap()
            if (value == null) nextFlags.remove(normalizedKey)
            else nextFlags[normalizedKey] = value
            current.copy(flags = nextFlags)
        }
    }

    suspend fun setBooleanFlag(key: String, value: Boolean?) {
        setFlag(key, value?.toString())
    }

    suspend fun setIntFlag(key: String, value: Int?) {
        setFlag(key, value?.toString())
    }

    suspend fun setLongFlag(key: String, value: Long?) {
        setFlag(key, value?.toString())
    }

    fun getFlag(settings: AppSettingsPayload, key: String): String? =
        settings.flags[key]

    fun getBooleanFlag(settings: AppSettingsPayload, key: String, default: Boolean = false): Boolean =
        settings.flags[key]?.toBooleanStrictOrNull() ?: default

    fun getIntFlag(settings: AppSettingsPayload, key: String, default: Int = 0): Int =
        settings.flags[key]?.toIntOrNull() ?: default

    fun getLongFlag(settings: AppSettingsPayload, key: String, default: Long = 0L): Long =
        settings.flags[key]?.toLongOrNull() ?: default

    suspend fun fetchSettingsFromRelay(): AppSettingsPayload? {
        if (!nostrClient.hasSigner) return null
        return try {
            var latest: AppSettingsPayload? = null
            withTimeout(15_000) {
                nostrClient.subscribeToAppData(dTag = SETTINGS_D_TAG).collect { (_, decrypted) ->
                    val parsed = runCatching {
                        json.decodeFromString(AppSettingsPayload.serializer(), decrypted)
                    }.getOrNull()
                    if (parsed != null) {
                        if (latest == null || parsed.updatedAt >= latest!!.updatedAt) {
                            latest = parsed
                        }
                    }
                }
            }
            latest
        } catch (e: Exception) {
            Log.w(TAG, "Settings fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun upsertSettings(update: (AppSettingsPayload) -> AppSettingsPayload) {
        if (!nostrClient.hasSigner) return
        val current = fetchSettingsFromRelay() ?: AppSettingsPayload()
        val next = update(current).copy(
            schemaVersion = max(current.schemaVersion, 1),
            updatedAt = System.currentTimeMillis()
        )
        try {
            nostrClient.publishEncryptedAppData(
                SETTINGS_D_TAG,
                json.encodeToString(AppSettingsPayload.serializer(), next)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish settings: ${e.message}")
        }
    }
}
