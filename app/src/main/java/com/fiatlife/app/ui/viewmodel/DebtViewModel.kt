package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.DebtPayoffSummary
import com.fiatlife.app.domain.model.isMinimumPaymentTrap
import com.fiatlife.app.domain.model.monthlyInterest
import com.fiatlife.app.domain.model.summarizeDebtPayoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtAccountCardUiModel(
    val monthlyInterest: Double = 0.0,
    val isMinimumPaymentTrap: Boolean = false,
    val monthlyPayment: Double = 0.0
)

data class DebtState(
    val accounts: List<CreditAccount> = emptyList(),
    val accountCardById: Map<String, DebtAccountCardUiModel> = emptyMap(),
    val totalCreditAvailable: Double = 0.0,
    val totalCreditUtilized: Double = 0.0,
    val utilizationPercent: Double = 0.0,
    val totalDebt: Double = 0.0,
    val totalMonthlyPayment: Double = 0.0,
    val payoff: DebtPayoffSummary? = null,
    val showAddDialog: Boolean = false,
    val editingAccount: CreditAccount? = null,
    val navigateToAccountId: String? = null,
    val isSaving: Boolean = false,
    val message: String = ""
)

private data class DebtComputed(
    val accounts: List<CreditAccount> = emptyList(),
    val accountCardById: Map<String, DebtAccountCardUiModel> = emptyMap(),
    val totalCreditAvailable: Double = 0.0,
    val totalCreditUtilized: Double = 0.0,
    val utilizationPercent: Double = 0.0,
    val totalDebt: Double = 0.0,
    val totalMonthlyPayment: Double = 0.0,
    val payoff: DebtPayoffSummary? = null
)

private data class DebtUiOverlay(
    val showAddDialog: Boolean = false,
    val editingAccount: CreditAccount? = null,
    val navigateToAccountId: String? = null,
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val repository: CreditAccountRepository
) : ViewModel() {

    private val uiOverlay = MutableStateFlow(DebtUiOverlay())

    val state: StateFlow<DebtState> = combine(
        repository.getAllCreditAccounts()
            .map { accounts -> buildDebtComputed(accounts) }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged(),
        uiOverlay
    ) { computed, ui ->
        DebtState(
            accounts = computed.accounts,
            accountCardById = computed.accountCardById,
            totalCreditAvailable = computed.totalCreditAvailable,
            totalCreditUtilized = computed.totalCreditUtilized,
            utilizationPercent = computed.utilizationPercent,
            totalDebt = computed.totalDebt,
            totalMonthlyPayment = computed.totalMonthlyPayment,
            payoff = computed.payoff,
            showAddDialog = ui.showAddDialog,
            editingAccount = ui.editingAccount,
            navigateToAccountId = ui.navigateToAccountId,
            isSaving = ui.isSaving,
            message = ui.message
        )
    }.stateWhileSubscribed(viewModelScope, DebtState())

    fun showAddAccount() {
        uiOverlay.update { it.copy(showAddDialog = true, editingAccount = null) }
    }

    fun showEditAccount(account: CreditAccount) {
        uiOverlay.update { it.copy(showAddDialog = true, editingAccount = account) }
    }

    fun dismissDialog() {
        uiOverlay.update { it.copy(showAddDialog = false, editingAccount = null) }
    }

    fun clearNavigateToAccountId() {
        uiOverlay.update { it.copy(navigateToAccountId = null) }
    }

    fun saveAccount(account: CreditAccount) {
        viewModelScope.launch {
            uiOverlay.update { it.copy(isSaving = true) }
            try {
                val saved = repository.saveCreditAccount(account)
                uiOverlay.update {
                    it.copy(
                        isSaving = false,
                        showAddDialog = false,
                        editingAccount = null,
                        navigateToAccountId = saved.id
                    )
                }
            } catch (e: Exception) {
                uiOverlay.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteAccount(account: CreditAccount) {
        viewModelScope.launch {
            repository.deleteCreditAccount(account)
        }
    }
}

private fun buildDebtComputed(accounts: List<CreditAccount>): DebtComputed {
    val revolving = accounts.filter { it.type.isRevolving }
    val totalAvailable = revolving.sumOf { it.creditLimit.coerceAtLeast(0.0) }
    val totalUtilized = revolving.sumOf { it.currentBalance }
    val utilization = if (totalAvailable > 0) totalUtilized / totalAvailable else 0.0
    val sorted = accounts.sortedWith(
        compareBy<CreditAccount> { !it.type.isRevolving }.thenBy { it.name.lowercase() }
    )
    val accountCardById = sorted.associate { account ->
        account.id to DebtAccountCardUiModel(
            monthlyInterest = account.monthlyInterest(),
            isMinimumPaymentTrap = account.isMinimumPaymentTrap(),
            monthlyPayment = account.effectiveMonthlyPayment()
        )
    }
    return DebtComputed(
        accounts = sorted,
        accountCardById = accountCardById,
        totalCreditAvailable = totalAvailable,
        totalCreditUtilized = totalUtilized,
        utilizationPercent = utilization,
        totalDebt = accounts.sumOf { it.currentBalance },
        totalMonthlyPayment = accounts.sumOf { it.effectiveMonthlyPayment() },
        payoff = summarizeDebtPayoff(accounts)
    )
}
