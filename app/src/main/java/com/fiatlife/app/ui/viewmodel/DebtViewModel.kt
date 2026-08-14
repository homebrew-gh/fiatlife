package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import com.fiatlife.app.domain.model.CreditStatementUpdate
import com.fiatlife.app.domain.model.DebtPayoffSummary
import com.fiatlife.app.domain.model.FinancialGoal
import com.fiatlife.app.domain.model.GoalCategory
import com.fiatlife.app.domain.model.isMinimumPaymentTrap
import com.fiatlife.app.domain.model.monthlyInterest
import com.fiatlife.app.domain.model.suggestedEmergencyFundTarget
import com.fiatlife.app.domain.model.suggestedMaintenanceAnnual
import com.fiatlife.app.domain.model.summarizeDebtPayoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtAccountCardUiModel(
    val monthlyInterest: Double = 0.0,
    val isMinimumPaymentTrap: Boolean = false,
    val monthlyPayment: Double = 0.0,
    val dueDays: Int = 0,
    val overdue: Boolean = false,
    val paidThisCycle: Boolean = false
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
    val message: String = "",
    val pendingMortgageGoals: CreditAccount? = null
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
    val message: String = "",
    val pendingMortgageGoals: CreditAccount? = null
)

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val repository: CreditAccountRepository,
    private val billRepository: BillRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val uiOverlay = MutableStateFlow(DebtUiOverlay())

    val state: StateFlow<DebtState> = combine(
        combine(
            repository.getAllCreditAccounts(),
            billRepository.getAllBills()
        ) { accounts, bills -> buildDebtComputed(accounts, bills) }
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
            message = ui.message,
            pendingMortgageGoals = ui.pendingMortgageGoals
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
                val previous = state.value.accounts.find { it.id == account.id }
                val saved = repository.saveCreditAccount(account)
                val promptGoals = previous == null && saved.type == CreditAccountType.MORTGAGE
                uiOverlay.update {
                    it.copy(
                        isSaving = false,
                        showAddDialog = false,
                        editingAccount = null,
                        navigateToAccountId = if (promptGoals) null else saved.id,
                        pendingMortgageGoals = if (promptGoals) saved else null
                    )
                }
            } catch (e: Exception) {
                uiOverlay.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun skipMortgageGoals() {
        val account = uiOverlay.value.pendingMortgageGoals ?: return
        uiOverlay.update {
            it.copy(pendingMortgageGoals = null, navigateToAccountId = account.id)
        }
    }

    fun applyMortgageGoals(
        emergencyMonths: Int,
        includeEmergency: Boolean,
        includeMaintenance: Boolean
    ) {
        viewModelScope.launch {
            val account = uiOverlay.value.pendingMortgageGoals ?: return@launch
            val existing = goalRepository.getAllGoals().first()
            val now = System.currentTimeMillis()
            val housing = account.housingPitiMonthly()
            if (includeEmergency && housing > 0) {
                val target = suggestedEmergencyFundTarget(housing, emergencyMonths)
                val current = existing.firstOrNull { it.category == GoalCategory.EMERGENCY_FUND }
                if (current != null) {
                    goalRepository.saveGoal(
                        current.copy(targetAmount = maxOf(current.targetAmount, target), updatedAt = now)
                    )
                } else {
                    goalRepository.saveGoal(
                        FinancialGoal(
                            name = "Emergency fund",
                            category = GoalCategory.EMERGENCY_FUND,
                            targetAmount = target,
                            color = GoalCategory.EMERGENCY_FUND.suggestedColor,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
            val homePrice = account.homePrice
            if (includeMaintenance && homePrice > 0) {
                val annual = suggestedMaintenanceAnnual(homePrice)
                val current = existing.firstOrNull {
                    it.category == GoalCategory.HOME_IMPROVEMENT &&
                        it.name.contains("maintenance", ignoreCase = true)
                }
                if (current != null) {
                    goalRepository.saveGoal(
                        current.copy(
                            targetAmount = maxOf(current.targetAmount, annual),
                            monthlyContribution = maxOf(current.monthlyContribution, annual / 12.0),
                            updatedAt = now
                        )
                    )
                } else {
                    goalRepository.saveGoal(
                        FinancialGoal(
                            name = "Home maintenance",
                            category = GoalCategory.HOME_IMPROVEMENT,
                            targetAmount = annual,
                            monthlyContribution = annual / 12.0,
                            notes = "About 1% of home price per year.",
                            color = GoalCategory.HOME_IMPROVEMENT.suggestedColor,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
            uiOverlay.update {
                it.copy(pendingMortgageGoals = null, navigateToAccountId = account.id)
            }
        }
    }

    fun deleteAccount(account: CreditAccount) {
        viewModelScope.launch {
            repository.deleteCreditAccount(account)
        }
    }

    fun updateStatement(account: CreditAccount, update: CreditStatementUpdate) {
        viewModelScope.launch {
            uiOverlay.update { it.copy(isSaving = true) }
            try {
                repository.updateStatement(account, update)
                uiOverlay.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                uiOverlay.update {
                    it.copy(isSaving = false, message = "Statement update failed: ${e.message}")
                }
            }
        }
    }
}

private fun paidThisCycle(account: CreditAccount, bills: List<Bill>): Boolean {
    val bill = bills.firstOrNull { it.id == account.linkedBillId }
        ?: bills.firstOrNull { it.linkedCreditAccountId == account.id }
    return bill?.isPaidForCurrentCycle() == true
}

private fun buildDebtComputed(
    accounts: List<CreditAccount>,
    bills: List<Bill>
): DebtComputed {
    val revolving = accounts.filter { it.type.isRevolving }
    val totalAvailable = revolving.sumOf { it.creditLimit.coerceAtLeast(0.0) }
    val totalUtilized = revolving.sumOf { it.currentBalance }
    val utilization = if (totalAvailable > 0) totalUtilized / totalAvailable else 0.0
    val paidById = accounts.associate { it.id to paidThisCycle(it, bills) }
    val sorted = accounts.sortedWith(
        compareBy<CreditAccount> { it.currentBalance <= 0.0 }
            .thenByDescending { it.dueUrgency(paidById[it.id] == true).overdue }
            .thenBy {
                val urgency = it.dueUrgency(paidById[it.id] == true)
                if (urgency.overdue) -urgency.days else urgency.days
            }
            .thenBy { it.name.lowercase() }
    )
    val accountCardById = sorted.associate { account ->
        val urgency = account.dueUrgency(paidById[account.id] == true)
        account.id to DebtAccountCardUiModel(
            monthlyInterest = account.monthlyInterest(),
            isMinimumPaymentTrap = account.isMinimumPaymentTrap(),
            monthlyPayment = account.effectiveMonthlyPayment(),
            dueDays = urgency.days,
            overdue = urgency.overdue,
            paidThisCycle = paidById[account.id] == true
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
