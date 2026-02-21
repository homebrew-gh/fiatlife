package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillPayment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BillRepository
) : ViewModel() {

    val billId: String = checkNotNull(savedStateHandle["billId"]) { "billId required" }

    val bill: StateFlow<Bill?> = repository.getBillById(billId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun recordPayment(bill: Bill) {
        viewModelScope.launch {
            val payment = BillPayment(
                date = System.currentTimeMillis(),
                amount = bill.amount
            )
            val updated = bill.copy(
                paymentHistory = bill.paymentHistory + payment,
                isPaid = true,
                lastPaidDate = payment.date
            )
            repository.saveBill(updated)
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            repository.deleteBill(bill)
        }
    }

    fun saveBill(bill: Bill) {
        viewModelScope.launch {
            repository.saveBill(bill)
        }
    }

    suspend fun getStatementBytes(hash: String): Result<ByteArray> =
        repository.downloadAttachment(hash)
}
