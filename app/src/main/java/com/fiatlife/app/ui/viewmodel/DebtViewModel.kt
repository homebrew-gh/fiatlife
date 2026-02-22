package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
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
    private val billRepository: BillRepository,
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
                var saved = repository.saveCreditAccount(account)
                val billSubcategory = when (saved.type) {
                    CreditAccountType.CREDIT_CARD -> BillSubcategory.CREDIT_CARD
                    CreditAccountType.STUDENT_LOAN -> BillSubcategory.STUDENT_LOAN
                    else -> BillSubcategory.OTHER_LOAN
                }
                if (saved.currentBalance > 0) {
                    if (saved.linkedBillId != null) {
                        val existing = billRepository.getBillById(saved.linkedBillId).first()
                        if (existing != null) {
                            billRepository.saveBill(
                                existing.copy(
                                    name = saved.name,
                                    amount = saved.effectiveMonthlyPayment(),
                                    dueDay = saved.dueDay,
                                    subcategory = billSubcategory,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        } else {
                            createAndLinkBill(saved, billSubcategory)
                        }
                    } else {
                        createAndLinkBill(saved, billSubcategory)
                    }
                } else {
                    saved.linkedBillId?.let { billId ->
                        billRepository.getBillById(billId).first()?.let { existing ->
                            billRepository.saveBill(
                                existing.copy(
                                    amount = 0.0,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
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

    private suspend fun createAndLinkBill(account: CreditAccount, billSubcategory: BillSubcategory) {
        val billId = UUID.randomUUID().toString()
        val bill = Bill(
            id = billId,
            name = account.name,
            amount = account.effectiveMonthlyPayment(),
            subcategory = billSubcategory,
            frequency = BillFrequency.MONTHLY,
            dueDay = account.dueDay,
            linkedCreditAccountId = account.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        billRepository.saveBill(bill)
        repository.saveCreditAccount(account.copy(linkedBillId = billId))
    }

    fun deleteAccount(account: CreditAccount) {
        viewModelScope.launch {
            repository.deleteCreditAccount(account)
        }
    }
}
