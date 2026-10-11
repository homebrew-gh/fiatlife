package com.fiatlife.app.data.sync

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fiatlife.app.MainActivity
import com.fiatlife.app.data.nostr.AmberSigner
import com.fiatlife.app.data.nostr.LocalSigner
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrSigner
import com.fiatlife.app.data.nostr.hexToByteArray
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.SimpleFinRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val TAG = "SimpleFinSyncWorker"
private const val WORK_NAME = "simplefin_sync"

/** Whether a FiatLife screen is visible, so Amber may show prompts. Set by [MainActivity]. */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
}

/**
 * Every 6 hours: pull bank and credit accounts from the relay, then fetch SimpleFIN
 * balances (skipped when any client fetched within the hour) and publish the changes.
 */
@HiltWorker
class SimpleFinSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dataStore: DataStore<Preferences>,
    private val nostrClient: NostrClient,
    private val bankAccountRepository: BankAccountRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val simpleFinRepository: SimpleFinRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!prepareSigner()) return Result.success()
        if (!nostrClient.ensureConnected(readyTimeoutMs = 20_000)) {
            Log.w(TAG, "Relay not ready (Amber may not allow background signing); skipping")
            return Result.success()
        }
        bankAccountRepository.syncFromNostr()
        creditAccountRepository.syncFromNostr()
        val result = simpleFinRepository.refresh()
        Log.i(TAG, "Background SimpleFIN sync: ${result.javaClass.simpleName}")
        withTimeoutOrNull(60_000) { nostrClient.outbox.first { it.pending == 0 } }
        return Result.success()
    }

    /**
     * Use the saved login. Amber's prompt path needs a visible screen, so in the background
     * Amber is used through its content provider only, which works once Amber is set to
     * always allow FiatLife. [MainActivity] restores its own signer when it starts.
     */
    private suspend fun prepareSigner(): Boolean {
        val prefs = dataStore.data.first()
        val relayUrl = prefs[MainActivity.KEY_RELAY_URL].orEmpty()
        if (relayUrl.isEmpty()) return false
        val current = nostrClient.currentSigner
        val signer: NostrSigner = when (prefs[MainActivity.KEY_AUTH_TYPE]) {
            "local" -> current as? LocalSigner ?: LocalSigner(
                prefs[MainActivity.KEY_PRIVATE_KEY]?.takeIf { it.isNotEmpty() }?.hexToByteArray()
                    ?: return false
            )
            "amber" -> {
                val pubkey = prefs[MainActivity.KEY_AMBER_PUBKEY]?.takeIf { it.isNotEmpty() } ?: return false
                val signerPackage = prefs[MainActivity.KEY_SIGNER_PACKAGE]?.takeIf { it.isNotEmpty() } ?: return false
                if (AppVisibility.visible && current != null) current
                else AmberSigner(applicationContext, pubkey, signerPackage)
            }
            else -> return false
        }
        nostrClient.setSigner(signer)
        nostrClient.connect(relayUrl, signer)
        return true
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SimpleFinSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
