package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.domain.model.StatementEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillsState(
    val bills: List<BillWithSource> = emptyList(),
    val filteredBills: List<BillWithSource> = emptyList(),
    val creditAccounts: List<CreditAccount> = emptyList(),
    val selectedGeneralCategory: BillGeneralCategory? = null,
    val showAddDialog: Boolean = false,
    val editingBill: Bill? = null,
    val editingIsCypherLog: Boolean = false,
    val editingPreservedTags: Map<String, List<String>>? = null,
    val dialogStatementEntries: List<StatementEntry> = emptyList(),
    val navigateToBillId: String? = null,
    val totalMonthly: Double = 0.0,
    val categoryTotals: Map<BillGeneralCategory, Double> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(BillsState())
    val state: StateFlow<BillsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            creditAccountRepository.getAllCreditAccounts().collect { accounts ->
                _state.update { it.copy(creditAccounts = accounts) }
            }
        }
        viewModelScope.launch {
            combine(
                repository.getAllBills(),
                cypherLogSubscriptionRepository.getAllAsBills()
            ) { nativeBills, cypherLogBills ->
                val merged = nativeBills.map { BillWithSource(it, com.fiatlife.app.domain.model.BillSource.NATIVE, null) } +
                    cypherLogBills
                merged.sortedBy { it.bill.name.lowercase() }
            }.collect { bills ->
                val allBills = bills.map { it.bill }
                val monthlyTotal = allBills.sumOf { b ->
                    b.effectiveAmountDue() * b.frequency.timesPerYear / 12.0
                }
                val categoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
                    .mapValues { (_, list) ->
                        list.sumOf { b -> b.effectiveAmountDue() * b.frequency.timesPerYear / 12.0 }
                    }

                _state.update { state ->
                    state.copy(
                        bills = bills,
                        filteredBills = filterBills(bills, state.selectedGeneralCategory),
                        totalMonthly = monthlyTotal,
                        categoryTotals = categoryTotals
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
                        cypherLogSubscriptionRepository.syncFromRelay()
                    } catch (_: Exception) { }
                }
        }
    }

    fun filterByGeneralCategory(generalCategory: BillGeneralCategory?) {
        _state.update {
            it.copy(
                selectedGeneralCategory = generalCategory,
                filteredBills = filterBills(it.bills, generalCategory)
            )
        }
    }

    fun showAddBill() {
        _state.update {
            it.copy(
                showAddDialog = true,
                editingBill = null,
                editingIsCypherLog = false,
                editingPreservedTags = null,
                dialogStatementEntries = emptyList()
            )
        }
    }

    fun showEditBill(item: BillWithSource) {
        _state.update {
            it.copy(
                showAddDialog = true,
                editingBill = item.bill,
                editingIsCypherLog = item.isCypherLog,
                editingPreservedTags = item.preservedTags,
                dialogStatementEntries = item.bill.statementEntries
            )
        }
    }

    fun dismissDialog() {
        _state.update {
            it.copy(
                showAddDialog = false,
                editingBill = null,
                editingIsCypherLog = false,
                editingPreservedTags = null,
                dialogStatementEntries = emptyList()
            )
        }
    }

    fun clearNavigateToBillId() {
        _state.update { it.copy(navigateToBillId = null) }
    }

    /**
     * Save bill. For new subscription, [showInCypherLog] chooses 37004 vs 30078.
     * When editing a CypherLog item, we use [editingPreservedTags] for round-trip.
     */
    fun saveBill(bill: Bill, showInCypherLog: Boolean? = null) {
        viewModelScope.launch {
            val current = _state.value
            val merged = bill.copy(statementEntries = bill.statementEntries + current.dialogStatementEntries)
            val isCypherLog = showInCypherLog ?: current.editingIsCypherLog
            val preservedTags = current.editingPreservedTags

            _state.update { it.copy(isSaving = true) }
            try {
                if (isCypherLog) {
                    val billWithId = if (merged.id.isEmpty()) merged.copy(id = java.util.UUID.randomUUID().toString()) else merged
                    cypherLogSubscriptionRepository.saveSubscription(billWithId, preservedTags)
                    _state.update {
                        it.copy(
                            isSaving = false,
                            showAddDialog = false,
                            editingBill = null,
                            editingIsCypherLog = false,
                            editingPreservedTags = null,
                            dialogStatementEntries = emptyList(),
                            navigateToBillId = billWithId.id
                        )
                    }
                } else {
                    val previousBill = current.editingBill
                    val saved = repository.saveBill(merged)
                    val newLinkedId = saved.linkedCreditAccountId
                    val oldLinkedId = previousBill?.linkedCreditAccountId
                    if (oldLinkedId != null && oldLinkedId != newLinkedId) {
                        creditAccountRepository.getCreditAccountById(oldLinkedId).first()?.let { acc ->
                            creditAccountRepository.saveCreditAccount(acc.copy(linkedBillId = null))
                        }
                    }
                    if (newLinkedId != null) {
                        creditAccountRepository.getCreditAccountById(newLinkedId).first()?.let { acc ->
                            creditAccountRepository.saveCreditAccount(acc.copy(linkedBillId = saved.id))
                        }
                    }
                    _state.update {
                        it.copy(
                            isSaving = false,
                            showAddDialog = false,
                            editingBill = null,
                            editingIsCypherLog = false,
                            editingPreservedTags = null,
                            dialogStatementEntries = emptyList(),
                            navigateToBillId = saved.id
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteBill(item: BillWithSource) {
        viewModelScope.launch {
            if (item.isCypherLog) {
                cypherLogSubscriptionRepository.deleteSubscription(item.bill.id)
            } else {
                repository.deleteBill(item.bill)
            }
        }
    }

    /** Record a payment: add to history with amount and date, mark paid, and for credit cards reduce balance. */
    fun recordPayment(item: BillWithSource) {
        if (item.isCypherLog) return
        viewModelScope.launch {
            val bill = item.bill
            val paymentAmount = bill.effectiveAmountDue()
            val payment = BillPayment(
                date = System.currentTimeMillis(),
                amount = paymentAmount
            )
            val updatedCcDetails = bill.creditCardDetails?.let { cc ->
                cc.copy(
                    currentBalance = (cc.currentBalance - paymentAmount).coerceAtLeast(0.0)
                )
            }
            val updated = bill.copy(
                paymentHistory = bill.paymentHistory + payment,
                isPaid = true,
                lastPaidDate = payment.date,
                creditCardDetails = updatedCcDetails
            )
            repository.saveBill(updated)
        }
    }

    fun uploadAttachment(data: ByteArray, contentType: String, filename: String) {
        viewModelScope.launch {
            repository.uploadAttachment(data, contentType, filename)
                .onSuccess { hash ->
                    val entry = StatementEntry(hash = hash, addedAt = System.currentTimeMillis(), label = filename)
                    _state.update { it.copy(dialogStatementEntries = it.dialogStatementEntries + entry) }
                }
                .onFailure { e ->
                    _state.update { it.copy(message = "Upload failed: ${e.message}") }
                }
        }
    }

    private fun filterBills(bills: List<BillWithSource>, generalCategory: BillGeneralCategory?): List<BillWithSource> {
        return if (generalCategory == null) bills
        else bills.filter { it.bill.effectiveGeneralCategory == generalCategory }
    }
}
