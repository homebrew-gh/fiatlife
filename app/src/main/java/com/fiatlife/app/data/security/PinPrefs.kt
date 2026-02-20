package com.fiatlife.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "fiatlife_pin_secure"
private const val KEY_PIN_LOCK_ENABLED = "pin_lock_enabled"
private const val KEY_PIN_SALT = "pin_salt"
private const val KEY_PIN_HASH = "pin_hash"
private const val PIN_LENGTH_MIN = 4
private const val PIN_LENGTH_MAX = 12

@Singleton
class PinPrefs @Inject constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            @Suppress("DEPRECATION")
            java.io.File(
                appContext.applicationInfo.dataDir + "/shared_prefs/" + PREFS_NAME + ".xml"
            ).delete()
        } catch (_: Throwable) { }
        null
    }

    private fun <T> withPrefs(default: T, block: (SharedPreferences) -> T): T {
        val p = prefs ?: return default
        return try { block(p) } catch (_: Throwable) { default }
    }

    fun isPinLockEnabled(): Boolean =
        withPrefs(false) { it.getBoolean(KEY_PIN_LOCK_ENABLED, false) }

    fun setPinLockEnabled(enabled: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_PIN_LOCK_ENABLED, enabled)?.apply()
            if (!enabled) {
                prefs?.edit()?.remove(KEY_PIN_SALT)?.remove(KEY_PIN_HASH)?.apply()
            }
        } catch (_: Throwable) { }
    }

    fun hasPinSet(): Boolean = withPrefs(false) {
        it.getString(KEY_PIN_HASH, null)?.isNotBlank() == true &&
                it.getString(KEY_PIN_SALT, null)?.isNotBlank() == true
    }

    fun setPin(pin: String): Boolean {
        val cleaned = pin.trim().filter { it.isDigit() }
        if (cleaned.length !in PIN_LENGTH_MIN..PIN_LENGTH_MAX) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pinHash(cleaned, salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return try {
            prefs?.edit()?.apply {
                putString(KEY_PIN_SALT, saltHex)
                putString(KEY_PIN_HASH, hashHex)
                putBoolean(KEY_PIN_LOCK_ENABLED, true)
                apply()
            }
            true
        } catch (_: Throwable) { false }
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = withPrefs(null) { it.getString(KEY_PIN_SALT, null) } ?: return false
        val storedHashHex = withPrefs(null) { it.getString(KEY_PIN_HASH, null) } ?: return false
        val salt = hexToBytes(saltHex) ?: return false
        val storedHash = hexToBytes(storedHashHex) ?: return false
        val computed = pinHash(pin.filter { it.isDigit() }, salt)
        return computed.size == storedHash.size && computed.indices.all { computed[it] == storedHash[it] }
    }

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
