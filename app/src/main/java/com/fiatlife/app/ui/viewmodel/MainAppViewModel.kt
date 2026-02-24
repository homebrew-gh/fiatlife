package com.fiatlife.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
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
    val isManualSyncing: Boolean = false
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val nostrClient: NostrClient,
    private val salaryRepository: SalaryRepository,
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val goalRepository: GoalRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val bankAccountRepository: BankAccountRepository
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

    val state = combine(baseState, isManualSyncing) { base, manualSyncing ->
        base.copy(isManualSyncing = manualSyncing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainAppState()
    )

    /** Manual sync trigger from the top-right sync status control. */
    fun manualSyncFromRelay() {
        if (_isManualSyncing.value) return
        viewModelScope.launch {
            _isManualSyncing.update { true }
            try {
                val jobs = listOf(
                    launch { runCatching { salaryRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Salary sync failed: ${it.message}") } },
                    launch { runCatching { billRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Bill sync failed: ${it.message}") } },
                    launch { runCatching { cypherLogSubscriptionRepository.syncFromRelay() }.onFailure { Log.w(TAG, "CypherLog 37004 sync failed: ${it.message}") } },
                    launch { runCatching { goalRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Goal sync failed: ${it.message}") } },
                    launch { runCatching { creditAccountRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Credit account sync failed: ${it.message}") } },
                    launch { runCatching { bankAccountRepository.syncFromNostr() }.onFailure { Log.w(TAG, "Bank account sync failed: ${it.message}") } }
                )
                jobs.joinAll()
            } finally {
                _isManualSyncing.update { false }
            }
        }
    }
}
