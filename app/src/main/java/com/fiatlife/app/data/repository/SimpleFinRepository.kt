package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.network.NetworkClients
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.SimpleFin
import com.fiatlife.app.domain.model.SimpleFinAccess
import com.fiatlife.app.domain.model.SimpleFinSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SimpleFinRepo"
private const val SHARE_D_TAG = "fiatlife/settings/simplefin"

/** Same as the web server: SimpleFIN Bridge allows about 24 requests a day across both clients. */
private const val MIN_REFETCH_MS = 60 * 60 * 1000L

/**
 * SimpleFIN connection the web server publishes (NIP-44 to self) once it is connected there.
 * [accessUrl] grants read access to all linked bank data: never store or log it.
 */
@Serializable
private data class SimpleFinShare(
    val accessUrl: String = "",
    val connectedAtMs: Long? = null,
    val lastFetchedAtMs: Long? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean = false
)

sealed interface SimpleFinRefresh {
    /** SimpleFIN hasn't been connected in the web app (or the web app hasn't shared it yet). */
    data object NotConnected : SimpleFinRefresh

    /** A client fetched less than an hour ago; its balances arrive through relay sync. */
    data class Recent(val lastFetchedAtMs: Long) : SimpleFinRefresh

    data class Fetched(
        val updatedAccounts: Int,
        val messages: List<String>,
        /** Oldest bank balance date among linked accounts, when older than [SimpleFin.STALE_MS]. */
        val staleSinceMs: Long?,
        /** Linked accounts the fetch didn't return, so they can't update. */
        val unmatchedAccounts: List<String>
    ) : SimpleFinRefresh

    data class Failed(val message: String) : SimpleFinRefresh
}

private class SimpleFinException(message: String) : Exception(message)

/**
 * Fetches balances from SimpleFIN Bridge directly, using the connection the web app shares,
 * and applies them to linked accounts the same way the web app does.
 */
@Singleton
class SimpleFinRepository @Inject constructor(
    private val nostrClient: NostrClient,
    private val networkClients: NetworkClients,
    private val bankAccountRepository: BankAccountRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val json: Json
) {
    private val mutex = Mutex()

    suspend fun refresh(): SimpleFinRefresh = mutex.withLock {
        if (!nostrClient.hasSigner) return@withLock SimpleFinRefresh.NotConnected
        val share = try {
            loadShare()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read SimpleFIN connection: ${e.message}")
            return@withLock SimpleFinRefresh.Failed("Could not read the SimpleFIN connection from the relay.")
        } ?: return@withLock SimpleFinRefresh.NotConnected

        val now = System.currentTimeMillis()
        val last = share.lastFetchedAtMs
        if (last != null && now - last in 0 until MIN_REFETCH_MS) {
            return@withLock SimpleFinRefresh.Recent(last)
        }
        val access = SimpleFin.parseAccessUrl(share.accessUrl)
            ?: return@withLock SimpleFinRefresh.Failed(
                "The SimpleFIN connection is invalid. Reconnect SimpleFIN in the web app."
            )
        val snapshot = try {
            withContext(Dispatchers.IO) { fetchBalances(access) }
        } catch (e: SimpleFinException) {
            return@withLock SimpleFinRefresh.Failed(e.message.orEmpty())
        } catch (e: Exception) {
            Log.w(TAG, "SimpleFIN fetch failed: ${e.javaClass.simpleName}")
            return@withLock SimpleFinRefresh.Failed("Could not reach SimpleFIN.")
        }
        publishShare(share.copy(lastFetchedAtMs = now, updatedAt = now))

        val bank = bankAccountRepository.getAllBankAccounts().first()
        val credit = creditAccountRepository.getAllCreditAccounts().first()
        val bankUpdates = SimpleFin.bankUpdates(bank, snapshot.accounts)
        bankUpdates.forEach { bankAccountRepository.saveBankAccount(it) }
        val debtUpdates = SimpleFin.debtUpdates(credit, snapshot.accounts)
        debtUpdates.forEach { creditAccountRepository.saveSyncedCreditAccount(it) }
        Log.d(TAG, "Applied SimpleFIN balances to ${bankUpdates.size + debtUpdates.size} account(s)")

        val linkedNames = bank.mapNotNull { a -> a.simplefinAccountKey?.let { it to a.name } } +
            credit.mapNotNull { a -> a.simplefinAccountKey?.let { it to a.name } }
        val linkedKeys = linkedNames.map { it.first }.toSet()
        val unmatched = SimpleFin.unmatchedKeys(linkedKeys, snapshot.accounts)
        SimpleFinRefresh.Fetched(
            updatedAccounts = bankUpdates.size + debtUpdates.size,
            messages = snapshot.messages,
            staleSinceMs = SimpleFin.staleSince(linkedKeys, snapshot.accounts, now),
            unmatchedAccounts = linkedNames.filter { it.first in unmatched }.map { it.second }
        )
    }

    private suspend fun loadShare(): SimpleFinShare? {
        var latest: SimpleFinShare? = null
        withTimeout(30_000) {
            nostrClient.subscribeToAppData(dTag = SHARE_D_TAG).collect { (_, decrypted) ->
                val share = runCatching { json.decodeFromString<SimpleFinShare>(decrypted) }.getOrNull()
                    ?: return@collect
                if ((share.updatedAt ?: 0) >= (latest?.updatedAt ?: 0)) latest = share
            }
        }
        return latest?.takeIf { !it.deleted && it.accessUrl.isNotBlank() }
    }

    private suspend fun publishShare(share: SimpleFinShare) {
        try {
            nostrClient.publishEncryptedAppData(
                SHARE_D_TAG,
                json.encodeToString(SimpleFinShare.serializer(), share)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not record SimpleFIN fetch time: ${e.message}")
        }
    }

    private fun fetchBalances(access: SimpleFinAccess): SimpleFinSnapshot {
        val url = access.balancesUrl
        val client = networkClients.clientFor(url).newBuilder()
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(access.username, access.password, Charsets.UTF_8))
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> {}
                response.code == 402 -> throw SimpleFinException(
                    "SimpleFIN says payment is required. Check your SimpleFIN Bridge subscription."
                )
                response.code == 403 -> throw SimpleFinException(
                    "SimpleFIN denied access. The connection may have been disabled in SimpleFIN " +
                        "Bridge — disconnect in the web app and connect again with a new setup token."
                )
                else -> throw SimpleFinException("SimpleFIN balance fetch failed (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            return runCatching { SimpleFin.parseAccountSet(body, json) }
                .getOrElse { throw SimpleFinException("Unexpected SimpleFIN response.") }
        }
    }
}
