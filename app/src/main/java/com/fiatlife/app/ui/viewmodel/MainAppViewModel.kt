package com.fiatlife.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BillerRepository
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.SalaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainAppVM"

data class MainAppState(
    val isConnected: Boolean = false,
    val hasData: Boolean = false,
    val isManualSyncing: Boolean = false,
    val pendingSync: Int = 0,
    val failedSync: Int = 0
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val nostrClient: NostrClient,
    private val salaryRepository: SalaryRepository,
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val goalRepository: GoalRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val billerRepository: BillerRepository
) : ViewModel() {

    private val _isManualSyncing = MutableStateFlow(false)
    val isManualSyncing = _isManualSyncing.asStateFlow()

    private val baseState = combine(
        nostrClient.connectionState,
        salaryRepository.getSalaryConfig(),
        billRepository.getAllBills(),
        cypherLogSubscriptionRepository.getAllAsBills(),
        goalRepository.getAllGoals()
    ) { connected, salary, bills, cypherLogBills, goals ->
        MainAppState(
            isConnected = connected,
            hasData = salary != null || bills.isNotEmpty() || cypherLogBills.isNotEmpty() || goals.isNotEmpty(),
            isManualSyncing = false
        )
    }

    val state = combine(
        baseState,
        isManualSyncing,
        nostrClient.outbox
    ) { base, manualSyncing, outbox ->
        base.copy(
            isManualSyncing = manualSyncing,
            pendingSync = outbox.pending,
            failedSync = outbox.failed
        )
    }.stateIn(
        scope = viewModelScope,
        // Lazily: start when first UI subscribes, then keep collecting so the banner
        // does not sit on initialValue (offline) while combine is inactive, which
        // happened with WhileSubscribed when collectors briefly dropped.
        started = SharingStarted.Lazily,
        initialValue = MainAppState()
    )

    /** Retry any background relay publishes that exhausted their retries. */
    fun retryFailedSync() {
        nostrClient.retryOutbox()
    }

    /** Manual sync trigger from the top-right sync status control. */
    fun manualSyncFromRelay() {
        if (_isManualSyncing.value) return
        viewModelScope.launch {
            _isManualSyncing.update { true }
            try {
                // Banner can show Offline while the socket is down; sync still queued REQs
                // and showed "Syncing with relay…". Wait for a real connection first.
                val relayReady = nostrClient.ensureConnected(15_000)
                if (!relayReady) {
                    Log.w(TAG, "Manual sync: relay not ready after timeout")
                }
                val jobs = listOf(
                    launch { runCatching { salaryRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Salary sync failed: ${it.message}") } },
                    launch { runCatching { billRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Bill sync failed: ${it.message}") } },
                    launch { runCatching { cypherLogSubscriptionRepository.syncFromRelay() }.onFailure { Log.w(TAG, "CypherLog 37004 sync failed: ${it.message}") } },
                    launch { runCatching { goalRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Goal sync failed: ${it.message}") } },
                    launch { runCatching { creditAccountRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Credit account sync failed: ${it.message}") } },
                    launch { runCatching { bankAccountRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Bank account sync failed: ${it.message}") } },
                    launch { runCatching { billerRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Biller sync failed: ${it.message}") } }
                )
                jobs.joinAll()
                runCatching { billRepository.backfillLegacyCreditLoanPayments() }
            } finally {
                _isManualSyncing.update { false }
            }
        }
    }
}
