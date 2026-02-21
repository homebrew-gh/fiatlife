package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtState(
    val accounts: List<CreditAccount> = emptyList(),
    val totalCreditAvailable: Double = 0.0,
    val totalCreditUtilized: Double = 0.0,
    val utilizationPercent: Double = 0.0,
    val totalDebt: Double = 0.0,
    val totalMonthlyPayment: Double = 0.0,
    val showAddDialog: Boolean = false,
    val editingAccount: CreditAccount? = null,
    val navigateToAccountId: String? = null,
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val repository: CreditAccountRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(DebtState())
    val state: StateFlow<DebtState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllCreditAccounts().collect { accounts ->
                val revolving = accounts.filter { it.type.isRevolving }
                val totalAvailable = revolving.sumOf { it.creditLimit.coerceAtLeast(0.0) }
                val totalUtilized = revolving.sumOf { it.currentBalance }
                val utilization = if (totalAvailable > 0) totalUtilized / totalAvailable else 0.0
                val totalDebt = accounts.sumOf { it.currentBalance }
                val totalMonthly = accounts.sumOf { it.effectiveMonthlyPayment() }

                _state.update {
                    it.copy(
                        accounts = accounts.sortedWith(
                            compareBy<CreditAccount> { !it.type.isRevolving }.thenBy { it.name.lowercase() }
                        ),
                        totalCreditAvailable = totalAvailable,
                        totalCreditUtilized = totalUtilized,
                        utilizationPercent = utilization,
                        totalDebt = totalDebt,
                        totalMonthlyPayment = totalMonthly
                    )
                }
            }
        }
        syncOnConnect()
    }

    private fun syncOnConnect() {
        viewModelScope.launch {
            nostrClient.connectionState
                .filter { it }
                .distinctUntilChanged()
                .collect {
                    try {
                        repository.syncFromNostr()
                    } catch (_: Exception) { }
                }
        }
    }

    fun showAddAccount() {
        _state.update { it.copy(showAddDialog = true, editingAccount = null) }
    }

    fun showEditAccount(account: CreditAccount) {
        _state.update { it.copy(showAddDialog = true, editingAccount = account) }
    }

    fun dismissDialog() {
        _state.update { it.copy(showAddDialog = false, editingAccount = null) }
    }

    fun clearNavigateToAccountId() {
        _state.update { it.copy(navigateToAccountId = null) }
    }

    fun saveAccount(account: CreditAccount) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val saved = repository.saveCreditAccount(account)
                _state.update {
                    it.copy(
                        isSaving = false,
                        showAddDialog = false,
                        editingAccount = null,
                        navigateToAccountId = saved.id
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteAccount(account: CreditAccount) {
        viewModelScope.launch {
            repository.deleteCreditAccount(account)
        }
    }
}
