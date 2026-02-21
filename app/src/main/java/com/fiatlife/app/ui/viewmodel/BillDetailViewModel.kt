package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillSource
import com.fiatlife.app.domain.model.BillWithSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository
) : ViewModel() {

    val billId: String = checkNotNull(savedStateHandle["billId"]) { "billId required" }

    private val nativeBill = repository.getBillById(billId)
    private val cypherLogBill = cypherLogSubscriptionRepository.getByDTag(billId)

    val billWithSource: StateFlow<BillWithSource?> = combine(nativeBill, cypherLogBill) { native, cypher ->
        when {
            native != null -> BillWithSource(native, BillSource.NATIVE, null)
            cypher != null -> cypher
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val bill: StateFlow<Bill?> = billWithSource.map { it?.bill }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun recordPayment(bill: Bill) {
        val item = billWithSource.value ?: return
        if (item.isCypherLog) return
        viewModelScope.launch {
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

    fun deleteBill(item: BillWithSource) {
        viewModelScope.launch {
            if (item.isCypherLog) {
                cypherLogSubscriptionRepository.deleteSubscription(item.bill.id)
            } else {
                repository.deleteBill(item.bill)
            }
        }
    }

    fun saveBill(bill: Bill) {
        val item = billWithSource.value ?: return
        viewModelScope.launch {
            if (item.isCypherLog) {
                cypherLogSubscriptionRepository.saveSubscription(bill, item.preservedTags)
            } else {
                repository.saveBill(bill)
            }
        }
    }

    suspend fun getStatementBytes(hash: String): Result<ByteArray> =
        repository.downloadAttachment(hash)
}
