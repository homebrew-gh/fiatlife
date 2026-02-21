package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillCategory
import com.fiatlife.app.domain.model.StatementEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillsState(
    val bills: List<Bill> = emptyList(),
    val filteredBills: List<Bill> = emptyList(),
    val selectedCategory: BillCategory? = null,
    val showAddDialog: Boolean = false,
    val editingBill: Bill? = null,
    val dialogStatementEntries: List<StatementEntry> = emptyList(),
    val navigateToBillId: String? = null,
    val totalMonthly: Double = 0.0,
    val categoryTotals: Map<BillCategory, Double> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: BillRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(BillsState())
    val state: StateFlow<BillsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllBills().collect { bills ->
                val monthlyTotal = bills.sumOf { b ->
                    b.amount * b.frequency.timesPerYear / 12.0
                }
                val categoryTotals = bills.groupBy { it.category }
                    .mapValues { (_, list) ->
                        list.sumOf { b -> b.amount * b.frequency.timesPerYear / 12.0 }
                    }

                _state.update { state ->
                    state.copy(
                        bills = bills,
                        filteredBills = filterBills(bills, state.selectedCategory),
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
                    } catch (_: Exception) { }
                }
        }
    }

    fun filterByCategory(category: BillCategory?) {
        _state.update {
            it.copy(
                selectedCategory = category,
                filteredBills = filterBills(it.bills, category)
            )
        }
    }

    fun showAddBill() {
        _state.update { it.copy(showAddDialog = true, editingBill = null, dialogStatementEntries = emptyList()) }
    }

    fun showEditBill(bill: Bill) {
        _state.update { it.copy(showAddDialog = true, editingBill = bill, dialogStatementEntries = bill.statementEntries) }
    }

    fun dismissDialog() {
        _state.update { it.copy(showAddDialog = false, editingBill = null, dialogStatementEntries = emptyList()) }
    }

    fun clearNavigateToBillId() {
        _state.update { it.copy(navigateToBillId = null) }
    }

    fun saveBill(bill: Bill) {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(isSaving = true) }
            try {
                val merged = bill.copy(statementEntries = bill.statementEntries + current.dialogStatementEntries)
                val saved = repository.saveBill(merged)
                _state.update {
                    it.copy(
                        isSaving = false,
                        showAddDialog = false,
                        editingBill = null,
                        dialogStatementEntries = emptyList(),
                        navigateToBillId = saved.id
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            repository.deleteBill(bill)
        }
    }

    fun togglePaid(bill: Bill) {
        viewModelScope.launch {
            val updated = bill.copy(
                isPaid = !bill.isPaid,
                lastPaidDate = if (!bill.isPaid) System.currentTimeMillis() else bill.lastPaidDate
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

    private fun filterBills(bills: List<Bill>, category: BillCategory?): List<Bill> {
        return if (category == null) bills else bills.filter { it.category == category }
    }
}
