package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.SalaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainAppState(
    val isConnected: Boolean = false,
    val hasData: Boolean = false
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val nostrClient: NostrClient,
    private val salaryRepository: SalaryRepository,
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    val state = combine(
        nostrClient.connectionState,
        salaryRepository.getSalaryConfig(),
        billRepository.getAllBills(),
        cypherLogSubscriptionRepository.getAllAsBills(),
        goalRepository.getAllGoals()
    ) { connected, salary, bills, cypherLogBills, goals ->
        MainAppState(
            isConnected = connected,
            hasData = salary != null || bills.isNotEmpty() || cypherLogBills.isNotEmpty() || goals.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainAppState()
    )
}
